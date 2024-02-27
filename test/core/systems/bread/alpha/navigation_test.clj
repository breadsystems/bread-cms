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
  (bread/match [this _])
  (bread/path [this route-name params]
    (let [route (get {::page [:field/lang :slugs]} route-name)]
      (str "/" (string/join "/" (map #(some-> % params name) route)))))
  bread/RoutesCollection
  (bread/routes [this] []))

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
                          {:post/_children [:post/slug {:post/_children ...}]}
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
      :recur-attrs #{:post/children}
      :spaths [[s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process post menu item data."
      :route/name ::page
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key nil
      :sort-by nil}]
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
                          {:post/_children [:post/slug {:post/_children ...}]}
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
      :recur-attrs #{:post/children}
      :spaths [[s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process post menu item data."
      :route/name ::page
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key #{:a :b}
      :sort-by nil}]
    {:menus
     {:basic-nav
      {:menu/type ::navigation/posts
       :post/status [:post.status/published :post.status/draft]
       :route/name ::page
       :field/key [:a :b]
       :recursion-limit 3}}}
    {:field/lang "en"}

    ;; Basic post menu; single :post/status, :field/key; :sort-by.
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
                          {:post/_children [:post/slug {:post/_children ...}]}
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
      :recur-attrs #{:post/children}
      :spaths [[s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :basic-nav :menu/items]
      :query/description "Process post menu item data."
      :route/name ::park
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key #{:b}
      :sort-by [:translatable/fields :title]}]
    {:menus
     {:basic-nav
      {:menu/type ::navigation/posts
       :post/type :post.type/park
       :post/status :post.status/draft
       :route/name ::park
       :field/key :b
       :sort-by [:translatable/fields :title]}}}
    {:field/lang "en"}

    ;; Global menu; custom menus-key.
    [{:query/name ::bread/value
      :query/description "Basic initial info for this global menu."
      :query/key [:my/menus :global-nav]
      :query/value {:menu/type ::navigation/global
                    :menu/key :global-nav}}
     {:query/name ::db/query
      :query/key [:my/menus :global-nav :menu/items]
      :query/description "Recursively query for menu items."
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?i [:db/id
                          :menu.item/order
                          {:menu.item/children ...}
                          {:menu.item/entity
                           [:db/id
                            :post/slug
                            {:translatable/fields [*]}
                            {:post/_children [:post/slug
                                              {:post/_children ...}]}]}
                          {:translatable/fields [*]}])]
         :in [$ ?key]
         :where [[?m :menu/key ?key]
                 [?m :menu/items ?i]]}
       :global-nav]}
     {:query/name ::i18n/fields
      :query/key [:my/menus :global-nav :menu/items]
      :query/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:menu.item/children}
      :spaths [[s/ALL s/ALL :menu.item/entity :translatable/fields]
               [s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:my/menus :global-nav :menu/items]
      :field/key nil
      :merge-entities? true
      :sort-by [:menu.item/order]
      :route/name ::my-route
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:global-nav
      {:menu/type ::navigation/global
       :menu/key ::main
       :route/name ::my-route}}
     :menus-key :my/menus}
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
                            {:translatable/fields [*]}
                            {:post/_children [:post/slug
                                              {:post/_children ...}]}]}
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
      :recur-attrs #{:menu.item/children}
      :spaths [[s/ALL s/ALL :menu.item/entity :translatable/fields]
               [s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:my/menus :location-nav :menu/items]
      :field/key nil
      :merge-entities? true
      :sort-by [:menu.item/order]
      :route/name ::my-route
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:location-nav
      {:menu/type ::navigation/location
       :menu/location ::primary
       :route/name ::my-route}}
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
                            {:translatable/fields [*]}
                            {:post/_children [:post/slug
                                              {:post/_children ...}]}]}
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
      :recur-attrs #{:menu.item/children}
      :spaths [[s/ALL s/ALL :menu.item/entity :translatable/fields]
               [s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :location-nav :menu/items]
      :field/key #{:a}
      :merge-entities? true
      :sort-by [:menu.item/order]
      :route/name ::my-route
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:location-nav
      {:menu/type ::navigation/location
       :menu/location ::primary
       :route/name ::my-route
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
                          {:taxon/_children [:taxon/slug
                                             {:taxon/_children ...}]}
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
      :recur-attrs #{:taxon/children}
      :spaths [[s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :taxon-nav :menu/items]
      :field/key nil
      :sort-by nil
      :route/name ::my-route
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :route/name ::my-route
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
                          {:taxon/_children [:taxon/slug
                                             {:taxon/_children ...}]}
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
      :recur-attrs #{:taxon/children}
      :spaths [[s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :taxon-nav :menu/items]
      :field/key #{:x :y :z}
      :sort-by nil
      :route/name ::park
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :route/name ::park
       :taxon/taxonomy :taxon.taxonomy/category
       :field/key [:x :y :z]
       :recursion-limit 2}}}
    {:field/lang "en"}

    ;; Basic taxon menu; recursion-limit, :field/keys, :taxon/slug; :sort-by.
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
                          {:taxon/_children [:taxon/slug
                                             {:taxon/_children ...}]}
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
      :recur-attrs #{:taxon/children}
      :spaths [[s/ALL s/ALL :translatable/fields]]}
     {:query/name ::navigation/items
      :query/key [:menus :taxon-nav :menu/items]
      :field/key #{:x :y :z}
      :sort-by [:translatable/fields :title]
      :route/name ::my-route
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :route/name ::my-route
       :taxon/taxonomy :taxon.taxonomy/category
       :taxon/slug "my-lovely-cat"
       :field/key [:x :y :z]
       :recursion-limit 2
       :sort-by [:translatable/fields :title]}}}
    {:field/lang "en"}

    ;;
    ))

