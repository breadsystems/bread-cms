(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded]]))

(deftest test-create-post-ancestry-rule
  (are
    [rule n]
    (= rule (post/create-post-ancestry-rule n))

    '[(post-ancestry ?child ?slug_0)
      [?child :post/slug ?slug_0]
      (not-join [?child] [?_ :post/children ?child])]
    1

    '[(post-ancestry ?child ?slug_0 ?slug_1)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      (not-join [?ancestor_1] [?_ :post/children ?ancestor_1])]
    2

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :post/slug ?slug_2]
      (not-join [?ancestor_2] [?_ :post/children ?ancestor_2])]
    3

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :post/slug ?slug_2]
      [?ancestor_3 :post/children ?ancestor_2]
      [?ancestor_3 :post/slug ?slug_3]
      (not-join [?ancestor_3] [?_ :post/children ?ancestor_3])]
    4

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3 ?slug_4)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :post/slug ?slug_2]
      [?ancestor_3 :post/children ?ancestor_2]
      [?ancestor_3 :post/slug ?slug_3]
      [?ancestor_4 :post/children ?ancestor_3]
      [?ancestor_4 :post/slug ?slug_4]
      (not-join [?ancestor_4] [?_ :post/children ?ancestor_4])]
    5))

(deftest test-dispatch-post-queries
  (let [db ::FAKEDB
        app (plugins->loaded [(db->plugin db)
                              (i18n/plugin {:query-strings? false
                                            :query-lang? false})
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
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/slug]) .]
           :in [$ % ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 1)]
         "simple"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       ;; pull and key come from component
       :dispatcher/pull [:post/slug]
       :dispatcher/key :post
       :route/params {:slugs "simple" :lang "en"}}

      ;; {:uri "/en/one/two"}
      ;; Default key -> :post
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ % ?slug_1 ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0 ?slug_1)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 2)]
         "one"
         "two"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       ;; default key -> :post
       :route/params {:slugs "one/two" :lang "en"}}

      ;; {:uri "/en/one/two"}
      ;; Default key -> :post
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ % ?slug_1 ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0 ?slug_1)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 2)]
         "one"
         "two"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key nil ;; default -> :post
       :route/params {:slugs "one/two" :lang "en"}}

      ;; {:uri "/en/one/two"}
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ % ?slug_1 ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0 ?slug_1)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 2)]
         "one"
         "two"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:slugs "one/two" :lang "en"}}

      ;; {:uri "/en/one/two/three"}
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ % ?slug_2 ?slug_1 ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0 ?slug_1 ?slug_2)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 3)]
         "one"
         "two"
         "three"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:slugs "one/two/three" :lang "en"}}

      ;; {:uri "/en/simple"}
      ;; :translatable/fields alone only queries for :db/id, not :field/content
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :translatable/fields]) .]
           :in [$ % ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 1)]
         "simple"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :translatable/fields]
       :dispatcher/key :post
       :route/params {:slugs "simple" :lang "en"}}

      ;; {:uri "/en/simple"}
      ;; :translatable/fields WITHOUT :field/content
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title {:translatable/fields
                                                [:field/key
                                                 :field/lang]}]) .]
           :in [$ % ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 1)]
         "simple"
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title {:translatable/fields [:field/key :field/lang]}]
       :dispatcher/key :post
       :route/params {:slugs "simple" :lang "en"}}

      ;; {:uri "/en"}
      ;; home page - no `slugs`
      [{:query/name ::db/query
        :query/key :post
        :query/db db
        :query/args
        ['{:find [(pull ?e [:db/id :post/title :custom/key]) .]
           :in [$ % ?slug_0 ?type ?status]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]}
         [(post/create-post-ancestry-rule 1)]
         ;; Empty slug!
         ""
         :post.type/page
         :post.status/published]}
       {:query/name ::post/compact-fields
        :query/key :post
        :query/description
        "Compact :translatable/fields into a more usable shape."}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull [:post/title :custom/key]
       :dispatcher/key :post
       :route/params {:lang "en"}})))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
