(ns systems.bread.alpha.taxon-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.taxon :as taxon]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-dispatch-taxon-queries
  (let [db ::FAKEDB
        app (plugins->loaded [(datastore->plugin db)
                              (dispatcher/plugin)])
        ->app (fn [dispatcher]
                (assoc app ::bread/dispatcher dispatcher))]

    (are
      [query dispatcher]
      (= query (-> dispatcher
                   ->app
                   (bread/hook ::bread/dispatch)
                   ::bread/queries))

      ;; {:uri "/en/by-taxon/category/some-tag"}
      [{:query/name ::store/query
        :query/key :taxon
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :post/title :custom/key]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [;; TODO support post/type
                   [?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :taxon}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :post/title :custom/key]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [;; TODO support post/type
                   [?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}})))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
