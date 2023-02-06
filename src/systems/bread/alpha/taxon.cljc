(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.field :as field]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.util.datalog :as datalog]))

(def post-taxonomized-rule
  '[(post-taxonomized ?post ?taxonomy ?taxon-slug)
    [?post :post/taxons ?e0]
    [?e0 :taxon/taxonomy ?taxonomy]
    [?e0 :taxon/slug ?taxon-slug]])

(defmethod bread/query ::compact
  [{k :query/key} data]
  (-> data
      (get k)
      (update :taxon/fields field/compact)
      (update :post/_taxons #(map post/compact-fields %))
      (rename-keys {:post/_taxons :taxon/posts})))

(defmethod dispatcher/dispatch :dispatcher.type/taxon
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key
         params :route/params
         taxonomy :taxon/taxonomy
         post-type :post/type
         post-status :post/status
         :or {post-status :post.status/published}}
        dispatcher
        db (store/datastore req)
        pull-spec (dispatcher/pull-spec dispatcher)
        lang (keyword (:lang params))
        taxon-query
        {:query/name ::store/query
         :query/key k
         :query/db db
         :query/args
         (filter
           identity
           [{:find [(list 'pull '?e0 pull-spec) '.]
             :in (filter identity ['$ '%
                                   (when post-status '?status)
                                   (when post-type '?type)
                                   '?taxonomy '?slug])
             :where (filter
                      identity
                      ['[?e0 :taxon/slug ?slug]
                       (when post-status
                         '[?p :post/status ?status])
                       (when post-type
                         '[?p :post/type ?type])
                       '(post-taxonomized ?p ?taxonomy ?slug)])}
            [post-taxonomized-rule]
            post-status ;; possibly nil
            post-type ;; possibly nil
            taxonomy
            (:slug params)])}
        compact-query {:query/name ::compact :query/key k}]
    {:queries (conj (bread/hook req ::i18n/queries [taxon-query])
                    compact-query)}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
