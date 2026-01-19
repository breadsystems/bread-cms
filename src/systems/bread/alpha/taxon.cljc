(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [com.rpl.specter :as s]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.query-inference :as qi]))

(defmethod bread/expand ::filter-posts
  [{k :expansion/key post-type :post/type post-status :post/status} data]
  (update (get data k)
          :post/_taxons
          (fn [posts]
            (filter (fn [post]
                      (and (or (nil? post-type)   (= post-type   (:post/type post)))
                           (or (nil? post-status) (= post-status (:post/status post)))))
                    posts))))

(defmethod bread/dispatch ::taxon
  [{::bread/keys [dispatcher] :as req}]
  (let [{k :dispatcher/key
         params :route/params
         taxonomy :taxon/taxonomy
         post-type :post/type
         post-status :post/status
         slug-param :route/slug-param
         :or {post-type :page
              post-status :post.status/published
              slug-param :thing/slug}} dispatcher
        pull-spec (vec (dispatcher/pull-spec dispatcher))
        ;; NOTE: because of how pull works, we can't specify the pull spec of the posts within
        ;; the requested taxon ~while also filtering those posts~ in the same query.
        ;; Instead, we query for all the posts and then filter them in memory.
        query {:find [(list 'pull '?e pull-spec) '.]
               :in '[$ ?taxonomy ?slug]
               :where '[[?e :taxon/taxonomy ?taxonomy]
                        [?e :thing/slug ?slug]]}
        {:keys [bindings]} (qi/infer-query-bindings :post/_taxons vector? query)]
    {:expansions (if (seq bindings)
                   (let [{:keys [binding-path entity-index]} (first bindings)
                         path (concat [:find         ;; find clause
                                       entity-index  ;; find position
                                       s/LAST]       ;; pull-expr
                                      binding-path) ;; within pull-expr
                         query (s/transform path #(conj % :post/type :post/status) query)
                         expansion {:expansion/name ::db/query
                                    :expansion/key k
                                    :expansion/db (db/database req)
                                    :expansion/args [query taxonomy (slug-param params)]}]
                     (concat (bread/hook req ::i18n/expansions expansion)
                             [{:expansion/name ::filter-posts
                               :expansion/key k
                               :post/type post-type
                               :post/status post-status}]))
                   (do
                     (bread/hook
                     req ::i18n/expansions
                     {:expansion/name ::db/query
                      :expansion/key k
                      :expansion/db (db/database req)
                      :expansion/args [query taxonomy (slug-param params)]})))}))

(defmethod bread/dispatch ::tag=>
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type ::taxon
                          :taxon/taxonomy :tag)]
    (bread/dispatch (assoc req ::bread/dispatcher dispatcher))))
