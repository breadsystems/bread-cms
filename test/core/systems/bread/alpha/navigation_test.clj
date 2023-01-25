;; Test navigation API at the datastore -> ::bread/data level.
(ns systems.bread.alpha.navigation-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.navigation :as navigation]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-post-menu-queries
  (let [db ::FAKEDB]
    (are
      [data navigation-config]
      (= data (-> (plugins->loaded [(datastore->plugin db)
                                    (i18n/plugin {:query-strings? false
                                                  :query-lang? false})
                                    (navigation/plugin navigation-config)])
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
      [] {:global-menus false}

      [{:query/name ::bread/value
        :query/key [:menus :main-nav]
        :query/value {:menu/type :menu.type/posts
                      :post/type :post.type/page}}
       {:query/name ::store/query
        :query/key [:menus :main-nav :items]
        :query/db db
        :query/args
        ['{:find [(pull ?e [;; Post menus don't store their own data in the db:
                            ;; instead, they follow the post hierarchy itself.
                            :db/id {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/page
         #{:post.status/published}]}
       {:query/name ::store/query
        :query/key [:navigation/i18n :main-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?type [?status ...] [?field-key ...] ?lang]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/fields ?f]
                   [?f :field/key ?field-key]
                   [?f :field/lang ?lang]]}
         :post.type/page
         #{:post.status/published}
         #{:title}
         :en]}
       {:query/name ::navigation/merge-post-menu-items
        :query/key [:menus :main-nav :items]}]
      {:menus
       [{:menu/key :main-nav
         :menu/type :menu.type/posts
         :post/type :post.type/page}]
       :global-menus false}

      ;; Support custom post status, post type, and field keys.
      [{:query/name ::bread/value
        :query/key [:custom-menu-key :main-nav]
        :query/value {:menu/type :menu.type/posts
                      :post/type :post.type/article}}
       {:query/name ::store/query
        :query/key [:custom-menu-key :main-nav :items]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/article
         #{:post.status/x :post.status/y}]}
       {:query/name ::store/query
        :query/key [:navigation/i18n :main-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?type [?status ...] [?field-key ...] ?lang]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/fields ?f]
                   [?f :field/key ?field-key]
                   [?f :field/lang ?lang]]}
         :post.type/article
         #{:post.status/x :post.status/y}
         #{:custom :other}
         :en]}
       {:query/name ::navigation/merge-post-menu-items
        :query/key [:custom-menu-key :main-nav :items]}]
      {:menus
       [{:menu/key :main-nav
         :menu/type :menu.type/posts
         :post/type :post.type/article
         :post/status [:post.status/x :post.status/y]
         :post/fields [:custom :other]}]
       :global-menus false
       :menus-key :custom-menu-key}

      ;; Page type composes with status and other options.
      [{:query/name ::bread/value
        :query/key [:custom-menu-key :main-nav]
        :query/value {:menu/type :menu.type/posts
                      :post/type :post.type/page}}
       {:query/name ::store/query
        :query/key [:custom-menu-key :main-nav :items]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/page
         #{:post.status/x :post.status/y}]}
       {:query/name ::store/query
        :query/key [:navigation/i18n :main-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?f [:db/id :field/key :field/content])]
           :in [$ ?type [?status ...] [?field-key ...] ?lang]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/fields ?f]
                   [?f :field/key ?field-key]
                   [?f :field/lang ?lang]]}
         :post.type/page
         #{:post.status/x :post.status/y}
         #{:custom :other}
         :en]}
       {:query/name ::navigation/merge-post-menu-items
        :query/key [:custom-menu-key :main-nav :items]}]
      {:menus
       [{:menu/key :main-nav
         :menu/type :menu.type/pages
         :post/status [:post.status/x :post.status/y]
         :post/fields [:custom :other]}]
       :global-menus false
       :menus-key :custom-menu-key})))

