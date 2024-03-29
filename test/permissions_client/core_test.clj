(ns permissions-client.core-test
  (:require [permissions-client.core :as pc]
            [cemerick.url :as curl]
            [cheshire.core :as json]
            [clj-http.fake :refer :all]
            [clojure.string :as string]
            [honey.sql :as hsql])
  (:use [clojure.test]))

(defn success-fn
  ([]
   (success-fn ""))
  ([body]
   (constantly {:status 200 :body body})))

(def fake-base-url "http://perms.example.org/")

(defn fake-sql-array
  "Returns a fake implementation of java.sql.Array that can be used for testing. This is just a test shim, so only
   some methods are fully implemented and any type conversion that would be done by a real implementation is ignored."
  [items]
  (reify java.sql.Array
    (free [_])
    (getArray [_]
      (into-array String items))
    (getArray [_ index count]
      (into-array String (take count (drop index items))))
    (getArray [_ index count _]
      (into-array String (take count (drop index items))))
    (getArray [_ _]
      (into-array String items))
    (getBaseType [_]
      0)
    (getBaseTypeName [_]
      "text")
    (getResultSet [_]
      nil)
    (getResultSet [_ _ _]
      nil)
    (getResultSet [_ _ _ _]
      nil)
    (getResultSet [_ _]
      nil)))

(defn fake-url [& components]
  (str (apply curl/url fake-base-url components)))

(defn fake-query-url [query & components]
  (str (assoc (apply curl/url fake-base-url components) :query query)))

(defn fake-lookup-url [lookup? & components]
  (str (assoc (apply curl/url fake-base-url components) :query {:lookup lookup?})))

(defn fake-min-level-url [min-level & components]
  (str (assoc (apply curl/url fake-base-url components) :query {:lookup true :min_level min-level})))

(defn create-fake-client []
  (pc/new-permissions-client fake-base-url))

(def fake-status
  {:description "Manages Permissions for the CyVerse Discovery Environment and related applications."
   :service     "Permissions Service"
   :version     "1.2.3.4"})

(defn fake-status-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-status)})

(deftest test-status
  (with-fake-routes {fake-base-url {:get fake-status-response}}
    (is (= (pc/get-status (create-fake-client)) fake-status))))

(def fake-subjects
  {:subjects
   [{:id           "e82a84e7-a5ef-4f2a-9b04-91e3d4888c8e"
     :subject_id   "ipctest"
     :subject_type "user"}
    {:id           "ccb1791d-5681-4df8-8f2d-3d80ff84a338"
     :subject_id   "1cf8509b-842d-49c9-a455-ca5dac6c2b92"
     :subject_type "group"}
    {:id           "bee5df29-a772-4d17-a32a-9957f234a375"
     :subject_id   "ipcdev"
     :subject_type "user"}]})

(defn fake-subjects-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-subjects)})

(deftest test-subjects
  (with-fake-routes {(fake-url "subjects") {:get fake-subjects-response}}
    (is (= (pc/list-subjects (create-fake-client)) fake-subjects))))

(deftest test-subjects-by-id
  (let [opts {:subject_id "ipctest"}]
    (with-fake-routes {(fake-query-url opts "subjects") {:get fake-subjects-response}}
      (is (= (pc/list-subjects (create-fake-client) opts) fake-subjects)))))

(deftest test-subjects-by-type
  (let [opts {:subject_type "user"}]
    (with-fake-routes {(fake-query-url opts "subjects") {:get fake-subjects-response}}
      (is (= (pc/list-subjects (create-fake-client) opts) fake-subjects)))))

(deftest test-subjects-by-id-and-type
  (let [opts {:subject_id "ipctest" :subject_type "user"}]
    (with-fake-routes {(fake-query-url opts "subjects") {:get fake-subjects-response}}
      (is (= (pc/list-subjects (create-fake-client) opts) fake-subjects)))))

(defn fake-subject [{subject-id :subject_id subject-type :subject_type}]
  {:id           "acefbb43-00fe-4b16-a834-68f24207aba7"
   :subject_id   subject-id
   :subject_type subject-type})

(defn add-subject-response [request]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode (fake-subject (json/decode (slurp (:body request)) true)))})

