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
      [?child :thing/slug ?slug_0]
      (not-join [?child] [?_ :post/children ?child])]
    1

    '[(post-ancestry ?child ?slug_0 ?slug_1)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      (not-join [?ancestor_1] [?_ :post/children ?ancestor_1])]
    2

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      (not-join [?ancestor_2] [?_ :post/children ?ancestor_2])]
    3

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      [?ancestor_3 :post/children ?ancestor_2]
      [?ancestor_3 :thing/slug ?slug_3]
      (not-join [?ancestor_3] [?_ :post/children ?ancestor_3])]
    4

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3 ?slug_4)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      [?ancestor_3 :post/children ?ancestor_2]
      [?ancestor_3 :thing/slug ?slug_3]
      [?ancestor_4 :post/children ?ancestor_3]
      [?ancestor_4 :thing/slug ?slug_4]
      (not-join [?ancestor_4] [?_ :post/children ?ancestor_4])]
    5))

(deftest test-post-dispatcher
  (let [attrs-map {:translatable/fields {:db/cardinality :db.cardinality/many}
                   :post/taxons         {:db/cardinality :db.cardinality/many}}
        app (plugins->loaded [(db->plugin ::FAKEDB)
                              (i18n/plugin {:query-strings? false
                                            :query-lang? false})
                              (dispatcher/plugin)
                              {:hooks
                               {::bread/attrs-map
                                [{:action/name ::bread/value
                                  :aciton/value attrs-map}]}}])]

    (are
      [queries dispatcher]
      (= queries (let [counter (atom 0)]
                   (-> (assoc app ::bread/dispatcher dispatcher)
                       (bread/hook ::bread/dispatch)
                       ::bread/queries)))

      [{:query/name ::db/query
        :query/key :post
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields [*]}]) .]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?type ?status]}
         '[[(post-ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :post/children ?child])]]
         ""
         :post.type/page
         :post.status/published]}
       {:query/name ::i18n/fields
        :query/key :post
        :query/description "Process translatable fields."
        :spaths [[:translatable/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull '[:thing/slug {:translatable/fields [*]}]
       :dispatcher/key :post
       {:lang "en" :slugs ""} :route/params}

      ;; Post type, status are dynamic.
      [{:query/name ::db/query
        :query/key :post
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields [*]}]) .]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?type ?status]}
         '[[(post-ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :post/children ?child])]]
         ""
         :post.type/article
         :post.status/draft]}
       {:query/name ::i18n/fields
        :query/key :post
        :query/description "Process translatable fields."
        :spaths [[:translatable/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type :dispatcher.type/page
       :dispatcher/pull '[:thing/slug {:translatable/fields [*]}]
       :dispatcher/key :post
       {:lang "en" :slugs ""} :route/params
       :post/status :post.status/draft
       :post/type :post.type/article}

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
