(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.query-inference :as qi]))

(defmethod bread/query ::filter-posts
  [{k :query/key post-type :post/type post-status :post/status} data]
  (filter (fn [post]
            (prn post-type post-status '? ((juxt :post/type :post/status) post))
            (doto (and (or (nil? post-type)   (= post-type   (:post/type post)))
                 (or (nil? post-status) (= post-status (:post/status post)))) prn))
          (get-in data [k :post/_taxons])))

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
        query {:find [(list 'pull '?e pull-spec) '.]
               :in '[$ ?taxonomy ?slug]
               :where '[[?e :taxon/taxonomy ?taxonomy]
                        [?e :thing/slug ?slug]]}
        {:keys [bindings]} (qi/infer-query-bindings
                             :post/_taxons
                             vector?
                             query)]
    {:queries (if (seq bindings)
                (vec (mapcat
                       (fn [query]
                         (bread/hook req ::i18n/queries query))
                       [{:query/name ::db/query
                         :query/key k
                         :query/db (db/database req)
                         :query/args [query taxonomy (:slug params)]}
                        {:query/name ::filter-posts
                         :query/key :tag-with-posts
                         :post/type post-type
                         :post/status post-status}]))
                (bread/hook
                  req ::i18n/queries
                  {:query/name ::db/query
                   :query/key k
                   :query/db (db/database req)
                   :query/args [query taxonomy (:slug params)]}))}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
