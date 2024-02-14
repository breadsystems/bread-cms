(ns systems.bread.alpha.taxon-test
  (:require
    [clojure.test :refer [deftest are]]
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

(deftest ^:kaocha/skip test-dispatch-taxon-queries
  (let [attrs-map {:menu/items          {:db/cardinality :db.cardinality/many}
                   :translatable/fields {:db/cardinality :db.cardinality/many}
                   :post/children       {:db/cardinality :db.cardinality/many}}
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
        ['{:find [(pull ?e [:db/id :taxon/slug :translatable/fields])
                  (pull ?e1 [:db/id :field/key :field/content])]
           :in [$ ?taxonomy ?slug ?lang]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]
                   [?e :translatable/fields ?e1]
                   [?e1 :field/lang ?lang]]}
         :taxon.taxonomy/category
         "some-tag"
         :en]}
       {:query/name ::i18n/reconstitute
        :query/key :taxon
        :attrs-map attrs-map
        :bindings [{:entity-index 0
                    :relation-index 1
                    :relation [:translatable/fields]
                    :attr :translatable/fields
                    :binding-sym '?e1}]}]
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
        :query/key :tag
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id :post/_taxons])
                  (pull ?e1 [:db/id :translatable/fields])
                  ;; TODO why is this getting cast to a vector??
                  (pull ?e2 [:db/id :field/key :field/content])]
           :in [$ ?taxonomy ?slug ?type ?status ?lang]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]
                   [?e1 :post/type ?type]
                   [?e1 :post/status ?status]
                   [?e1 :translatable/fields ?e2]
                   [?e2 :field/lang ?lang]]}
         :taxon.taxonomy/tag
         "some-tag"
         :post.type/page
         :post.status/published
         :en]}
       {:query/name ::i18n/reconstitute
        :query/key :tag
        :attrs-map attrs-map
        :bindings [{:attr :translatable/fields
                    :binding-sym '?e2
                    :entity-index 1
                    :relation-index 2
                    :relation [:translatable/fields]}]}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; Custom :post/type and :post/status with :post/_taxons
      #_#_
      [{:query/name ::db/query
        :query/key :tag
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                             ;; :taxon/posts
                             :post/_taxons])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::db/query
        :query/key [:tag :post/_taxons]
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])
                  (pull ?e1 [:db/id :field/key :field/content])]
           :in [$ ?taxon ?type ?status ?lang]
           :where [[?post :post/taxons ?taxon]
                   [?post :post/type ?type]
                   [?post :post/status ?status]
                   [?post :translatable/fields ?e1]
                   [?e1 :field/lang ?lang]]}
         [::bread/data :tag :db/id]
         :post.type/article
         :post.status/draft
         :en]}
       {:query/name ::i18n/reconstitute
        :query/key [:tag :post/_taxons]
        :attrs-map attrs-map
        :bindings [{:attr :translatable/fields
                    :binding-sym '?e1
                    :entity-index 0
                    :relation-index 1
                    :relation [:translatable/fields]}]}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :post/status :post.status/draft
       :post/type :post.type/article
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag with :post/type and :post/status nil
      #_#_
      [{:query/name ::db/query
        :query/key :tag
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                             ;; :taxon/posts
                             :post/_taxons])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::db/query
        :query/key [:tag :post/_taxons]
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])
                  (pull ?e1 [:db/id :field/key :field/content])]
           :in [$ ?taxon ?lang]
           :where [[?post :post/taxons ?taxon]
                   [?post :translatable/fields ?e1]
                   [?e1 :field/lang ?lang]]}
         [::bread/data :tag :db/id]
         :en]}
       {:query/name ::i18n/reconstitute
        :query/key [:tag :post/_taxons]
        :attrs-map attrs-map
        :bindings [{:attr :translatable/fields
                    :binding-sym '?e1
                    :entity-index 0
                    :relation-index 1
                    :relation [:translatable/fields]}]}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :post/type nil
       :post/status nil
       :route/params {:lang "en" :slug "some-tag"}}

      ;; {:uri "/en/tag/some-tag"}
      ;; :dispatcher.type/tag with :post/type and :post/status false
      #_#_
      [{:query/name ::db/query
        :query/key :tag
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                             ;; :taxon/posts
                             :post/_taxons])]
           :in [$ ?taxonomy ?slug]
           :where [[?e :taxon/taxonomy ?taxonomy]
                   [?e :taxon/slug ?slug]]}
         :taxon.taxonomy/tag
         "some-tag"]}
       {:query/name ::db/query
        :query/key [:tag :post/_taxons]
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?post [:db/id :translatable/fields])
                  (pull ?e1 [:db/id :field/key :field/content])]
           :in [$ ?taxon ?lang]
           :where [[?post :post/taxons ?taxon]
                   [?post :translatable/fields ?e1]
                   [?e1 :field/lang ?lang]]}
         [::bread/data :tag :db/id]
         :en]}
       {:query/name ::i18n/reconstitute
        :query/key [:tag :post/_taxons]
        :attrs-map attrs-map
        :bindings [{:binding-sym '?e1
                    :attr :translatable/fields
                    :entity-index 0
                    :relation-index 1
                    :relation [:translatable/fields]}]}]
      {:dispatcher/type :dispatcher.type/tag
       :dispatcher/pull [{:post/_taxons [{:translatable/fields [:field/key
                                                                :field/content]}]}]
       :dispatcher/key :tag
       :post/type false ; same as explicit nil.
       :post/status false ; same as explicit nil.
       :route/params {:lang "en" :slug "some-tag"}}

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