(deftest test-merge-post-menu-items
  (are
    [menus unmerged]
    (= menus (-> (bread/query
                   {:query/name ::navigation/merge-post-menu-items
                    :query/key [:menus :main-nav]}
                   unmerged)))

    ;; simple case, default title field
    {:menu/type :menu.type/posts
     :post/type :post.type/page
     :items [{:title "eleven"
              :entity {:db/id 1
                       :post/slug "parent-page"
                       :post/fields {:title "eleven"}}
              :children []}
             {:title "twenty-two"
              :entity {:db/id 2
                       :post/slug ""
                       :post/fields {:title "twenty-two"}}
              :children []}]}
    {:menus
     {:main-nav
      {:menu/type :menu.type/posts
       :post/type :post.type/page
       :items [[{:db/id 1
                 :post/slug "parent-page"
                 :post/fields [{:db/id 11}]}]
               [{:db/id 2
                 :post/slug ""
                 :post/fields [{:db/id 22}]}]]}}
     :navigation/i18n
     {:main-nav
      [[{:db/id 11 :field/key :title :field/content "\"eleven\""}]
       [{:db/id 22 :field/key :title :field/content "\"twenty-two\""}]]}}

    ;; non-recursive case
    {:menu/type :menu.type/posts
     :post/type :post.type/page
     :title-field :one
     :items [{:title "eleven"
              :entity {:db/id 1
                       :post/slug "parent-page"
                       :post/fields {:one "eleven"
                                     :two "twelve"}}
              :children []}
             {;; NOTE: :one is missing from this post's fields.
              :title nil
              :entity {:db/id 2
                       :post/slug ""
                       :post/fields {;; Handle missing fields gracefully.
                                     :two "twenty-two"}}
              :children []}]}
    {:menus
     {:main-nav
      {:menu/type :menu.type/posts
       :post/type :post.type/page
       :title-field :one
       :items [[{:db/id 1
                 :post/slug "parent-page"
                 :post/fields [{:db/id 11} {:db/id 12} {:db/id 13}]}]
               [{:db/id 2
                 :post/slug ""
                 :post/fields [{:db/id 21} {:db/id 22} {:db/id 23}]}]]}}
     :navigation/i18n
     {:main-nav
      [[{:db/id 11 :field/key :one :field/content "\"eleven\""}]
       [{:db/id 12 :field/key :two :field/content "\"twelve\""}]
       [{:db/id 22 :field/key :two :field/content "\"twenty-two\""}]]}}

    ;; recursive case (with children)
    {:menu/type :menu.type/posts
     :post/type :post.type/page
     :title-field :one
     :items [{:title "eleven"
              :entity {:db/id 1
                       :post/slug "parent-page"
                       :post/fields {:one "eleven"
                                     :two "twelve"}}
              :children [{:title "thirty-one"
                          :entity {:db/id 3
                                   :post/slug "child-page"
                                   :post/fields {:one "thirty-one"
                                                 :two "thirty-two"}}
                          :children []}
                         {:title "forty-one"
                          :entity {:db/id 4
                                   :post/slug "another-kid"
                                   :post/fields {:one "forty-one"
                                                 :two "forty-two"}}
                          :children [{:title "fifty-one"
                                      :entity {:db/id 5
                                               :post/slug "grandchild"
                                               :post/fields {:one "fifty-one"
                                                             :two "fifty-two"}}
                                      :children []}
                                     {:title "sixty-one"
                                      :entity {:db/id 6
                                               :post/slug "another-grandchild"
                                               :post/fields {:one "sixty-one"
                                                             :two "sixty-two"}}
                                      :children []}]}]}
             {:title "twenty-one"
              :entity {:db/id 2
                       :post/slug ""
                       :post/fields {:one "twenty-one"
                                     :two "twenty-two"}}
              :children []}]}
    {:menus
     {:main-nav
      {:menu/type :menu.type/posts
       :post/type :post.type/page
       :title-field :one
       :items [[{:db/id 1
                 :post/slug "parent-page"
                 :post/fields [{:db/id 11} {:db/id 12} {:db/id 13}]
                 :post/children
                 [{:db/id 3
                   :post/slug "child-page"
                   :post/fields [{:db/id 31} {:db/id 32} {:db/id 33}]}
                  {:db/id 4
                   :post/slug "another-kid"
                   :post/fields [{:db/id 41} {:db/id 42} {:db/id 43}]
                   :post/children
                   [{:db/id 5
                     :post/slug "grandchild"
                     :post/fields [{:db/id 51} {:db/id 52} {:db/id 53}]}
                    {:db/id 6
                     :post/slug "another-grandchild"
                     :post/fields [{:db/id 61} {:db/id 62} {:db/id 63}]}]}]}]
               [{:db/id 2
                 :post/slug ""
                 :post/fields [{:db/id 21} {:db/id 22} {:db/id 23}]}]]}}
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
       [{:db/id 62 :field/key :two :field/content "\"sixty-two\""}]]}}

    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
