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
      (not-join [?child] [?_ :thing/children ?child])]
    1

    '[(post-ancestry ?child ?slug_0 ?slug_1)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      (not-join [?ancestor_1] [?_ :thing/children ?ancestor_1])]
    2

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :thing/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      (not-join [?ancestor_2] [?_ :thing/children ?ancestor_2])]
    3

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :thing/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      [?ancestor_3 :thing/children ?ancestor_2]
      [?ancestor_3 :thing/slug ?slug_3]
      (not-join [?ancestor_3] [?_ :thing/children ?ancestor_3])]
    4

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3 ?slug_4)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :thing/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      [?ancestor_3 :thing/children ?ancestor_2]
      [?ancestor_3 :thing/slug ?slug_3]
      [?ancestor_4 :thing/children ?ancestor_3]
      [?ancestor_4 :thing/slug ?slug_4]
      (not-join [?ancestor_4] [?_ :thing/children ?ancestor_4])]
    5))

(deftest test-post-dispatcher
  (let [attrs-map {:thing/fields {:db/cardinality :db.cardinality/many}
                   :post/taxons  {:db/cardinality :db.cardinality/many}}
        app (plugins->loaded [(db->plugin ::FAKEDB)
                              (i18n/plugin {:query-strings? false
                                            :query-lang? false})
                              (dispatcher/plugin)
                              {:hooks
                               {::bread/attrs-map
                                [{:action/name ::bread/value
                                  :aciton/value attrs-map}]}}])]

    (are
      [expansions dispatcher]
      (= expansions (let [counter (atom 0)]
                   (-> (assoc app ::bread/dispatcher dispatcher)
                       (bread/hook ::bread/dispatch)
                       ::bread/expansions)))

      [{:expansion/name ::db/query
        :expansion/description "Query for pages matching the current request URI"
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [*]}]) .]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?type ?status]}
         '[[(post-ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :thing/children ?child])]]
         "hello"
         :post.type/page
         :post.status/published]}
       {:expansion/name ::i18n/fields
        :expansion/key :post
        :expansion/description "Process translatable fields."
        :spaths [[:thing/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type ::post/page
       :dispatcher/pull '[:thing/slug {:thing/fields [*]}]
       :dispatcher/key :post
       :route/params {:lang "en" :thing/slug* "hello"}}

      ;; Post type, status are dynamic.
      [{:expansion/name ::db/query
        :expansion/description "Query for pages matching the current request URI"
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [*]}]) .]
           :where [(post-ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?type ?status]}
         '[[(post-ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :thing/children ?child])]]
         "hello"
         :post.type/article
         :post.status/draft]}
       {:expansion/name ::i18n/fields
        :expansion/key :post
        :expansion/description "Process translatable fields."
        :spaths [[:thing/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type ::post/page
       :dispatcher/pull '[:thing/slug {:thing/fields [*]}]
       :dispatcher/key :post
       :route/params {:lang "en" :thing/slug* "hello"}
       :post/status :post.status/draft
       :post/type :post.type/article}

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
