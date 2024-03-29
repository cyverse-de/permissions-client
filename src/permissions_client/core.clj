(ns permissions-client.core
  (:use [medley.core :only [map-keys remove-vals]])
  (:require [cemerick.url :as curl]
            [clj-http.client :as http]
            [clojure.string :as string]
            [honey.sql.helpers :as h]))

(defprotocol Client
  "A client library for the Permissions API."
  (get-status [_]
    "Retrieves information about the status of the permissions service.")

  (list-subjects [_] [_ opts]
    "Lists subjects defined in the permissions service.")

  (add-subject [_ external-id subject-type]
    "Registers a subject in the permissions service. The external-id field is the subject ID known to the
     client. For clients that use Grouper, this subject ID should the same as the one used by Grouper. The
     subject ID must be unique within the permissions database. The subject-type field can be either 'user'
     or 'group'.")

  (delete-subject [_ id] [_ external-id subject-type]
    "Removes a subject from the permissions database.")

  (update-subject [_ id external-id subject-type]
    "Updates a subject in the permissions service. The external-id field is the subject ID known to the client.
     For clients that use Grouper, this subject ID should be the same as the one used by Grouper. The subject
     ID must be unique within the permissions database. The subject-type field can be either 'user' or 'group'.")

  (list-resources [_] [_ opts]
    "Lists resources defined in the permissions service.")

  (add-resource [_ resource-name resource-type]
    "Adds a resource to the permissions service. The resource-name field is the name or identifier that is used
      by the client to refer to the resource. This field must be unique among resources of the same type. The
      resource-type field is the name of the resource type, which must have been registered in the permission
      database already.")

  (delete-resource [_ id] [_ resource-name resource-type]
    "Removes a resource from the permissions service.")

  (update-resource [_ id resource-name]
    "Updates a resource in the permissions service. The resource-name field is the name or identifier that is
     used by the client to refer to the resource. This field must be unique among resources of the same type.
     The type of an existing resource may not be modified.")

  (list-resource-types [_] [_ opts]
    "Lists resource types registered in the permissions service.")

  (add-resource-type [_ resource-type-name description]
    "Adds a resource type to the permissions service. A resource type is a class of entities to which
     permissions may be assigned. For example, the Discovery Environment uses two resource types, 'app'
     and 'analysis', with individual apps or analyses being registered as resources of their respective
     types. Each resource type must have a unique name.")

  (delete-resource-type [_ id]
    "Removes the resource type with the given ID from the permissions service. A resource type with associated
     resources may not be deleted.")

  (delete-resource-type-by-name [_ resource-type-name]
    "Removes the resource type with the given name from the permissions service. A resource type with associated
     resources may not be deleted.")

  (update-resource-type [_ id resource-type-name description]
    "Updates a resource type in the permissions service. Each resource type must have a unique name.")

  (list-permissions [_]
    "Lists all permissions known to the permissions service.")

  (copy-permissions [_ source-type source-id subjects]
    "Copies permissions from one subject to one or more other subjects. Only permissions that are assigned
     directly to the source subject are copied. The source-type parameter contains the type of the source
     subject. The source-id parameter contains the subject ID from Grouper. The subjects parameter contains
     a list of destination subjects, with each subject containing :subject_type and :subject_id keys.")

  (grant-permission [_ resource-type resource-name subject-type subject-id level]
    "Grants permission to access a resource to a user. The resource-type, resource-name, subject-type, and
     subject-id fields have the same meanings as in the resources and subjects methods. Neither the resource
     nor the subject need to be registered before calling this ednpoint; they will be added to the database
     if necessary. The permission level must correspond to one of the available levels in the permissions
     service. The currently available levels are 'read', 'admin', 'write', and 'own'.")

  (revoke-permission [_ resource-type resource-name subject-type subject-id]
    "Revokes a permission that has previously been granted.")

  (list-resource-permissions [_ resource-type resource-name]
    "Lists all permissions associated with a resource.")

  (get-subject-permissions
    [_ subject-type subject-id lookup?]
    [_ subject-type subject-id lookup? min-level]
    "Looks up permissions that have been granted to a subject. If the 'lookup?' flag is set to 'true' and the
     subject happens to be a user then the most privileged permissions available to the user or any group that
     the user belongs to (as determined by Grouper) will be listed. If the 'lookup?' flag is set to 'false' or
     the subject is a group then only permissions that were granted directly to the subject will be listed.")

  (get-subject-permissions-for-resource-type
    [_ subject-type subject-id resource-type lookup?]
    [_ subject-type subject-id resource-type lookup? min-level]
    "Looks up permissions that have been granted to a subject for a single resource type. If the 'lookup?' flag
     is set to 'true' and the subject happens to be a user then the most privileged permissions available to the
     user or any group that the user belongs to (as determined by Grouper) will be listed. If the 'lookup?'
     flag is set to 'false' or the subject is a group then only permissions that were granted directly to the
     subject will be listed.")

  (get-abbreviated-subject-permissions-for-resource-type
    [_ subject-type subject-id resource-type lookup?]
    [_ subject-type subject-id resource-type lookup? min-level]
    "Looks up permissions that have been granted to a subject for a single resource type. If the 'lookup?' flag
     is set to 'true' and the subject happens to be a user then the most privileged permissions available to the
     user or any group that the user belongs to (as determined by Grouper) will be listed. If the 'lookup?'
     flag is set to 'false' or the subject is a group then only permissions that were granted directly to the
     subject will be listed. The only difference between `get-subject-permissions-for-resource-type` and this
     method is that this one returns less information in order to reduce the amount of data that needs to be
     serialized and deserialized.")

  (get-subject-permissions-for-resource
    [_ subject-type subject-id resource-type resource-name lookup?]
    [_ subject-type subject-id resource-type resource-name lookup? min-level]
    "Looks up permissions that have been granted to a subject for a single resource. If the 'lookup?' flag is
     set to 'true' and the subject happens to be a user then the most privileged permissions available to the
     user or any group that the user belongs to (as determined by Grouper) will be listed. If the 'lookup?'
     flag is set to 'false' or the subject is a group then only permissions that were granted directly to the
     subject will be listed.")

  (accessible-resource-query-dsl
    [_ subject-ids resource-type]
    [_ subject-ids resource-type min-level]
    "Returns the HoneySQL DSL representing a query that can be used to find resource IDs that are accessible to one or
     more subjects. The `subject-ids` argument should be an SQL array containing a list of subject IDs. The
     `resource-type` argument is the name of the resource type as defined in the permissions service. The `min-level`
     argument is the minimum permission level required for the query. For example, if the `min-level` parameter is
     `write` then resources for which the user or users have at most `read` or `admin` (limited write) access will not
     be included in the result set."))

(defn- build-url [base-url & path-elements]
  (str (apply curl/url base-url path-elements)))

(defn- prepare-opts [opts ks]
  (remove-vals nil? (select-keys opts ks)))

(defn- t [schema-name table-name]
  (keyword (str (name schema-name) "." (name table-name))))

(deftype PermissionsClient [base-url schema-name]
  Client

  (get-status [_]
    (:body (http/get base-url {:as :json})))

  (list-subjects [_]
    (:body (http/get (build-url base-url "subjects")
                     {:as :json})))

  (list-subjects [_ opts]
    (:body (http/get (build-url base-url "subjects")
                     {:query-params (prepare-opts opts [:subject_id :subject_type])
                      :as           :json})))

  (add-subject [_ external-id subject-type]
    (:body (http/post (build-url base-url "subjects")
                      {:form-params  {:subject_id   external-id
                                      :subject_type subject-type}
                       :content-type :json
                       :as           :json})))

  (delete-subject [_ id]
    (http/delete (build-url base-url "subjects" id))
    nil)

  (delete-subject [_ external-id subject-type]
    (http/delete (build-url base-url "subjects")
                 {:query-params {:subject_id   (str external-id)
                                 :subject_type (str subject-type)}})
    nil)

  (update-subject [_ id external-id subject-type]
    (:body (http/put (build-url base-url "subjects" id)
                     {:form-params  {:subject_id   external-id
                                     :subject_type subject-type}
                      :content-type :json
                      :as           :json})))

  (list-resources [_]
    (:body (http/get (build-url base-url "resources") {:as :json})))

  (list-resources [_ opts]
    (:body (http/get (build-url base-url "resources")
                     {:query-params (prepare-opts opts [:resource_type_name :resource_name])
                      :as           :json})))

  (add-resource [_ resource-name resource-type]
    (:body (http/post (build-url base-url "resources")
                      {:form-params  {:name          resource-name
                                      :resource_type resource-type}
                       :content-type :json
                       :as           :json})))

  (delete-resource [_ id]
    (http/delete (build-url base-url "resources" id))
    nil)

  (delete-resource [_ resource-name resource-type]
    (http/delete (build-url base-url "resources")
                 {:query-params {:resource_type_name (str resource-type)
                                 :resource_name      (str resource-name)}})
    nil)

  (update-resource [_ id resource-name]
    (:body (http/put (build-url base-url "resources" id)
                     {:form-params  {:name resource-name}
                      :content-type :json
                      :as           :json})))

  (list-resource-types [_]
    (:body (http/get (build-url base-url "resource_types") {:as :json})))

  (list-resource-types [_ opts]
    (:body (http/get (build-url base-url "resource_types")
                     {:query-params (prepare-opts opts [:resource_type_name])
                      :as           :json})))

  (add-resource-type [_ resource-type-name description]
    (:body (http/post (build-url base-url "resource_types")
                      {:form-params  {:name        resource-type-name
                                      :description description}
                       :content-type :json
                       :as           :json})))

  (delete-resource-type [_ id]
    (http/delete (build-url base-url "resource_types" id))
    nil)

  (delete-resource-type-by-name [_ resource-type-name]
    (http/delete (build-url base-url "resource_types")
                 {:query-params {:resource_type_name (str resource-type-name)}})
    nil)

  (update-resource-type [_ id resource-type-name description]
    (:body (http/put (build-url base-url "resource_types" id)
                     {:form-params  {:name        resource-type-name
                                     :description description}
                      :content-type :json
                      :as           :json})))

  (list-permissions [_]
    (:body (http/get (build-url base-url "permissions") {:as :json})))

  (copy-permissions [_ source-type source-id subjects]
    (http/post (build-url base-url "permissions" "subjects" source-type source-id "copy")
               {:form-params  {:subjects subjects}
                :content-type :json})
    nil)

  (grant-permission [_ resource-type resource-name subject-type subject-id level]
    (:body (http/put (build-url base-url "permissions" "resources" resource-type resource-name "subjects"
                                subject-type subject-id)
                     {:form-params  {:permission_level level}
                      :content-type :json
                      :as           :json})))

  (revoke-permission [_ resource-type resource-name subject-type subject-id]
    (http/delete (build-url base-url "permissions" "resources" resource-type resource-name "subjects" subject-type
                            subject-id))
    nil)

  (list-resource-permissions [_ resource-type resource-name]
    (:body (http/get (build-url base-url "permissions" "resources" resource-type resource-name) {:as :json})))

  (get-subject-permissions [_ subject-type subject-id lookup?]
    (:body (http/get (build-url base-url "permissions" "subjects" subject-type subject-id)
                     {:query-params {:lookup lookup?}
                      :as           :json})))

  (get-subject-permissions [_ subject-type subject-id lookup? min-level]
    (:body (http/get (build-url base-url "permissions" "subjects" subject-type subject-id)
                     {:query-params {:lookup lookup? :min_level min-level}
                      :as           :json})))

  (get-subject-permissions-for-resource-type [_ subject-type subject-id resource-type lookup?]
    (:body (http/get (build-url base-url "permissions" "subjects" subject-type subject-id resource-type)
                     {:query-params {:lookup lookup?}
                      :as           :json})))

  (get-subject-permissions-for-resource-type [_ subject-type subject-id resource-type lookup? min-level]
    (:body (http/get (build-url base-url "permissions" "subjects" subject-type subject-id resource-type)
                     {:query-params {:lookup lookup? :min_level min-level}
                      :as           :json})))

  (get-abbreviated-subject-permissions-for-resource-type [_ subject-type subject-id resource-type lookup?]
    (:body (http/get (build-url base-url "permissions" "abbreviated" "subjects" subject-type subject-id resource-type)
                     {:query-params {:lookup lookup?}
                      :as           :json})))

  (get-abbreviated-subject-permissions-for-resource-type [_ subject-type subject-id resource-type lookup? min-level]
    (:body (http/get (build-url base-url "permissions" "abbreviated" "subjects" subject-type subject-id resource-type)
                     {:query-params {:lookup lookup? :min_level min-level}
                      :as           :json})))

  (get-subject-permissions-for-resource [_ subject-type subject-id resource-type resource-name lookup?]
    (:body (http/get (build-url base-url "permissions" "subjects" subject-type subject-id resource-type resource-name)
                     {:query-params {:lookup lookup?}
                      :as           :json})))

  (get-subject-permissions-for-resource [_ subject-type subject-id resource-type resource-name lookup? min-level]
    (:body (http/get (build-url base-url "permissions" "subjects" subject-type subject-id resource-type resource-name)
                     {:query-params {:lookup lookup? :min_level min-level}
                      :as           :json})))

  (accessible-resource-query-dsl [client subject-ids resource-type]
    (accessible-resource-query-dsl client subject-ids resource-type "read"))

  (accessible-resource-query-dsl [_ subject-ids resource-type min-level]
    (if-not (instance? java.sql.Array subject-ids)
      (throw (IllegalArgumentException. "subject-ids must be an instance of java.sql.Array")))
    (-> (h/select-distinct [[:cast :pr.name :uuid] :id])
        (h/from [(t schema-name :permissions) :pp])
        (h/join [(t schema-name :subjects) :ps] [:= :pp.subject_id :ps.id])
        (h/join [(t schema-name :resources) :pr] [:= :pp.resource_id :pr.id])
        (h/join [(t schema-name :resource_types) :prt] [:= :pr.resource_type_id :prt.id])
        (h/join [(t schema-name :permission_levels) :pl] [:= :pp.permission_level_id :pl.id])
        (h/where [:= :ps.subject_id [:any subject-ids]])
        (h/where [:= :prt.name resource-type])
        (h/where [:<= :pl.precedence (-> (h/select :precedence)
                                         (h/from (t schema-name :permission_levels))
                                         (h/where [:= :name min-level]))]))))

(defn new-permissions-client
  ([]
   (new-permissions-client "http://permissions"))
  ([base-url]
   (new-permissions-client base-url :permissions))
  ([base-url schema-name]
   (PermissionsClient. base-url schema-name)))
