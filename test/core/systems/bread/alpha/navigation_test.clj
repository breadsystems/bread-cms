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
  (bread/route-params [this _] params)
  (bread/match [this _])
  (bread/route-spec [this match]
    [:field/lang :thing/slug*])
  (bread/path [this route-name params]
    (let [route (get {::page [:field/lang :thing/slug*]} route-name)]
      (str "/" (string/join "/" (map #(some-> % params name) route)))))
  (bread/routes [this] []))

(deftest test-expansions-hook
  (are
    [data config req-params]
    (= data (-> (plugins->loaded [(db->plugin ::FAKEDB)
                                  (route/plugin {:router (MockRouter.
                                                           req-params)})
                                  (i18n/plugin {:query-strings? false
                                                :query-lang? false})
                                  (navigation/plugin config)])
                (bread/hook ::bread/dispatch)
                ::bread/expansions))

    ;; Support disabling navigation completely.
    [] false {}
    [] nil {}
    [] {} {}

    ;; Basic post menu.
    [{:expansion/name ::bread/value
      :expansion/key [:menus :basic-nav]
      :expansion/description "Basic initial info for this posts menu."
      :expansion/value {:menu/type ::navigation/posts
                    :post/type :post.type/page}}
     {:expansion/name ::db/query
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Recursively query for posts of a specific type."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?e [;; Post menus don't store their own data in the db:
                          ;; instead, they follow the post hierarchy itself.
                          :db/id
                          :post/type
                          :post/status
                          {:thing/fields [*]}
                          {:thing/_children [:thing/slug {:thing/_children ...}]}
                          {:thing/children ...}])]
         :in [$ ?type [?status ...]]
         :where [[?e :post/type ?type]
                 [?e :post/status ?status]
                 (not-join [?e] [?_ :thing/children ?e])]}
       :post.type/page
       #{:post.status/published}]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Process post menu item data."
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
    [{:expansion/name ::bread/value
      :expansion/key [:menus :basic-nav]
      :expansion/description "Basic initial info for this posts menu."
      :expansion/value {:menu/type ::navigation/posts
                    :post/type :post.type/page}}
     {:expansion/name ::db/query
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Recursively query for posts of a specific type."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?e [:db/id
                          :post/type
                          :post/status
                          {:thing/fields [*]}
                          {:thing/_children [:thing/slug {:thing/_children ...}]}
                          {:thing/children 3}])]
         :in [$ ?type [?status ...]]
         :where [[?e :post/type ?type]
                 [?e :post/status ?status]
                 (not-join [?e] [?_ :thing/children ?e])]}
       :post.type/page
       #{:post.status/published :post.status/draft}]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Process post menu item data."
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
    [{:expansion/name ::bread/value
      :expansion/key [:menus :basic-nav]
      :expansion/description "Basic initial info for this posts menu."
      :expansion/value {:menu/type ::navigation/posts
                    :post/type :post.type/park}}
     {:expansion/name ::db/query
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Recursively query for posts of a specific type."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?e [;; Post menus don't store their own data in the db:
                          ;; instead, they follow the post hierarchy itself.
                          :db/id
                          :post/type
                          :post/status
                          {:thing/fields [*]}
                          {:thing/_children [:thing/slug {:thing/_children ...}]}
                          {:thing/children ...}])]
         :in [$ ?type [?status ...]]
         :where [[?e :post/type ?type]
                 [?e :post/status ?status]
                 (not-join [?e] [?_ :thing/children ?e])]}
       :post.type/park
       #{:post.status/draft}]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :basic-nav :menu/items]
      :expansion/description "Process post menu item data."
      :route/name ::park
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})
      :field/key #{:b}
      :sort-by [:thing/fields :title]}]
    {:menus
     {:basic-nav
      {:menu/type ::navigation/posts
       :post/type :post.type/park
       :post/status :post.status/draft
       :route/name ::park
       :field/key :b
       :sort-by [:thing/fields :title]}}}
    {:field/lang "en"}

    ;; Global menu; custom menus-key.
    [{:expansion/name ::bread/value
      :expansion/description "Basic initial info for this global menu."
      :expansion/key [:my/menus :global-nav]
      :expansion/value {:menu/type ::navigation/global
                    :menu/key :global-nav}}
     {:expansion/name ::db/query
      :expansion/key [:my/menus :global-nav :menu/items]
      :expansion/description "Recursively query for menu items."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?i [:db/id
                          :thing/order
                          {:thing/children ...}
                          {:menu.item/entity
                           [:db/id
                            :thing/slug
                            {:thing/fields [*]}
                            {:thing/_children [:thing/slug
                                              {:thing/_children ...}]}]}
                          {:thing/fields [*]}])]
         :in [$ ?key]
         :where [[?m :menu/key ?key]
                 [?m :menu/items ?i]]}
       :global-nav]}
     {:expansion/name ::i18n/fields
      :expansion/key [:my/menus :global-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :menu.item/entity :thing/fields]
               [s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:my/menus :global-nav :menu/items]
      :field/key nil
      :merge-entities? true
      :sort-by [:thing/order]
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
    [{:expansion/name ::bread/value
      :expansion/description "Basic initial info for this location menu."
      :expansion/key [:my/menus :location-nav]
      :expansion/value {:menu/type ::navigation/location
                    :menu/location ::primary}}
     {:expansion/name ::db/query
      :expansion/key [:my/menus :location-nav :menu/items]
      :expansion/description "Recursively query for menu items."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?i [:db/id
                          :thing/order
                          {:thing/children ...}
                          {:menu.item/entity
                           [:db/id
                            :thing/slug
                            {:thing/fields [*]}
                            {:thing/_children [:thing/slug
                                              {:thing/_children ...}]}]}
                          {:thing/fields [*]}])]
         :in [$ ?location]
         :where [[?m :menu/locations ?location]
                 [?m :menu/items ?i]]}
       ::primary]}
     {:expansion/name ::i18n/fields
      :expansion/key [:my/menus :location-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :menu.item/entity :thing/fields]
               [s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:my/menus :location-nav :menu/items]
      :field/key nil
      :merge-entities? true
      :sort-by [:thing/order]
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
    [{:expansion/name ::bread/value
      :expansion/description "Basic initial info for this location menu."
      :expansion/key [:menus :location-nav]
      :expansion/value {:menu/type ::navigation/location
                    :menu/location ::primary}}
     {:expansion/name ::db/query
      :expansion/key [:menus :location-nav :menu/items]
      :expansion/description "Recursively query for menu items."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?i [:db/id
                          :thing/order
                          {:thing/children ...}
                          {:menu.item/entity
                           [:db/id
                            :thing/slug
                            {:thing/fields [*]}
                            {:thing/_children [:thing/slug
                                              {:thing/_children ...}]}]}
                          {:thing/fields [*]}])]
         :in [$ ?location]
         :where [[?m :menu/locations ?location]
                 [?m :menu/items ?i]]}
       ::primary]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :location-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :menu.item/entity :thing/fields]
               [s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :location-nav :menu/items]
      :field/key #{:a}
      :merge-entities? true
      :sort-by [:thing/order]
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
    [{:expansion/name ::bread/value
      :expansion/description "Basic initial info for this taxon menu."
      :expansion/key [:menus :taxon-nav]
      :expansion/value {:menu/type ::navigation/taxon
                    :taxon/taxonomy :taxon.taxonomy/tag
                    :thing/slug nil}}
     {:expansion/name ::db/query
      :expansion/key [:menus :taxon-nav :menu/items]
      :expansion/description "Recursively query for taxons of a specific taxonomy."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?e [:db/id
                          :taxon/taxonomy
                          :thing/slug
                          {:thing/_children [:thing/slug
                                             {:thing/_children ...}]}
                          {:thing/children ...}
                          {:thing/fields [*]}])]
         :in [$ ?taxonomy]
         :where [[?e :taxon/taxonomy ?taxonomy]]}
       :taxon.taxonomy/tag]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :taxon-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :taxon-nav :menu/items]
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
    [{:expansion/name ::bread/value
      :expansion/description "Basic initial info for this taxon menu."
      :expansion/key [:menus :taxon-nav]
      :expansion/value {:menu/type ::navigation/taxon
                    :taxon/taxonomy :taxon.taxonomy/category
                    :thing/slug nil}}
     {:expansion/name ::db/query
      :expansion/key [:menus :taxon-nav :menu/items]
      :expansion/description "Recursively query for taxons of a specific taxonomy."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?e [:db/id
                          :taxon/taxonomy
                          :thing/slug
                          {:thing/_children [:thing/slug
                                             {:thing/_children ...}]}
                          {:thing/children 2}
                          {:thing/fields [*]}])]
         :in [$ ?taxonomy]
         :where [[?e :taxon/taxonomy ?taxonomy]]}
       :taxon.taxonomy/category]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :taxon-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :taxon-nav :menu/items]
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

    ;; Basic taxon menu; recursion-limit, :field/keys, :thing/slug; :sort-by.
    [{:expansion/name ::bread/value
      :expansion/description "Basic initial info for this taxon menu."
      :expansion/key [:menus :taxon-nav]
      :expansion/value {:menu/type ::navigation/taxon
                    :taxon/taxonomy :taxon.taxonomy/category
                    :thing/slug "my-lovely-cat"}}
     {:expansion/name ::db/query
      :expansion/key [:menus :taxon-nav :menu/items]
      :expansion/description "Recursively query for taxons of a specific taxonomy."
      :expansion/db ::FAKEDB
      :expansion/args
      ['{:find [(pull ?e [:db/id
                          :taxon/taxonomy
                          :thing/slug
                          {:thing/_children [:thing/slug
                                             {:thing/_children ...}]}
                          {:thing/children 2}
                          {:thing/fields [*]}])]
         :in [$ ?taxonomy ?slug]
         :where [[?e :taxon/taxonomy ?taxonomy]
                 [?e :thing/slug ?slug]]}
       :taxon.taxonomy/category
       "my-lovely-cat"]}
     {:expansion/name ::i18n/fields
      :expansion/key [:menus :taxon-nav :menu/items]
      :expansion/description "Process translatable fields."
      :field/lang :en
      :compact? true
      :format? true
      :recur-attrs #{:thing/children}
      :spaths [[s/ALL s/ALL :thing/fields]]}
     {:expansion/name ::navigation/items
      :expansion/key [:menus :taxon-nav :menu/items]
      :field/key #{:x :y :z}
      :sort-by [:thing/fields :title]
      :route/name ::my-route
      :route/params {:field/lang "en"}
      :router (MockRouter. {:field/lang "en"})}]
    {:menus
     {:taxon-nav
      {:menu/type ::navigation/taxon
       :route/name ::my-route
       :taxon/taxonomy :taxon.taxonomy/category
       :thing/slug "my-lovely-cat"
       :field/key [:x :y :z]
       :recursion-limit 2
       :sort-by [:thing/fields :title]}}}
    {:field/lang "en"}

    ;;
    ))

