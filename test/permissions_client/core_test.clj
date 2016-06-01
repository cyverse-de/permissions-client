(ns permissions-client.core-test
  (:require [permissions-client.core :as pc]
            [cemerick.url :as curl]
            [cheshire.core :as json]
            [clojure.string :as string])
  (:use [clj-http.fake]
        [clojure.test]))

(def fake-base-url "http://perms.example.org/")

(defn fake-url [& components]
  (str (apply curl/url fake-base-url components)))

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

(deftest test-status []
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

(deftest test-subjects []
  (with-fake-routes {(fake-url "subjects") {:get fake-subjects-response}}
    (is (= (pc/list-subjects (create-fake-client)) fake-subjects))))

(defn fake-subject [{subject-id :subject_id subject-type :subject_type}]
  {:id           "acefbb43-00fe-4b16-a834-68f24207aba7"
   :subject_id   subject-id
   :subject_type subject-type})

(defn add-subject-response [request]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode (fake-subject (json/decode (slurp (:body request)) true)))})

(deftest test-add-subject []
  (with-fake-routes {(fake-url "subjects") {:post add-subject-response}}
    (is (= (pc/add-subject (create-fake-client) "ipctest" "user")
           (fake-subject {:subject_id "ipctest" :subject_type "user"})))))

(defn delete-subject-response-fn [id]
  (fn [{:keys [uri]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body ""}
      {:status 400 :body ""})))

(deftest test-delete-subject []
  (let [subject-id "54430509-794e-497e-8ccd-ba01c713ef9f"]
    (with-fake-routes {(fake-url "subjects" subject-id) {:delete (delete-subject-response-fn subject-id)}}
      (pc/delete-subject (create-fake-client) subject-id)
      (is true "Subject deleted successfully."))))

(defn update-subject-response-fn [id]
  (fn [{:keys [uri body]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body (json/encode (assoc (json/decode (slurp body) true) :id id))}
      {:status 400 :body ""})))

(deftest test-update-subject []
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

(deftest test-list-resources []
  (with-fake-routes {(fake-url "resources") {:get list-resources-response}}
    (is (= (pc/list-resources (create-fake-client)) fake-resources))))

(defn fake-resource [{name :name resource-type :resource_type}]
  {:id            "1aab7522-426a-411b-bef3-1c702ad9e89b"
   :name          name
   :resource_type resource-type})

(defn add-resource-response [{:keys [body]}]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode (fake-resource (json/decode (slurp body) true)))})

(deftest test-add-resource []
  (let [resource (fake-resource {:name "d" :resource_type "analysis"})]
    (with-fake-routes {(fake-url "resources") {:post add-resource-response}}
      (is (= (pc/add-resource (create-fake-client) (:name resource) (:resource_type resource)) resource)))))

(defn delete-resource-response-fn [id]
  (fn [{:keys [uri]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body ""}
      {:status 400 :body ""})))

(deftest test-delete-resource []
  (let [resource-id "20dbf604-a080-4706-acbc-8e65779cf638"]
    (with-fake-routes {(fake-url "resources" resource-id) {:delete (delete-resource-response-fn resource-id)}}
      (pc/delete-resource (create-fake-client) resource-id)
      (is true "Resource deleted successfully."))))

(defn update-resource-response-fn [id resource-type]
  (fn [{:keys [uri body]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body (json/encode (assoc (json/decode (slurp body) true) :id id :resource_type resource-type))}
      {:status 400 :body ""})))

(deftest test-update-resource []
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

(deftest test-delete-resource-type []
  (let [id "f41c182b-721a-4e6d-94a5-d9db7144ddc7"]
    (with-fake-routes {(fake-url "resource_types" id) (delete-resource-type-response-fn id)}
      (pc/delete-resource-type (create-fake-client) id)
      (is true "Resource type deleted successfully."))))

(defn update-resource-type-response-fn [id]
  (fn [{:keys [uri body]}]
    (if (= id (last (string/split uri #"/")))
      {:status 200 :body (json/encode (assoc (json/decode (slurp body) true) :id id))}
      {:status 400 :body ""})))

(deftest test-update-resource-type []
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

(defn list-perms-response [_]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    (json/encode fake-perms)})

(deftest test-list-perms []
  (with-fake-routes {(fake-url "permissions") {:get list-perms-response}}
    (is (= (pc/list-permissions (create-fake-client)) fake-perms))))

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

(deftest test-grant-perm []
  (let [[rt rn st sn l] ["app" "a" "user" "ipcdev" "own"]]
    (with-fake-routes {(fake-url "permissions" "resources" rt rn "subjects" st sn) {:put grant-perm-response}}
      (is (= (pc/grant-permission (create-fake-client) "app" "a" "user" "ipcdev" "own")
             (fake-perm "app" "a" "user" "ipcdev" "own"))))))
