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

(comment
  (require '[kaocha.repl :as k])
  (k/run))