(deftest test-add-subject
  (with-fake-routes {(fake-url "subjects") {:post add-subject-response}}
    (is (= (pc/add-subject (create-fake-client) "ipctest" "user")
           (fake-subject {:subject_id "ipctest" :subject_type "user"})))))

(defn delete-subject-response-fn [id]
  (fn [{:keys [uri]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body ""}
      {:status 400 :body ""})))

(deftest test-delete-subject
  (let [subject-id "54430509-794e-497e-8ccd-ba01c713ef9f"]
    (with-fake-routes {(fake-url "subjects" subject-id) {:delete (delete-subject-response-fn subject-id)}}
      (pc/delete-subject (create-fake-client) subject-id))))

(deftest test-delete-subject-by-external-id
  (let [opts {:subject_id "ipctest" :subject_type "user"}]
    (with-fake-routes {(fake-query-url opts "subjects") fake-status-response}
      (pc/delete-subject (create-fake-client) (:subject_id opts) (:subject_type opts)))))

(defn update-subject-response-fn [id]
  (fn [{:keys [uri body]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body (json/encode (assoc (json/decode (slurp body) true) :id id))}
      {:status 400 :body ""})))

(deftest test-update-subject
  (let [subject (fake-subject {:subject_id "ipcdev" :subject_type "user"})]
    (with-fake-routes {(fake-url "subjects" (:id subject)) {:put (update-subject-response-fn (:id subject))}}
      (is (= (pc/update-subject (create-fake-client) (:id subject) (:subject_id subject) (:subject_type subject))
             subject)))))

(def fake-resources
  [{:id            "b3ae58c3-0d79-416b-a232-0938e7a9b699"
    :name          "a"
    :resource_type "app"}
   {:id            "9a366ab6-0d67-4bba-b1cc-ded2a30929b4"
    :name          "b"
    :resource_type "app"}
   {:id            "2350b289-ab0d-468f-b849-66c0ece7060b"
    :name          "c"
    :resource_type "analysis"}])

(defn list-resources-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-resources)})

(deftest test-list-resources
  (with-fake-routes {(fake-url "resources") {:get list-resources-response}}
    (is (= (pc/list-resources (create-fake-client)) fake-resources))))

(deftest test-list-resources-by-name
  (let [opts {:resource_name "a"}]
    (with-fake-routes {(fake-query-url opts "resources") {:get list-resources-response}}
      (is (= (pc/list-resources (create-fake-client) opts) fake-resources)))))

(deftest test-list-resources-by-type
  (let [opts {:resource_type_name "app"}]
    (with-fake-routes {(fake-query-url opts "resources") {:get list-resources-response}}
      (is (= (pc/list-resources (create-fake-client) opts) fake-resources)))))

(deftest test-list-resources-by-name-and-type
  (let [opts {:resource_name "a" :resource_type_name "app"}]
    (with-fake-routes {(fake-query-url opts "resources") {:get list-resources-response}}
      (is (= (pc/list-resources (create-fake-client) opts) fake-resources)))))

(defn fake-resource [{name :name resource-type :resource_type}]
  {:id            "1aab7522-426a-411b-bef3-1c702ad9e89b"
   :name          name
   :resource_type resource-type})

(defn add-resource-response [{:keys [body]}]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode (fake-resource (json/decode (slurp body) true)))})

(deftest test-add-resource
  (let [resource (fake-resource {:name "d" :resource_type "analysis"})]
    (with-fake-routes {(fake-url "resources") {:post add-resource-response}}
      (is (= (pc/add-resource (create-fake-client) (:name resource) (:resource_type resource)) resource)))))

(defn delete-resource-response-fn [id]
  (fn [{:keys [uri]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body ""}
      {:status 400 :body ""})))

(deftest test-delete-resource
  (let [resource-id "20dbf604-a080-4706-acbc-8e65779cf638"]
    (with-fake-routes {(fake-url "resources" resource-id) {:delete (delete-resource-response-fn resource-id)}}
      (pc/delete-resource (create-fake-client) resource-id))))

(deftest test-delete-resource-by-name-and-type
  (let [opts {:resource_name "a" :resource_type_name "app"}]
    (with-fake-routes {(fake-query-url opts "resources") {:delete fake-status-response}}
      (pc/delete-resource (create-fake-client) (:resource_name opts) (:resource_type_name opts)))))

