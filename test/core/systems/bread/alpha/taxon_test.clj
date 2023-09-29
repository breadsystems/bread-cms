(ns systems.bread.alpha.taxon-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.taxon :as taxon]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-dispatch-taxon-queries
  (let [db ::FAKEDB
        app (plugins->loaded [(datastore->plugin db)
                              (i18n/plugin {:query-strings? false
                                            :query-lang? false})
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
      ;; Not querying for any translatable content.
      [{:query/name ::store/query
        :query/key :taxon
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id :taxon/slug]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :taxon}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id :taxon/whatever]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :post/type and :post/status have no effect without :post/_taxons
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id :taxon/whatever]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :post/status :post.status/draft
       :post/type :post.type/article
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/by-taxon/category/some-tag"}
      ;; Query includes :taxon/field as a map.
      [{:query/name ::store/query
        :query/key :taxon
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id :taxon/slug :translatable/fields]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/category
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:taxon :translatable/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?e0 ?lang]
           :where [[?e :field/lang ?lang]
                   [?e0 :translatable/fields ?e]]}
         [::bread/data :taxon :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :taxon}]
      {:dispatcher/type :dispatcher.type/taxon
       :dispatcher/pull [:taxon/slug
                         {:translatable/fields [:field/key
                                                :field/content]}]
       :dispatcher/key :taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; Default :post/type and :post/status with :post/_taxons
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id
                             ;; :taxon/posts
                             :post/_taxons]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons]
        :query/db db
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])]
           :in [$ ?taxon ?type ?status]
           :where [[?post :post/taxons ?taxon]
                   [?post :post/type ?type]
                   [?post :post/status ?status]]}
         [::bread/data :tag :db/id]
         :post.type/page
         :post.status/published]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons :translatable/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?e1 ?lang]
           :where [[?e :field/lang ?lang]
                   [?e0 :translatable/fields ?e]
                   [?e0 :post/taxons ?e1]]}
         [::bread/data :tag :post/_taxons :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; Custom :post/type and :post/status with :post/_taxons
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id
                             ;; :taxon/posts
                             :post/_taxons]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons]
        :query/db db
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])]
           :in [$ ?taxon ?type ?status]
           :where [[?post :post/taxons ?taxon]
                   [?post :post/type ?type]
                   [?post :post/status ?status]]}
         [::bread/data :tag :db/id]
         :post.type/article
         :post.status/draft]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons :translatable/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?e1 ?lang]
           :where [[?e :field/lang ?lang]
                   [?e0 :translatable/fields ?e]
                   [?e0 :post/taxons ?e1]]}
         [::bread/data :tag :post/_taxons :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :post/status :post.status/draft
       :post/type :post.type/article
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag with :post/type and :post/status nil
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id
                             ;; :taxon/posts
                             :post/_taxons]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons]
        :query/db db
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])]
           :in [$ ?taxon]
           :where [[?post :post/taxons ?taxon]]}
         [::bread/data :tag :db/id]]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons :translatable/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?e1 ?lang]
           :where [[?e :field/lang ?lang]
                   [?e0 :translatable/fields ?e]
                   [?e0 :post/taxons ?e1]]}
         [::bread/data :tag :post/_taxons :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :post/type nil
       :post/status nil
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag with :post/type and :post/status false
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id
                             ;; :taxon/posts
                             :post/_taxons]) .]
           :in [$ ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons]
        :query/db db
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])]
           :in [$ ?taxon]
           :where [[?post :post/taxons ?taxon]]}
         [::bread/data :tag :db/id]]}
       {:query/name ::store/query
        :query/key [:tag :post/_taxons :translatable/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?e1 ?lang]
           :where [[?e :field/lang ?lang]
                   [?e0 :translatable/fields ?e]
                   [?e0 :post/taxons ?e1]]}
         [::bread/data :tag :post/_taxons :db/id]
         :en]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :post/type false ; same as explicit nil.
       :post/status false ; same as explicit nil.
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; TODO Dynamic params from request...
      #_#_
      [{:query/name ::store/query
        :query/key :tag
        :query/db db
        :query/args
        ['{:find [(pull ?e0 [:db/id :taxon/whatever]) .]
           :in [$ ?status ?taxonomy ?slug]
           :where [[?e0 :taxon/taxonomy ?taxonomy]
                   [?e0 :taxon/slug ?slug]]}
         :post.status/draft ; populated from req
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::taxon/compact
        :query/key :tag}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [:taxon/whatever]
       :dispatcher/key :tag
       :dispatcher/params {:post/status :status
                           :post/type :type}
       :route/params {:lang "en" :slug "some-tag"
                      :type "article" :status "draft"}}

)))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
