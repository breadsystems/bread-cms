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
    [?post :post/taxons ?e]
    [?e :taxon/taxonomy ?taxonomy]
    [?e :taxon/slug ?taxon-slug]])

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
        ;; If we're querying for fields, we'll want to run a special query
        ;; to correctly handle i18n.
        fields-binding (datalog/attr-binding :taxon/fields pull-spec)
        ;; Don't query for fields in initial taxon query, it won't be properly
        ;; internationalized anyway.
        pull-spec (if fields-binding
                    (filter #(not= fields-binding %) pull-spec)
                    pull-spec)
        taxon-inputs (filter identity ['$ '%
                                       (when post-status '?status)
                                       (when post-type '?type)
                                       '?taxonomy '?slug])
        taxon-query
        {:query/name ::store/query
         :query/key k
         :query/db db
         :query/args
         (filter
           identity
           [{:find [(list 'pull '?e pull-spec) '.]
             :in taxon-inputs
             :where (filter
                      identity
                      ['[?e :taxon/slug ?slug]
                       (when post-status
                         '[?p :post/status ?status])
                       (when post-type
                         '[?p :post/type ?type])
                       '(post-taxonomized ?p ?taxonomy ?slug)])}
            [post-taxonomized-rule]
            post-status
            post-type
            taxonomy
            (:slug params)])}
        fields-query
        (when fields-binding
          (let [field-keys (or (:taxon/fields fields-binding)
                               [:field/key :field/content])]
            {:query/name ::store/query
             :query/key [k :taxon/fields]
             :query/db db
             :query/args
             [{:find [(list 'pull '?f (cons :db/id field-keys))]
               :in '[$ ?e ?lang]
               :where '[[?e :taxon/fields ?f]
                        [?f :field/lang ?lang]]}
              [::bread/data k :db/id]
              ;; TODO i18n/lang
              (keyword (:lang params))]}))
        compact-query {:query/name ::compact :query/key k}]
    {:queries (if fields-query
                [taxon-query fields-query compact-query]
                [taxon-query compact-query])}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