(defn update-resource-response-fn [id resource-type]
  (fn [{:keys [uri body]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body (json/encode (assoc (json/decode (slurp body) true) :id id :resource_type resource-type))}
      {:status 400 :body ""})))

(deftest test-update-resource
  (let [{:keys [id name] resource-type :resource_type :as resource} (fake-resource {:name "e" :resource_type "app"})]
    (with-fake-routes {(fake-url "resources" id) (update-resource-response-fn id resource-type)}
      (is (= (pc/update-resource (create-fake-client) id name) resource)))))

(def fake-resource-types
  {:resource_types
   [{:description "A resource type description."
     :id          "a5ccd2c3-6e19-464f-8525-e108e62998e5"
     :name        "rtype"}
    {:description "Another resource type description."
     :id          "c783abd0-5a91-49f4-82f3-3e2b5d495f59"
     :name        "artype"}
    {:description "A resource type description for pirates."
     :id          "88fc2154-9b83-4a02-bcb4-221273b59f4e"
     :name        "arrrrrrrtype"}]})

(defn list-resource-types-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-resource-types)})

(deftest test-list-resource-types
  (with-fake-routes {(fake-url "resource_types") {:get list-resource-types-response}}
    (is (= (pc/list-resource-types (create-fake-client)) fake-resource-types))))

(deftest test-list-resource-types-by-name
  (let [opts {:resource_type_name "rtype"}]
    (with-fake-routes {(fake-query-url opts "resource_types") {:get list-resource-types-response}}
      (is (= (pc/list-resource-types (create-fake-client) opts) fake-resource-types)))))

(defn fake-resource-type [{:keys [name description]}]
  {:id          "2bec53ae-4732-4768-86fd-344b9692332f"
   :name        name
   :description description})

(defn add-resource-type-response [{:keys [body]}]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode (fake-resource-type (json/decode (slurp body) true)))})

(deftest test-add-resource-type
  (let [{:keys [name description] :as resource-type} (fake-resource-type {:name "a" :description "b"})]
    (with-fake-routes {(fake-url "resource_types") {:post add-resource-type-response}}
      (is (= (pc/add-resource-type (create-fake-client) name description) resource-type)))))

(defn delete-resource-type-response-fn [id]
  (fn [{:keys [uri]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body ""}
      {:status 400 :body ""})))

(deftest test-delete-resource-type
  (let [id "f41c182b-721a-4e6d-94a5-d9db7144ddc7"]
    (with-fake-routes {(fake-url "resource_types" id) (delete-resource-type-response-fn id)}
      (pc/delete-resource-type (create-fake-client) id))))

(deftest test-delete-resource-type-by-name
  (let [opts {:resource_type_name "app"}]
    (with-fake-routes {(fake-query-url opts "resource_types") {:delete fake-status-response}}
      (pc/delete-resource-type-by-name (create-fake-client) (:resource_type_name opts)))))

(defn update-resource-type-response-fn [id]
  (fn [{:keys [uri body]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body (json/encode (assoc (json/decode (slurp body) true) :id id))}
      {:status 400 :body ""})))

(deftest test-update-resource-type
  (let [{:keys [id name description] :as resource-type} (fake-resource-type {:name "a" :description "b"})]
    (with-fake-routes {(fake-url "resource_types" id) {:put (update-resource-type-response-fn id)}}
      (is (= (pc/update-resource-type (create-fake-client) id name description) resource-type)))))

(def fake-perms
  {:permissions
   [{:id               "b587bbab-4c5f-4adc-baa7-f4ce04690c8e"
     :permission_level "read"
     :resource         {:id            "bc689e70-e773-4f1e-8aae-afe61fdaec9e"
                        :name          "a"
                        :resource_type "app"}
     :subject          {:id            "0e49e81b-1de3-4837-9b8b-77065fc946a2"
                        :name          "ipcdev"
                        :subject_type  "user"}}
    {:id               "c36ea831-fa4d-418b-8afb-27a7b8f7d9d6"
     :permission_level "own"
     :resource         {:id            "beabd0f7-ac6b-4f5a-9d68-06b1232b3d89"
                        :name          "a"
                        :resource_type "app"}
     :subject          {:id            "2aec85ff-ccda-4e29-b7eb-e4847a10c28c"
                        :name          "ipcdev"
                        :subject_type  "user"}}]})

