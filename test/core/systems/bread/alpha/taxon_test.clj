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

#_ ;; TODO move taxon inference to its own hook?
(deftest test-taxon-inference
  (let [app (plugins->loaded [(db->plugin ::FAKEDB)
                              {:hooks
                               {::bread/attrs-map {:attr :ATTR}}}])]
    (are
      [query ])))

(deftest test-dispatch-taxon-queries
  (let [attrs-map {:translatable/fields {:db/cardinality :db.cardinality/many}
                   :post/_taxons        {:db/cardinality :db.cardinality/many}}
        app (plugins->loaded [(db->plugin ::FAKEDB)
                              (i18n/plugin {:query-strings? false
                                            :query-lang? false
                                            :compact-fields? false})
                              (dispatcher/plugin)
                              {:hooks
                               {::bread/attrs-map
                                [{:action/name ::bread/value
                                  :action/value attrs-map}]}}])
        ->app (fn [dispatcher]
                (assoc app ::bread/dispatcher dispatcher))]

    (are
      [query dispatcher]
      (= query (let [counter (atom 0)
                     gensym* (fn [prefix]
                               (symbol (str prefix (swap! counter inc))))]
                 (with-redefs [gensym gensym*]
                   (-> dispatcher
                       ->app
                       (bread/hook ::bread/dispatch)
                       ::bread/queries))))

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Not querying for any translatable content.
      [{:query/name ::db/query
        :query/key :taxon
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id :taxon/slug])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/category
         "some-tag"]}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag
      [{:query/name ::db/query
        :query/key :tag
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id :taxon/whatever])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :post/type and :post/status have no effect without :post/_taxons
      [{:query/name ::db/query
        :query/key :tag
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id :taxon/whatever])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :post/status :post.status/draft
       :post/type :post.type/article
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Query includes :translatable/field as a map.
      [{:query/name ::db/query
        :query/key :taxon
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :taxon/slug
                            {:translatable/fields
                             [:field/key :field/content]}])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::i18n/filter-fields
        :query/key :taxon
        :field/lang :en
        :spath [:translatable/fields]}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug
                         {:translatable/fields [:field/key
                                                :field/content]}]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; Default :post/type and :post/status with :post/_taxons
      [{:query/name ::db/query
        :query/key :tag-with-posts
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            {:post/_taxons
                             [{:translatable/fields
                               [:field/key :field/content]}]}
                            {:translatable/fields
                             [:field/key :field/content]}])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::i18n/filter-fields
        :query/key :tag-with-posts
        :field/lang :en
        :spath [:post/_taxons s/ALL :translatable/fields]}
       {:query/name ::i18n/filter-fields
        :query/key :tag-with-posts
        :field/lang :en
        :spath [:translatable/fields]}
       {:query/name ::taxon/filter-posts
        :query/key :tag-with-posts
        :post/type :post.type/page
        :post/status :post.status/published}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}
                         {:translatable/fields [:field/key :field/content]}]
       :dispatcher/key :tag-with-posts
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :post.type/article and :post.status/draft with :post/_taxons
      [;; Query for the taxon and its relation.
       {:query/name ::db/query
        :query/key :tag-with-posts
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            {:post/_taxons
                             [{:translatable/fields
                               [:field/key :field/content]}]}
                            {:translatable/fields
                             [:field/key :field/content]}])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       ;; Filter the taxon's posts' fields by lang.
       {:query/name ::i18n/filter-fields
        :query/key :tag-with-posts
        :field/lang :en
        :spath [:post/_taxons s/ALL :translatable/fields]}
       ;; Filter the taxon's own fields by lang.
       {:query/name ::i18n/filter-fields
        :query/key :tag-with-posts
        :field/lang :en
        :spath [:translatable/fields]}
       ;; Filter the taxon's posts by type and status.
       {:query/name ::taxon/filter-posts
        :query/key :tag-with-posts
        :post/type :post.type/article
        :post/status :post.status/draft}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}
                         {:translatable/fields [:field/key :field/content]}]
       :dispatcher/key :tag-with-posts
       :route/params {:lang "en" :slug "some-tag"}
       :post/type :post.type/article
       :post/status :post.status/draft}

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
