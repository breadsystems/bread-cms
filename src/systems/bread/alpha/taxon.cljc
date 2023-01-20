(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.field :as field]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.datastore :as store]))

(def post-taxonomized-rule
  '[(post-taxonomized ?post ?taxonomy ?taxon-slug)
    [?post :post/taxons ?t]
    [?t :taxon/taxonomy ?taxonomy]
    [?t :taxon/slug ?taxon-slug]])

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
         taxonomy :taxon/taxonomy} dispatcher
        db (store/datastore req)
        pull-spec (dispatcher/pull-spec dispatcher)]
    {:queries [{:query/name ::store/query
                :query/key k
                :query/db db
                :query/args
                [{:find [(list 'pull '?t pull-spec) '.]
                  :in '[$ % ?status ?taxonomy ?slug]
                  :where '[[?t :taxon/slug ?slug]
                           [?p :post/status ?status]
                           (post-taxonomized ?p ?taxonomy ?slug)]}
                 [post-taxonomized-rule]
                 :post.status/published
                 taxonomy
                 (:slug params)]}
               {:query/name ::compact
                :query/key k}]}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