(def fake-abbreviated-perms
  {:permissions
   [{:id               "b587bbab-4c5f-4adc-baa7-f4ce04690c8e"
     :permission_level "read"
     :resource_name    "a"
     :resource_type    "app"}
    {:id               "c36ea831-fa4d-418b-8afb-27a7b8f7d9d6"
     :permission_level "own"
     :resource_name    "a"
     :resource_type    "app"}]})

(defn list-perms-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-perms)})

(defn list-abbreviated-perms-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-abbreviated-perms)})

(deftest test-list-perms
  (with-fake-routes {(fake-url "permissions") {:get list-perms-response}}
    (is (= (pc/list-permissions (create-fake-client)) fake-perms))))

(defn copy-perms-handler [expected-source-type expected-source-id expected-subjects]
  (fn [{:keys [uri body]}]
    (if-let [match (re-find #"^/permissions/subjects/([^/]+)/([^/]+)/copy" uri)]
      (let [[_ actual-source-type actual-source-id] match
            actual-subjects (:subjects (json/decode (slurp body) true))]
        (is (= expected-source-type actual-source-type))
        (is (= expected-source-id actual-source-id))
        (is (= expected-subjects actual-subjects))
        {:status 200 :body ""})
      {:status 400 :body ""})))

(deftest test-copy-perms
  (let [source-type "user"
        source-id   "ipcdev"
        subjects    [{:subject_type "user" :subject_id "ipctest"}]]
    (with-fake-routes {(fake-url "permissions" "subjects" source-type source-id "copy")
                       {:post (copy-perms-handler source-type source-id subjects)}}
      (is (nil? (pc/copy-permissions (create-fake-client) source-type source-id subjects))))))

(defn fake-perm [resource-type resource-name subject-type subject-id level]
  {:id               "e3c73dd4-501f-40b2-9bf4-631b1ca93a89"
   :permission_level level
   :resource         (fake-resource {:name resource-name :resource_type resource-type})
   :subject          (fake-subject {:subject_id subject-id :subject_type subject-type})})

(defn grant-perm-response [{:keys [uri body]}]
  (if-let [match (re-find #"^/permissions/resources/([^/]+)/([^/]+)/subjects/([^/]+)/([^/]+)" uri)]
    (let [[_ resource-type resource-name subject-type subject-id] match
          level (:permission_level (json/decode (slurp body) true))]
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    (json/encode (fake-perm resource-type resource-name subject-type subject-id level))})
    {:status 400 :body ""}))

(deftest test-grant-perm
  (let [[rt rn st sn l] ["app" "a" "user" "ipcdev" "own"]]
    (with-fake-routes {(fake-url "permissions" "resources" rt rn "subjects" st sn) {:put grant-perm-response}}
      (is (= (pc/grant-permission (create-fake-client) rt rn st sn l)
             (fake-perm rt rn st sn l))))))

(deftest test-revoke-perm
  (let [[rt rn st sn] ["app" "a" "user" "ipcdev"]]
    (with-fake-routes {(fake-url "permissions" "resources" rt rn "subjects" st sn) {:delete (success-fn)}}
      (pc/revoke-permission (create-fake-client) rt rn st sn))))

(deftest test-list-resource-permissions
  (let [[rt rn] ["app" "a"]]
    (with-fake-routes {(fake-url "permissions" "resources" rt rn) {:get list-perms-response}}
      (is (= (pc/list-resource-permissions (create-fake-client) rt rn) fake-perms)))))

(deftest test-get-subject-permissions
  (let [[st sn] ["user" "ipcdev"]]
    (with-fake-routes {(fake-lookup-url false "permissions" "subjects" st sn) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions (create-fake-client) st sn false) fake-perms)))
    (with-fake-routes {(fake-lookup-url true "permissions" "subjects" st sn) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions (create-fake-client) st sn true) fake-perms)))
    (with-fake-routes {(fake-min-level-url "write" "permissions" "subjects" st sn) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions (create-fake-client) st sn true "write") fake-perms)))))