(deftest test-items-location-hook
  (are
    [items query data]
    (= items (bread/query query data))

    nil
    {:query/name ::navigation/items
     :query/key [:menus :my-nav :menu/items]}
    {:menus {}}

    nil
    {:query/name ::navigation/items
     :query/key [:menus :my-nav :menu/items]}
    {:menus {:my-nav {}}}

    nil
    {:query/name ::navigation/items
     :query/key [:menus :my-nav :menu/items]}
    {:menus {:my-nav {:menu/items nil}}}

    []
    {:query/name ::navigation/items
     :query/key [:menus :my-nav :menu/items]}
    {:menus {:my-nav {:menu/items []}}}

    ;; Basic menu items; no :field/key filtering.
    [{:uri "/abc"
      :translatable/fields {:uri "/abc" :my/field "My Field"}
      :children []}
     {:uri "/xyz"
      :translatable/fields {:uri "/xyz" :other/field "Other"}
      :children []}]
    {:query/name ::navigation/items
     :query/key [:menus :#nofilter :menu/items]
     :field/key nil
     :sort-by [:menu.item/order]}
    {:menus {:#nofilter {:menu/items [[{:translatable/fields
                                        {:uri "/abc"
                                         :my/field "My Field"}}]
                                      [{:translatable/fields
                                        {:uri "/xyz"
                                         :other/field "Other"}}]]}}}

    ;; Basic menu items with related entities.
    [{:uri "/en/xyz"
      :translatable/fields {}
      :children []}
     {:uri "/en/abc"
      :translatable/fields {:my/field "Override"
                            :other/field "Another override"}
      :children []}
     {:uri "/en/parent/child"
      :translatable/fields {:my/field "Post field"
                            :other/field "Another post field"}
      :children []}]
    {:query/name ::navigation/items
     :query/key [:menus :my-nav :menu/items]
     :merge-entities? true
     :field/key #{:my/field :other/field}
     :sort-by [:menu.item/order]
     :router (MockRouter. {})
     :route/name ::page
     :route/params {:field/lang :en}}
    {:menus {:my-nav
             {:menu/items
              [[{:menu.item/order 2
                 :menu.item/entity
                 {:post/slug "abc"
                  :translatable/fields {:extra "This gets filtered out..."
                                        :my/field "My Field"
                                        :other/field "Other"}}
                 :translatable/fields {:more "...and so does this"
                                       :my/field "Override"
                                       :other/field "Another override"}}]
               [{:menu.item/order 3
                 :menu.item/entity
                 {:post/slug "child"
                  :translatable/fields {:extra "This gets filtered out..."
                                        :my/field "Post field"
                                        :other/field "Another post field"}
                  ;; Post ancestry.
                  :post/_children
                  [{:post/slug "parent"}]}
                 :translatable/fields {:more "...and so does this"}}]
               [{:menu.item/order 1
                 ;; no post
                 :translatable/fields {:uri "/en/xyz"}}]]}}}

    ;; :merge-entities? false; recursive.
    [{:uri "/en/xyz"
      :translatable/fields {}
      :children
      [{:uri "/en/xyz/123"
        :translatable/fields {:my/field "Daughter"}
        :children []}
       {:uri "/en/xyz/456"
        :translatable/fields {:my/field "Child"}
        :children [{:uri "/en/xyz/456/789"
                    :translatable/fields {:my/field "Grandchild"}
                    :children []}]}]}
     {:uri "/en/abc"
      :translatable/fields {:my/field "Override"
                            :other/field "Another override"}
      :children []}
     {:uri "/en/parent/child"
      :translatable/fields {}
      :children []}]
    {:query/name ::navigation/items
     :query/key [:menus :my-nav :menu/items]
     :merge-entities? false
     :field/key #{:my/field :other/field}
     :sort-by [:menu.item/order]
     :router (MockRouter. {})
     :route/name ::page
     :route/params {:field/lang :en}}
    {:menus {:my-nav
             {:menu/items
              [[{:menu.item/order 2
                 :menu.item/entity {:post/slug "abc"
                                    :translatable/fields
                                    {:extra "This gets filtered out..."
                                     :my/field "My Field"
                                     :other/field "Other"}}
                 :translatable/fields {:more "...and so does this"
                                       :my/field "Override"
                                       :other/field "Another override"}}]
               [{:menu.item/order 3
                 :menu.item/entity {:post/slug "child"
                                    :translatable/fields
                                    {:extra "This gets filtered out..."
                                     :my/field "Post field"
                                     :other/field "Another post field"}
                                    ;; Post ancestry.
                                    :post/_children [{:post/slug "parent"}]}
                 :translatable/fields {:more "...and so does this"}}]
               [{:menu.item/order 1
                 ;; No entity.
                 :translatable/fields {:uri "/en/xyz"}
                 :menu.item/children
                 [{:menu.item/order 1
                   :translatable/fields {:uri "/en/xyz/456"
                                         :my/field "Child"}
                   :menu.item/children
                   [{:menu.item/order 0
                     :translatable/fields {:uri "/en/xyz/456/789"
                                           :my/field "Grandchild"}}]}
                  {:menu.item/order 0
                   :translatable/fields {:uri "/en/xyz/123"
                                         :my/field "Daughter"}}]}]]}}}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