(deftest test-items-location-hook
  (are
    [items query data]
    (= items (bread/expand query data))

    nil
    {:expansion/name ::navigation/items
     :expansion/key [:menus :my-nav :menu/items]}
    {:menus {}}

    nil
    {:expansion/name ::navigation/items
     :expansion/key [:menus :my-nav :menu/items]}
    {:menus {:my-nav {}}}

    nil
    {:expansion/name ::navigation/items
     :expansion/key [:menus :my-nav :menu/items]}
    {:menus {:my-nav {:menu/items nil}}}

    []
    {:expansion/name ::navigation/items
     :expansion/key [:menus :my-nav :menu/items]}
    {:menus {:my-nav {:menu/items []}}}

    ;; Basic menu items; no :field/key filtering.
    [{:uri "/abc"
      :thing/fields {:uri "/abc" :my/field "My Field"}
      :thing/children []}
     {:uri "/xyz"
      :thing/fields {:uri "/xyz" :other/field "Other"}
      :thing/children []}]
    {:expansion/name ::navigation/items
     :expansion/key [:menus :#nofilter :menu/items]
     :router (MockRouter. {})
     :field/key nil
     :sort-by [:thing/order]}
    {:menus {:#nofilter {:menu/items [[{:thing/fields
                                        {:uri "/abc"
                                         :my/field "My Field"}}]
                                      [{:thing/fields
                                        {:uri "/xyz"
                                         :other/field "Other"}}]]}}}

    ;; Basic menu items with related entities.
    [{:uri "/en/xyz"
      :thing/fields {}
      :thing/children []}
     {:uri "/en/abc"
      :thing/fields {:my/field "Override"
                     :other/field "Another override"}
      :thing/children []}
     {:uri "/en/parent/child"
      :thing/fields {:my/field "Post field"
                     :other/field "Another post field"}
      :thing/children []}]
    {:expansion/name ::navigation/items
     :expansion/key [:menus :my-nav :menu/items]
     :merge-entities? true
     :field/key #{:my/field :other/field}
     :sort-by [:thing/order]
     :router (MockRouter. {})
     :route/name ::page
     :route/params {:field/lang :en}}
    {:menus {:my-nav
             {:menu/items
              [[{:thing/order 2
                 :menu.item/entity
                 {:thing/slug "abc"
                  :thing/fields {:extra "This gets filtered out..."
                                 :my/field "My Field"
                                 :other/field "Other"}}
                 :thing/fields {:more "...and so does this"
                                :my/field "Override"
                                :other/field "Another override"}}]
               [{:thing/order 3
                 :menu.item/entity
                 {:thing/slug "child"
                  :thing/fields {:extra "This gets filtered out..."
                                 :my/field "Post field"
                                 :other/field "Another post field"}
                  ;; Post ancestry.
                  :thing/_children
                  [{:thing/slug "parent"}]}
                 :thing/fields {:more "...and so does this"}}]
               [{:thing/order 1
                 ;; no post
                 :thing/fields {:uri "/en/xyz"}}]]}}}

    ;; :merge-entities? false; recursive.
    [{:uri "/en/xyz"
      :thing/fields {}
      :thing/children
      [{:uri "/en/xyz/123"
        :thing/fields {:my/field "Daughter"}
        :thing/children []}
       {:uri "/en/xyz/456"
        :thing/fields {:my/field "Child"}
        :thing/children [{:uri "/en/xyz/456/789"
                          :thing/fields {:my/field "Grandchild"}
                          :thing/children []}]}]}
     {:uri "/en/abc"
      :thing/fields {:my/field "Override"
                     :other/field "Another override"}
      :thing/children []}
     {:uri "/en/parent/child"
      :thing/fields {}
      :thing/children []}]
    {:expansion/name ::navigation/items
     :expansion/key [:menus :my-nav :menu/items]
     :merge-entities? false
     :field/key #{:my/field :other/field}
     :sort-by [:thing/order]
     :router (MockRouter. {})
     :route/name ::page
     :route/params {:field/lang :en}}
    {:menus {:my-nav
             {:menu/items
              [[{:thing/order 2
                 :menu.item/entity {:thing/slug "abc"
                                    :thing/fields
                                    {:extra "This gets filtered out..."
                                     :my/field "My Field"
                                     :other/field "Other"}}
                 :thing/fields {:more "...and so does this"
                                :my/field "Override"
                                :other/field "Another override"}}]
               [{:thing/order 3
                 :menu.item/entity {:thing/slug "child"
                                    :thing/fields
                                    {:extra "This gets filtered out..."
                                     :my/field "Post field"
                                     :other/field "Another post field"}
                                    ;; Post ancestry.
                                    :thing/_children [{:thing/slug "parent"}]}
                 :thing/fields {:more "...and so does this"}}]
               [{:thing/order 1
                 ;; No entity.
                 :thing/fields {:uri "/en/xyz"}
                 :thing/children
                 [{:thing/order 1
                   :thing/fields {:uri "/en/xyz/456"
                                  :my/field "Child"}
                   :thing/children
                   [{:thing/order 0
                     :thing/fields {:uri "/en/xyz/456/789"
                                    :my/field "Grandchild"}}]}
                  {:thing/order 0
                   :thing/fields {:uri "/en/xyz/123"
                                  :my/field "Daughter"}}]}]]}}}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
