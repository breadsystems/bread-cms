(ns systems.bread.alpha.route
  (:require
    [systems.bread.alpha.component :as comp]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn match [req]
  (bread/hook->> req :hook/match-route))

(defn params [req match]
  (bread/hook->> req :hook/route-params match))

(defn resolver [req]
  ;; TODO make default resolver behavior a little more dynamic
  (bread/hook->> req :hook/resolver (merge {:resolver/attr :slugs
                                            :resolver/internationalize? true
                                            :resolver/type :post
                                            :resolver/ancestry? true
                                            :post/type :post.type/page}
                                           (bread/hook->> req :hook/match->resolver (match req)))))

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
