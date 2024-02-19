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

    ;;
    ))

(deftest ^:kaocha/skip test-merge-post-menu-items
  (are
    [menus menu-data]
    (= menus
       (-> (bread/query
             {:query/name ::navigation/merge-post-menu-items
              :query/key [:main-nav :items]
              :route/name :the-route-name
              :router
              (let [routes {:the-route-name [:lang :slugs]}]
                (reify bread/Router
                  (bread/path [_ nm params]
                    ;; Verify that the merge-post-menu-items
                    ;; query is passing the right route name to
                    ;; the router to generate the URL. Example:
                    ;; (bread/path r :the-route-name {:lang :en :slugs "abc"})
                    ;; -> "/en/abc"
                    (str "/" (string/join
                               "/"
                               (filter
                                 (complement empty?)
                                 (map (comp name params) (nm routes))))))))
              :lang :en}
             menu-data)))

    ;; simple case, default title field
    [{:title "eleven"
      :url "/en/parent-page"
      :entity {:db/id 1
               :post/slug "parent-page"
               :translatable/fields {:title "eleven"}}
      :children []}
     {:title "twenty-two"
      :url "/en"
      :entity {:db/id 2
               :post/slug ""
               :translatable/fields {:title "twenty-two"}}
      :children []}]
    {:menus
     {:main-nav
      {:menu/type ::navigation/posts
       :post/type :post.type/page
       :route/name :bread.route/page
       :items [[{:db/id 1
                 :post/slug "parent-page"
                 :translatable/fields [{:db/id 11}]}]
               [{:db/id 2
                 :post/slug ""
                 :translatable/fields [{:db/id 22}]}]]}}
     :navigation/i18n
     {:main-nav
      [[{:db/id 11 :field/key :title :field/content "\"eleven\""}]
       [{:db/id 22 :field/key :title :field/content "\"twenty-two\""}]]}}

    ;; non-recursive case
    [{:title "eleven"
      :url "/en/parent-page"
      :entity {:db/id 1
               :post/slug "parent-page"
               :translatable/fields {:one "eleven"
                                     :two "twelve"}}
      :children []}
     {;; NOTE: :one is missing from this post's fields.
      :title nil
      :url "/en"
      :entity {:db/id 2
               :post/slug ""
               :translatable/fields {;; Handle missing fields gracefully.
                                     :two "twenty-two"}}
      :children []}]
    {:menus
     {:main-nav
      {:menu/type ::navigation/posts
       :post/type :post.type/page
       :route/name :bread.route/page
       :title-field :one
       :items [[{:db/id 1
                 :post/slug "parent-page"
                 :translatable/fields [{:db/id 11} {:db/id 12} {:db/id 13}]}]
               [{:db/id 2
                 :post/slug ""
                 :translatable/fields [{:db/id 21} {:db/id 22} {:db/id 23}]}]]}}
     :navigation/i18n
     {:main-nav
      [[{:db/id 11 :field/key :one :field/content "\"eleven\""}]
       [{:db/id 12 :field/key :two :field/content "\"twelve\""}]
       [{:db/id 22 :field/key :two :field/content "\"twenty-two\""}]]}}

    ;; recursive case (with children)
    [{:title "eleven"
      :url "/en/parent-page"
      :entity {:db/id 1
               :post/slug "parent-page"
               :translatable/fields {:one "eleven"
                                     :two "twelve"}}
      :children [{:title "thirty-one"
                  :url "/en/parent-page/child-page"
                  :entity {:db/id 3
                           :post/slug "child-page"
                           :translatable/fields {:one "thirty-one"
                                                 :two "thirty-two"}}
                  :children []}
                 {:title "forty-one"
                  :url "/en/parent-page/another-kid"
                  :entity {:db/id 4
                           :post/slug "another-kid"
                           :translatable/fields {:one "forty-one"
                                                 :two "forty-two"}}
                  :children [{:title "fifty-one"
                              :url "/en/parent-page/another-kid/grandchild"
                              :entity {:db/id 5
                                       :post/slug "grandchild"
                                       :translatable/fields {:one "fifty-one"
                                                     :two "fifty-two"}}
                              :children []}
                             {:title "sixty-one"
                              :url "/en/parent-page/another-kid/another-grandchild"
                              :entity {:db/id 6
                                       :post/slug "another-grandchild"
                                       :translatable/fields {:one "sixty-one"
                                                     :two "sixty-two"}}
                              :children []}]}]}
     {:title "twenty-one"
      :url "/en"
      :entity {:db/id 2
               :post/slug ""
               :translatable/fields {:one "twenty-one"
                                     :two "twenty-two"}}
      :children []}]
    {:menus
     {:main-nav
      {:menu/type ::navigation/posts
       :post/type :post.type/page
       :route/name :bread.route/page
       :title-field :one
       :items [[{:db/id 1
                 :post/slug "parent-page"
                 :translatable/fields [{:db/id 11} {:db/id 12} {:db/id 13}]
                 :post/children
                 [{:db/id 3
                   :post/slug "child-page"
                   :translatable/fields [{:db/id 31} {:db/id 32} {:db/id 33}]}
                  {:db/id 4
                   :post/slug "another-kid"
                   :translatable/fields [{:db/id 41} {:db/id 42} {:db/id 43}]
                   :post/children
                   [{:db/id 5
                     :post/slug "grandchild"
                     :translatable/fields [{:db/id 51} {:db/id 52} {:db/id 53}]}
                    {:db/id 6
                     :post/slug "another-grandchild"
                     :translatable/fields [{:db/id 61} {:db/id 62} {:db/id 63}]}]}]}]
               [{:db/id 2
                 :post/slug ""
                 :translatable/fields [{:db/id 21} {:db/id 22} {:db/id 23}]}]]}}
     :navigation/i18n
     {:main-nav
      [[{:db/id 11 :field/key :one :field/content "\"eleven\""}]
       [{:db/id 12 :field/key :two :field/content "\"twelve\""}]
       [{:db/id 21 :field/key :one :field/content "\"twenty-one\""}]
       [{:db/id 22 :field/key :two :field/content "\"twenty-two\""}]
       [{:db/id 31 :field/key :one :field/content "\"thirty-one\""}]
       [{:db/id 32 :field/key :two :field/content "\"thirty-two\""}]
       [{:db/id 41 :field/key :one :field/content "\"forty-one\""}]
       [{:db/id 42 :field/key :two :field/content "\"forty-two\""}]
       [{:db/id 51 :field/key :one :field/content "\"fifty-one\""}]
       [{:db/id 52 :field/key :two :field/content "\"fifty-two\""}]
       [{:db/id 61 :field/key :one :field/content "\"sixty-one\""}]
       [{:db/id 62 :field/key :two :field/content "\"sixty-two\""}]]}}))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
