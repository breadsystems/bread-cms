;; Test navigation API at the datastore -> ::bread/data level.
(ns systems.bread.alpha.navigation-test
  (:require
    [clojure.test :refer [deftest are]]
    [clojure.string :as string]
    [systems.bread.alpha.navigation :as navigation]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded
                                              map->router]]))

(deftest test-post-menu-queries
  (let [db ::FAKEDB
        router (reify bread/Router
                 (bread/match [_ _])
                 (bread/params [_ _]))]
    (are
      [data navigation-config]
      (= data (-> (plugins->loaded [(db->plugin db)
                                    (i18n/plugin {:query-strings? false
                                                  :query-lang? false})
                                    (navigation/plugin navigation-config)
                                    (route/plugin {:router router})])
                  ;; Navigation plugin declares the only ::bread/dispatch
                  ;; handlers we care about, we don't need a specific
                  ;; dispatcher here. Dispatchers are normally responsible
                  ;; for fetching things like posts based on the current
                  ;; route. Menus, on the other hand, generally don't change
                  ;; much between routes.
                  (bread/hook ::bread/dispatch)
                  ::bread/queries))

      ;; Support disabling navigation completely.
      [] false
      [] nil
      [] {}

      ;; Basic post menu.
      [{:query/name ::bread/value
        :query/key [:basic-nav]
        :query/value {:menu/type ::navigation/posts
                      :post/type :post.type/page}}
       {:query/name ::db/query
        :query/key [:basic-nav  :items]
        :query/db db
        :query/args
        ['{:find [(pull ?e [;; Post menus don't store their own data in the db:
                            ;; instead, they follow the post hierarchy itself.
                            :db/id * {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/page
         #{:post.status/published}]}
       {:query/name ::db/query
        :query/key [:navigation/i18n :basic-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?type [?status ...] [?field-key ...] ?lang]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :translatable/fields ?f]
                   [?f :field/key ?field-key]
                   [?f :field/lang ?lang]]}
         :post.type/page
         #{:post.status/published}
         #{:title}
         :en]}
       {:query/name ::navigation/merge-post-menu-items
        :query/key [:basic-nav :items]
        :route/name :bread.route/page
        :router router
        :lang :en}]
      {:menus
       [{:menu/key :basic-nav
         :menu/type ::navigation/posts
         :post/type :post.type/page
         :route/name :bread.route/page}]}

      ;; Support custom post status, post type, and field keys.
      [{:query/name ::bread/value
        :query/key [:articles-menu]
        :query/value {:menu/type ::navigation/posts
                      :post/type :post.type/article}}
       {:query/name ::db/query
        :query/key [:articles-menu :items]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id * {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/article
         #{:post.status/x :post.status/y}]}
       {:query/name ::db/query
        :query/key [:navigation/i18n :articles-menu]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?type [?status ...] [?field-key ...] ?lang]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :translatable/fields ?f]
                   [?f :field/key ?field-key]
                   [?f :field/lang ?lang]]}
         :post.type/article
         #{:post.status/x :post.status/y}
         #{:custom :other}
         :en]}
       {:query/name ::navigation/merge-post-menu-items
        :query/key [:articles-menu :items]
        :route/name :bread.route/page
        :router router
        :lang :en}]
      {:menus
       [{:menu/key :articles-menu
         :menu/type ::navigation/posts
         :post/type :post.type/article
         :post/status [:post.status/x :post.status/y]
         :translatable/fields [:custom :other]
         :route/name :bread.route/page}]}

      ;; Page type composes with status and other options.
      [{:query/name ::bread/value
        :query/key [:posts-menu]
        :query/value {:menu/type ::navigation/posts
                      :post/type :post.type/page}}
       {:query/name ::db/query
        :query/key [:posts-menu :items]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id * {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/page
         #{:post.status/x :post.status/y}]}
       {:query/name ::db/query
        :query/key [:navigation/i18n :posts-menu]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?type [?status ...] [?field-key ...] ?lang]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :translatable/fields ?f]
                   [?f :field/key ?field-key]
                   [?f :field/lang ?lang]]}
         :post.type/page
         #{:post.status/x :post.status/y}
         #{:custom :other}
         :en]}
       {:query/name ::navigation/merge-post-menu-items
        :query/key [:posts-menu :items]
        :route/name :bread.route/page
        :router router
        :lang :en}]
      {:menus
       [{:menu/key :posts-menu
         :menu/type ::navigation/pages
         :post/status [:post.status/x :post.status/y]
         :translatable/fields [:custom :other]
         :route/name :bread.route/page}]}

      ;; Location menu.
      [{:query/name ::bread/value
        :query/key [:location-nav]
        :query/value {:menu/type ::navigation/location}}
       {:query/name ::db/query
        :query/key [:location-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id
                            :menu/key
                            :menu/uuid
                            :menu/locations
                            {:menu/items [:db/id
                                          :menu.item/order
                                          {:menu.item/children [*]}
                                          {:menu.item/entity [*]}
                                          :translatable/fields]}])]
           :in [$ ?loc]
           :where [[?e :menu/locations ?loc]]}
         :location-nav]}
       {:query/name ::db/query
        :query/key [:location-nav :menu/items :translatable/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?e1 ?lang]
           :where [[?e :field/lang ?lang]
                   [?e0 :translatable/fields ?e]
                   [?e1 :menu/items ?e0]]}
         [::bread/data :location-nav :db/id]
         :en]}]
      {:menus
       [{:menu/key :location-nav
         :menu/type ::navigation/location}]}

      ;;
      )))

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
