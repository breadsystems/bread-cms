;; Test navigation API at the datastore -> ::bread/data level.
(ns systems.bread.alpha.navigation-test
  (:require
    [clojure.test :refer [deftest is are]]
    [systems.bread.alpha.navigation :as navigation]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-location-menu-queries
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

      [{:query/name ::store/query
        :query/key [:menus :main-nav]
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
       {:query/name ::navigation/expand-entities
        :query/key [:menus :main-nav]}]
      {:menus
       [{:menu/key :main-nav
         :menu/type :menu.type/posts
         :post/type :post.type/page}]
       :global-menus false}

      ;; Querying for custom post statuses
      [{:query/name ::store/query
        :query/key [:menus :main-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/page
         #{:post.status/x :post.status/y}]}
       {:query/name ::navigation/expand-entities
        :query/key [:menus :main-nav]}]
      {:menus
       [{:menu/key :main-nav
         :menu/type :menu.type/posts
         :post/type :post.type/page
         :post/status [:post.status/x :post.status/y]}]
       :global-menus false}

      ;; Page type composes with status and other options.
      [{:query/name ::store/query
        :query/key [:menus :pages-nav]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id {:post/children [*]}])]
           :in [$ ?type [?status ...]]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   (not-join [?e] [?parent :post/children ?e])]}
         :post.type/page
         #{:post.status/a :post.status/b}]}
       {:query/name ::navigation/expand-entities
        :query/key [:menus :pages-nav]}]
      {:menus
       [{:menu/key :pages-nav
         :menu/type :menu.type/pages
         :post/status [:post.status/a :post.status/b]}]
       :global-menus false}

      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