(deftest test-get-subject-permissions-for-resource-type
  (let [[st sn rt] ["user" "ipcdev" "app"]]
    (with-fake-routes {(fake-lookup-url false "permissions" "subjects" st sn rt) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions-for-resource-type (create-fake-client) st sn rt false) fake-perms)))
    (with-fake-routes {(fake-lookup-url true "permissions" "subjects" st sn rt) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions-for-resource-type (create-fake-client) st sn rt true) fake-perms)))
    (with-fake-routes {(fake-min-level-url "admin" "permissions" "subjects" st sn rt) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions-for-resource-type (create-fake-client) st sn rt true "admin") fake-perms)))))

(deftest test-get-abbreviated-subject-permissions-for-resource-type
  (let [[st sn rt] ["user" "ipcdev" "app"]]
    (with-fake-routes {(fake-lookup-url false "permissions" "abbreviated" "subjects" st sn rt)
                       {:get list-abbreviated-perms-response}}
      (is (= (pc/get-abbreviated-subject-permissions-for-resource-type (create-fake-client) st sn rt false)
             fake-abbreviated-perms)))
    (with-fake-routes {(fake-lookup-url true "permissions" "abbreviated" "subjects" st sn rt)
                       {:get list-abbreviated-perms-response}}
      (is (= (pc/get-abbreviated-subject-permissions-for-resource-type (create-fake-client) st sn rt true)
             fake-abbreviated-perms)))
    (with-fake-routes {(fake-min-level-url "admin" "permissions" "abbreviated" "subjects" st sn rt)
                       {:get list-abbreviated-perms-response}}
      (is (= (pc/get-abbreviated-subject-permissions-for-resource-type (create-fake-client) st sn rt true "admin")
             fake-abbreviated-perms)))))

(deftest test-get-subject-permissions-for-resource
  (let [[st sn rt rn] ["user" "ipcdev" "app" "a"]]
    (with-fake-routes {(fake-lookup-url false "permissions" "subjects" st sn rt rn) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions-for-resource (create-fake-client) st sn rt rn false) fake-perms)))
    (with-fake-routes {(fake-lookup-url true "permissions" "subjects" st sn rt rn) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions-for-resource (create-fake-client) st sn rt rn true) fake-perms)))
    (with-fake-routes {(fake-min-level-url "own" "permissions" "subjects" st sn rt rn) {:get list-perms-response}}
      (is (= (pc/get-subject-permissions-for-resource (create-fake-client) st sn rt rn true "own") fake-perms)))))

(defn accessible-resource-query-sql
  "Returns SQL we expect to be generated from the DSL returned by `accessible-resource-query-dsl`"
  []
  (str "SELECT DISTINCT CAST(pr.name AS UUID) AS id "
       "FROM permissions.permissions AS pp "
       "INNER JOIN permissions.subjects AS ps ON pp.subject_id = ps.id "
       "INNER JOIN permissions.resources AS pr ON pp.resource_id = pr.id "
       "INNER JOIN permissions.resource_types AS prt ON pr.resource_type_id = prt.id "
       "INNER JOIN permissions.permission_levels AS pl ON pp.permission_level_id = pl.id "
       "WHERE (ps.subject_id = ANY(?)) "
       "AND (prt.name = ?) "
       "AND (pl.precedence <= (SELECT precedence FROM permissions.permission_levels WHERE name = ?))"))

(deftest test-accessible-resource-query-dsl
  (let [subject-ids    (fake-sql-array ["foo" "bar" "baz"])
        expected-query (accessible-resource-query-sql)
        client         (create-fake-client)]
    (let [[query & params] (hsql/format (pc/accessible-resource-query-dsl client subject-ids "analysis"))]
      (is (= expected-query query))
      (is (= "analysis" (second params)))
      (is (= "read" (last params))))
    (let [[query & params] (hsql/format (pc/accessible-resource-query-dsl client subject-ids "app" "own"))]
      (is (= expected-query query))
      (is (= "app" (second params)))
      (is (= "own" (last params))))
    (is (thrown? IllegalArgumentException (pc/accessible-resource-query-dsl "foo" "app" "own")))))
