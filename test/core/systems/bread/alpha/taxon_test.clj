(ns systems.bread.alpha.taxon-test
  (:require
    [clojure.test :refer [deftest are]]
    [com.rpl.specter :as s]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.taxon :as taxon]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded]]))

(deftest test-dispatch-taxon-expansions
  (let [attrs-map {:thing/fields {:db/cardinality :db.cardinality/many}
                   :post/taxons  {:db/cardinality :db.cardinality/many}}
        app (plugins->loaded [(db->plugin ::FAKEDB)
                              (i18n/plugin {:query-strings? false
                                            :query-lang? false})
                              (dispatcher/plugin)
                              {:hooks
                               {::bread/attrs-map
                                [{:action/name ::bread/value
                                  :action/value attrs-map}]}}])]

    (are
      [expansions dispatcher]
      (= expansions (let [counter (atom 0)]
                   (-> (assoc app ::bread/dispatcher dispatcher)
                       (bread/hook ::bread/dispatch)
                       ::bread/expansions)))

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Not querying for any translatable content.
      [{:expansion/name ::db/query
        :expansion/key :taxon
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id :thing/slug]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :thing/slug ?slug]]}
         :taxon.taxonomy/category
         "some-tag"]}]
      {:dispatcher/type ::taxon/taxon
       :dispatcher/pull [:thing/slug]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; ::taxon/tag
      [{:expansion/name ::db/query
        :expansion/key :tag
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id :taxon/whatever]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :thing/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}]
      {:dispatcher/type ::taxon/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :post/type and :post/status have no effect without :post/_taxons
      [{:expansion/name ::db/query
        :expansion/key :tag
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id :taxon/whatever]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :thing/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}]
      {:dispatcher/type ::taxon/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :post/status :post.status/draft
       :post/type :post.type/article
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Query includes :thing/field as a map.
      [{:expansion/name ::db/query
        :expansion/key :taxon
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :thing/slug ?slug]]}
         :taxon.taxonomy/category
         "some-tag"]}
       {:expansion/name ::i18n/fields
        :expansion/key :taxon
        :expansion/description "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}
        :spaths [[:thing/fields]]}]
      {:dispatcher/type ::taxon/taxon
       :dispatcher/pull [:thing/slug
                         {:thing/fields [:field/key
                                         :field/content]}]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; Default :post/type and :post/status with :post/_taxons
      [{:expansion/name ::db/query
        :expansion/key :tag-with-posts
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            {:post/_taxons
                             [{:thing/fields
                               [:db/id :field/lang :field/key :field/content]}]}
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :thing/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:expansion/name ::i18n/fields
        :expansion/key :tag-with-posts
        :expansion/description "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}
        :spaths [[:post/_taxons s/ALL :thing/fields]
                 [:thing/fields]]}
       {:expansion/name ::taxon/filter-posts
        :expansion/key :tag-with-posts
        :post/type :post.type/page
        :post/status :post.status/published}]
      {:dispatcher/type ::taxon/tag
       :dispatcher/pull [{:post/_taxons [{:thing/fields [:field/key
                                                         :field/content]}]}
                         {:thing/fields [:field/key :field/content]}]
       :dispatcher/key :tag-with-posts
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :post.type/article and :post.status/draft with :post/_taxons
      [;; Query for the taxon and its relation.
       {:expansion/name ::db/query
        :expansion/key :tag-with-posts
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            {:post/_taxons
                             [{:thing/fields
                               [:db/id :field/lang :field/key :field/content]}]}
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :thing/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:expansion/name ::i18n/fields
        :expansion/key :tag-with-posts
        :expansion/description "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}
        :spaths [[:post/_taxons s/ALL :thing/fields]
                 [:thing/fields]]}
       {:expansion/name ::taxon/filter-posts
        :expansion/key :tag-with-posts
        :post/type :post.type/article
        :post/status :post.status/draft}]
      {:dispatcher/type ::taxon/tag
       :dispatcher/pull [{:post/_taxons [{:thing/fields [:field/key
                                                         :field/content]}]}
                         {:thing/fields [:field/key :field/content]}]
       :dispatcher/key :tag-with-posts
       :route/params {:lang "en" :slug "some-tag"}
       :post/type :post.type/article
       :post/status :post.status/draft}

      ;;
      )))

(deftest test-filter-posts-hook
  (let [posts
        [{:post/type :page    :post/status :published :thing/slug "one"}
         {:post/type :article :post/status :published :thing/slug "two"}
         {:post/type :page    :post/status :published :thing/slug "three"}
         {:post/type :article :post/status :published :thing/slug "four"}
         {:post/type :page    :post/status :draft     :thing/slug "five"}]]
    (are
      [filtered-slugs post-type post-status]
      (= filtered-slugs (->> {:the-query-key {:post/_taxons posts}}
                             (bread/expand {:expansion/name ::taxon/filter-posts
                                            :expansion/key :the-query-key
                                            :post/type post-type
                                            :post/status post-status})
                             (map :thing/slug)))

      ;; Filter by post type only.
      ["one" "three" "five"]
      :page nil

      ;; Filter by post status only.
      ["one" "two" "three" "four"]
      nil :published

      ;; Filter by post type AND status.
      ["one" "three"]
      :page :published

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
