(ns systems.bread.alpha.route
  (:require
    [systems.bread.alpha.component :as comp]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn component [req]
  (bread/hook-> req :hook/component))

(defn entity-id [req]
  (bread/hook req :hook/id))

(defn entity [req]
  (let [cmp (component req)
        eid (entity-id req)
        {:query/keys [schema ident]} (comp/get-query cmp {:db/id eid})]
    (when ident
      (store/pull (store/datastore req) schema ident))))
