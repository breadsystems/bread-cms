(ns systems.bread.alpha.taxon
  (:require
    [clojure.set :refer [rename-keys]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.query-inference :as qi]))

(defmethod bread/expand ::filter-posts
  [{k :expansion/key post-type :post/type post-status :post/status} data]
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
    {:expansions (if (seq bindings)
                   (vec (mapcat
                          (fn [query]
                            (bread/hook req ::i18n/expansions query))
                          [{:expansion/name ::db/query
                            :expansion/key k
                            :expansion/db (db/database req)
                            :expansion/args [query taxonomy (:slug params)]}
                           {:expansion/name ::filter-posts
                            :expansion/key :tag-with-posts
                            :post/type post-type
                            :post/status post-status}]))
                   (bread/hook
                     req ::i18n/expansions
                     {:expansion/name ::db/query
                      :expansion/key k
                      :expansion/db (db/database req)
                      :expansion/args [query taxonomy (:slug params)]}))}))

(defmethod dispatcher/dispatch :dispatcher.type/tag
  [{::bread/keys [dispatcher] :as req}]
  (let [dispatcher (assoc dispatcher
                          :dispatcher/type :dispatcher.type/taxon
                          :taxon/taxonomy :taxon.taxonomy/tag)]
    (dispatcher/dispatch (assoc req ::bread/dispatcher dispatcher))))
