(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.field :as field]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.internal.query-inference :as i]))

(defmethod bread/query ::compact
  [{k :query/key} data]
  (-> data
      (get k)
      (update :taxon/fields field/compact)
      (update :post/_taxons #(map post/compact-fields %))
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
    {:query/name ::store/query
     :query/key path
     :query/db (:query/db taxon-query)
     :query/args
     (coalesce-query
       [{:find [(list 'pull '?post pull)]
         :in ['$ '?taxon (when t '?type) (when status '?status)]
         :where ['[?post :post/taxons ?taxon]
                 (when t '[?post :post/type ?type])
                 (when status '[?post :post/status ?status])]}
        (when t t) (when status status)
        [::bread/data (first path) :db/id]])}))

(defmethod dispatcher/dispatch :dispatcher.type/taxon
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key
         params :route/params
         taxonomy :taxon/taxonomy
         post-type :post/type
         post-status :post/status
         :or {post-type :post.type/page
              post-status :post.status/published}}
        dispatcher
        db (store/datastore req)
        pull-spec (dispatcher/pull-spec dispatcher)
        taxon-query {:query/name ::store/query
                     :query/key k
                     :query/db db
                     :query/args
                     [{:find [(list 'pull '?e0 pull-spec) '.]
                       :in '[$ ?taxonomy ?slug]
                       :where '[[?e0 :taxon/taxonomy ?taxonomy]
                                [?e0 :taxon/slug ?slug]]}
                      taxonomy
                      (:slug params)]}
        taxon-queries (i/infer
                        [taxon-query] [:post/_taxons]
                        (partial posts-query post-type post-status))
        compact-query {:query/name ::compact :query/key k}]
    {:queries (conj (bread/hook req ::i18n/queries taxon-queries)
                    compact-query)}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
