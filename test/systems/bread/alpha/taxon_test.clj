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
      ;; No field I18n.
      [{:query/name ::store/query
        :query/key :taxon
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :taxon/slug]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [[?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :taxon}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Query includes field I18n!
      [{:query/name ::store/query
        :query/key :taxon
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :taxon/slug]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [[?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:taxon :taxon/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?t ?lang]
           :where [[?t :taxon/fields ?f]
                   [?f :field/lang ?lang]]}
         [::bread/data :taxon :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :taxon}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug :taxon/fields]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Query includes :taxon/field as a map.
      [{:query/name ::store/query
        :query/key :taxon
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :taxon/slug]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [[?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:taxon :taxon/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content :field/lang])]
           :in [$ ?t ?lang]
           :where [[?t :taxon/fields ?f]
                   [?f :field/lang ?lang]]}
         [::bread/data :taxon :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :taxon}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug
                         {:taxon/fields [:field/key :field/content :field/lang]}]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag with :post/type
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :taxon/whatever]) .]
           :in [$ % ?status ?type ?taxonomy ?slug]
           :where [[?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   [?p :post/type ?type]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :post.type/page
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :post/type :post.type/page
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag with :post/status
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :taxon/whatever]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [[?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/draft
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :post/status :post.status/draft
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?t [:db/id :taxon/whatever]) .]
           :in [$ % ?status ?taxonomy ?slug]
           :where [[?t :taxon/slug ?slug]
                   [?p :post/status ?status]
                   (post-taxonomized ?p ?taxonomy ?slug)]}
         [taxon/post-taxonomized-rule]
         :post.status/published
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}}

      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
