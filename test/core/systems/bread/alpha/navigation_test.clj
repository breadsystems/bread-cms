(ns systems.bread.alpha.navigation-test
  (:require
    [clojure.test :refer [deftest are]]
    [clojure.string :as string]
    [com.rpl.specter :as s]
    [systems.bread.alpha.navigation :as navigation]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded
                                              map->router]]))

(defrecord MockRouter [params]
  bread/Router
  (bread/params [this _] params)
  (bread/match [this _]))

(deftest test-queries-hook
  (are
    [data config req-params]
    (= data (-> (plugins->loaded [(db->plugin ::FAKEDB)
                                  (route/plugin {:router (MockRouter.
                                                           req-params)})
                                  (i18n/plugin {:query-strings? false
                                                :query-lang? false})
                                  (navigation/plugin config)])
                (bread/hook ::bread/dispatch)
                ::bread/queries))

    ;; Support disabling navigation completely.
    [] false {}
    [] nil {}
    [] {} {}

    ;; Basic post menu.
    [{:query/name ::bread/value
      :query/key [:menus :basic-nav]
      :query/description "Basic initial info for this posts menu."
      :query/value {:menu/type ::navigation/posts
                    :post/type :post.type/page}}
     {:query/name ::db/query
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Recursively query for posts of a specific type."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [;; Post menus don't store their own data in the db:
                          ;; instead, they follow the post hierarchy itself.
                          :db/id
                          :post/type
                          :post/status
                          {:translatable/fields [*]}
                          {:post/children ...}])]
         :in [$ ?type [?status ...]]
         :where [[?e :post/type ?type]
                 [?e :post/status ?status]
                 (not-join [?e] [?_ :post/children ?e])]}
       :post.type/page
       #{:post.status/published}]}
     {:query/name ::i18n/fields
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/posts]
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process post menu item data."
      :route/name ::page
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key nil}]
    {:menus
     {:basic-nav
      {:menu/type ::navigation/posts
       :route/name ::page}}}
    {:field/lang "en"}

    ;; Basic post menu; passing colls to :post/status, :field/key;
    ;; passing recursion-limit.
    [{:query/name ::bread/value
      :query/key [:menus :basic-nav]
      :query/description "Basic initial info for this posts menu."
      :query/value {:menu/type ::navigation/posts
                    :post/type :post.type/page}}
     {:query/name ::db/query
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Recursively query for posts of a specific type."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/type
                          :post/status
                          {:translatable/fields [*]}
                          {:post/children 3}])]
         :in [$ ?type [?status ...]]
         :where [[?e :post/type ?type]
                 [?e :post/status ?status]
                 (not-join [?e] [?_ :post/children ?e])]}
       :post.type/page
       #{:post.status/published :post.status/draft}]}
     {:query/name ::i18n/fields
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/posts]
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process post menu item data."
      :route/name ::page
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key #{:a :b}}]
    {:menus
     {:basic-nav
      {:menu/type ::navigation/posts
       :post/status [:post.status/published :post.status/draft]
       :route/name ::page
       :field/key [:a :b]
       :recursion-limit 3}}}
    {:field/lang "en"}

    ;; Basic post menu; single :post/status, :field/key.
    [{:query/name ::bread/value
      :query/key [:menus :basic-nav]
      :query/description "Basic initial info for this posts menu."
      :query/value {:menu/type ::navigation/posts
                    :post/type :post.type/park}}
     {:query/name ::db/query
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Recursively query for posts of a specific type."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [;; Post menus don't store their own data in the db:
                          ;; instead, they follow the post hierarchy itself.
                          :db/id
                          :post/type
                          :post/status
                          {:translatable/fields [*]}
                          {:post/children ...}])]
         :in [$ ?type [?status ...]]
         :where [[?e :post/type ?type]
                 [?e :post/status ?status]
                 (not-join [?e] [?_ :post/children ?e])]}
       :post.type/park
       #{:post.status/draft}]}
     {:query/name ::i18n/fields
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/posts]
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process post menu item data."
      :route/name ::park
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key #{:b}}]
    {:menus
     {:basic-nav
      {:menu/type ::navigation/posts
       :post/type :post.type/park
       :post/status :post.status/draft
       :route/name ::park
       :field/key :b}}}
    {:field/lang "en"}

    ;; Location menu; custom menus-key.
    [{:query/name ::bread/value
      :query/description "Basic initial info for this location menu."
      :query/key [:my/menus :location-nav]
      :query/value {:menu/type ::navigation/location
                    :menu/location ::primary}}
     {:query/name ::db/query
      :query/key [:my/menus :location-nav :menu/items]
      :query/description "Recursively query for menu items."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?i [:db/id
                          :menu.item/order
                          {:menu.item/children ...}
                          {:menu.item/entity
                           [:db/id
                            :post/slug
                            {:translatable/fields [*]}]}
                          {:translatable/fields [*]}])]
         :in [$ ?location]
         :where [[?m :menu/locations ?location]
                 [?m :menu/items ?i]]}
       ::primary]}
     {:query/name ::i18n/fields
      :query/key [:my/menus :location-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:menu.item/entity :translatable/fields]
               [:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/location]
      :query/key [:my/menus :location-nav :menu/items]
      :field/key nil
      :merge-entities? true}]
    {:menus
     {:location-nav
      {:menu/type ::navigation/location
       :menu/location ::primary}}
     :menus-key :my/menus}
    {:field/lang "en"}

    ;; Location menu; recursion-limit, field/keys.
    [{:query/name ::bread/value
      :query/description "Basic initial info for this location menu."
      :query/key [:menus :location-nav]
      :query/value {:menu/type ::navigation/location
                    :menu/location ::primary}}
     {:query/name ::db/query
      :query/key [:menus :location-nav :menu/items]
      :query/description "Recursively query for menu items."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?i [:db/id
                          :menu.item/order
                          {:menu.item/children ...}
                          {:menu.item/entity
                           [:db/id
                            :post/slug
                            {:translatable/fields [*]}]}
                          {:translatable/fields [*]}])]
         :in [$ ?location]
         :where [[?m :menu/locations ?location]
                 [?m :menu/items ?i]]}
       ::primary]}
     {:query/name ::i18n/fields
      :query/key [:menus :location-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:menu.item/entity :translatable/fields]
               [:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/location]
      :query/key [:menus :location-nav :menu/items]
      :field/key #{:a}
      :merge-entities? true}]
    {:menus
     {:location-nav
      {:menu/type ::navigation/location
       :menu/location ::primary
       :field/key :a
       :merge-entities? true}}}
    {:field/lang "en"}

    ;; Basic taxon menu.
    [{:query/name ::bread/value
      :query/description "Basic initial info for this taxon menu."
      :query/key [:menus :taxon-nav]
      :query/value {:menu/type ::navigation/taxon
                    :taxon/taxonomy :taxon.taxonomy/tag
                    :taxon/slug nil}}
     {:query/name ::db/query
      :query/key [:menus :taxon-nav :menu/items]
      :query/description "Recursively query for taxons of a specific taxonomy."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :taxon/taxonomy
                          :taxon/slug
                          {:taxon/children ...}
                          {:translatable/fields [*]}])]
         :in [$ ?taxonomy]
         :where [[?e :taxon/taxonomy ?taxonomy]]}
       :taxon.taxonomy/tag]}
     {:query/name ::i18n/fields
      :query/key [:menus :taxon-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/taxon]
      :query/key [:menus :taxon-nav :menu/items]
      :field/key nil}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :taxon/taxonomy :taxon.taxonomy/tag}}}
    {:field/lang "en"}

    ;; Basic taxon menu; recursion-limit, :field/keys.
    [{:query/name ::bread/value
      :query/description "Basic initial info for this taxon menu."
      :query/key [:menus :taxon-nav]
      :query/value {:menu/type ::navigation/taxon
                    :taxon/taxonomy :taxon.taxonomy/category
                    :taxon/slug nil}}
     {:query/name ::db/query
      :query/key [:menus :taxon-nav :menu/items]
      :query/description "Recursively query for taxons of a specific taxonomy."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :taxon/taxonomy
                          :taxon/slug
                          {:taxon/children 2}
                          {:translatable/fields [*]}])]
         :in [$ ?taxonomy]
         :where [[?e :taxon/taxonomy ?taxonomy]]}
       :taxon.taxonomy/category]}
     {:query/name ::i18n/fields
      :query/key [:menus :taxon-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/taxon]
      :query/key [:menus :taxon-nav :menu/items]
      :field/key #{:x :y :z}}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :field/key [:x :y :z]
       :recursion-limit 2}}}
    {:field/lang "en"}

    ;; Basic taxon menu; recursion-limit, :field/keys, :taxon/slug.
    [{:query/name ::bread/value
      :query/description "Basic initial info for this taxon menu."
      :query/key [:menus :taxon-nav]
      :query/value {:menu/type ::navigation/taxon
                    :taxon/taxonomy :taxon.taxonomy/category
                    :taxon/slug "my-lovely-cat"}}
     {:query/name ::db/query
      :query/key [:menus :taxon-nav :menu/items]
      :query/description "Recursively query for taxons of a specific taxonomy."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :taxon/taxonomy
                          :taxon/slug
                          {:taxon/children 2}
                          {:translatable/fields [*]}])]
         :in [$ ?taxonomy ?slug]
         :where [[?e :taxon/taxonomy ?taxonomy]
                 [?e :taxon/slug ?slug]]}
       :taxon.taxonomy/category
       "my-lovely-cat"]}
     {:query/name ::i18n/fields
      :query/key [:menus :taxon-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :spaths [[:translatable/fields]]}
     {:query/name [::navigation/items ::navigation/taxon]
      :query/key [:menus :taxon-nav :menu/items]
      :field/key #{:x :y :z}}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :taxon/taxonomy :taxon.taxonomy/category
       :taxon/slug "my-lovely-cat"
       :field/key [:x :y :z]
       :recursion-limit 2}}}
    {:field/lang "en"}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
