(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-dispatch-post-queries
  (let [db ::FAKEDB
        app (plugins->loaded [(datastore->plugin db)
                              (dispatcher/plugin)])
        ->app (fn [dispatcher]
                (assoc app ::bread/dispatcher dispatcher))]

    (are
      [query dispatcher]
      (= query (-> dispatcher
                   ->app
                   (bread/hook ::bread/dispatch)
                   ::bread/queries))

      ;; {:uri "/en/simple"}
      ;; i18n'd by default
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ ?type ?status ?slug_0]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   (not-join [?e] [?root-ancestor :post/children ?e])]}
         :post.type/page
         :post.status/published
         "simple"]}]
      {:dispatcher/type :dispatcher.type/page
       ;; pull and key come from component
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:slugs "simple" :lang "en"}}

      ;; {:uri "/en/one/two"}
      ;; Default key -> :post
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ ?type ?status ?slug_0 ?slug_1]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   [?parent_1 :post/children ?e]
                   [?parent_1 :post/slug ?slug_1]
                   (not-join
                     [?parent_1]
                     [?root-ancestor :post/children ?parent_1])]}
         :post.type/page
         :post.status/published
         "two"
         "one"]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       ;; default key -> :post
       :route/params {:slugs "one/two" :lang "en"}}

      ;; {:uri "/en/one/two"}
      ;; Default key -> :post
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ ?type ?status ?slug_0 ?slug_1]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   [?parent_1 :post/children ?e]
                   [?parent_1 :post/slug ?slug_1]
                   (not-join
                     [?parent_1]
                     [?root-ancestor :post/children ?parent_1])]}
         :post.type/page
         :post.status/published
         "two"
         "one"]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key nil ;; default -> :post
       :route/params {:slugs "one/two" :lang "en"}}

      ;; {:uri "/en/one/two"}
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ ?type ?status ?slug_0 ?slug_1]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   [?parent_1 :post/children ?e]
                   [?parent_1 :post/slug ?slug_1]
                   (not-join
                     [?parent_1]
                     [?root-ancestor :post/children ?parent_1])]}
         :post.type/page
         :post.status/published
         "two"
         "one"]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:slugs "one/two" :lang "en"}}

      ;; {:uri "/en/one/two/three"}
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ ?type ?status ?slug_0 ?slug_1 ?slug_2]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   [?parent_1 :post/children ?e]
                   [?parent_1 :post/slug ?slug_1]
                   [?parent_2 :post/children ?parent_1]
                   [?parent_2 :post/slug ?slug_2]
                   (not-join
                     [?parent_2]
                     [?root-ancestor :post/children ?parent_2])]}
         :post.type/page
         :post.status/published
         "three"
         "two"
         "one"]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:slugs "one/two/three" :lang "en"}}

      ;; {:uri "/en/simple"}
      ;; :post/fields i18n
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :post/fields]) .]
           :in [$ ?type ?status ?slug_0]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   (not-join [?e] [?root-ancestor :post/children ?e])]}
         :post.type/page
         :post.status/published
         "simple"]}
       {:query/name ::store/query
        :query/key [:post :post/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/content])]
           :in [$ ?p ?lang]
           :where [[?p :post/fields ?e]
                   [?e :field/lang ?lang]]}
         [::bread/data :post :db/id]
         :en]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :post/fields]
       :dispatcher/key :post
       :route/params {:slugs "simple" :lang "en"}}

      ;; {:uri "/en/simple"}
      ;; :post/fields i18n w/ nested pull clause
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title {:post/fields
                                                [:field/key
                                                 :field/lang]}]) .]
           :in [$ ?type ?status ?slug_0]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   (not-join
                     [?e]
                     [?root-ancestor :post/children ?e])]}
         :post.type/page
         :post.status/published
         "simple"]}
       {:query/name ::store/query
        :query/key [:post :post/fields]
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :field/key :field/lang])]
           :in [$ ?p ?lang]
           :where [[?p :post/fields ?e]
                   [?e :field/lang ?lang]]}
         [::bread/data :post :db/id]
         :en]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title {:post/fields [:field/key :field/lang]}]
       :dispatcher/key :post
       :route/params {:slugs "simple" :lang "en"}}

      ;; {:uri "/en"}
      ;; home page - no `slugs`
      [{:query/name ::store/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ ?type ?status ?slug_0]
           :where [[?e :post/type ?type]
                   [?e :post/status ?status]
                   [?e :post/slug ?slug_0]
                   (not-join [?e] [?root-ancestor :post/children ?e])]}
         :post.type/page
         :post.status/published
         ;; Empty slug!
         ""]}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:lang "en"}})))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
