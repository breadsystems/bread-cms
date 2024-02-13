(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.query-inference :as qi]))

(defmethod bread/query ::compact
  [{k :query/key} data]
  (-> data
      (get k)
      (i18n/compact)
      (update :post/_taxons i18n/compact)
      (rename-keys {:post/_taxons :taxon/posts})))

;; TODO datalog-pull ...?
(defn- coalesce-query [query]
  (as-> query $
    (update-in $ [0 :in] (partial filterv identity))
    (update-in $ [0 :where] (partial filterv identity))
    (filterv identity $)))

(defn- posts-query [t status taxon-query spec path]
  (let [pull (get spec (last path))
        pull (if (some #{:db/id} pull) pull (cons :db/id pull))]
    {:query/name ::db/query
     :query/key path
     :query/db (:query/db taxon-query)
     :query/args
     (coalesce-query
       [{:find [(list 'pull '?post (vec pull))]
         :in ['$ '?taxon (when t '?type) (when status '?status)]
         :where ['[?post :post/taxons ?taxon]
                 (when t '[?post :post/type ?type])
                 (when status '[?post :post/status ?status])]}
        [::bread/data (first path) :db/id]
        (when t t) (when status status)])}))

(defmethod dispatcher/dispatch :dispatcher.type/taxon
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key
         params :route/params
         taxonomy :taxon/taxonomy
         post-type :post/type
         post-status :post/status
         :or {post-type :post.type/page
              post-status :post.status/published}} dispatcher
        pull-spec (vec (dispatcher/pull-spec dispatcher))
        orig-q {:find [(list 'pull '?e pull-spec)]
                :in '[$ ?taxonomy ?slug]
                :where '[[?e :taxon/taxonomy ?taxonomy]
                         [?e :taxon/slug ?slug]]}
        {:keys [query bindings]} (qi/infer-query-bindings
                                   :post/_taxons
                                   (fn [{:keys [target]}]
                                     {:in ['?type '?status]
                                      :where [[target :post/type '?type]
                                              [target :post/status '?status]]})
                                   vector?
                                   orig-q)]
    {:queries (bread/hook
                req ::i18n/queries*
                (if (seq bindings)
                  {:query/name ::db/query
                   :query/key k
                   :query/db (db/database req)
                   :query/args [query
                                taxonomy
                                (:slug params)
                                post-type
                                post-status]}
                  {:query/name ::db/query
                   :query/key k
                   :query/db (db/database req)
                   :query/args [orig-q taxonomy (:slug params)]}))}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
