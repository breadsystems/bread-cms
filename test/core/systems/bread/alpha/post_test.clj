(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              naive-router
                                              plugins->loaded]]))

(deftest test-by-slug*-expansion
  (let [app (plugins->loaded [(db->plugin ::FAKEDB)])]

    (are
      [expansion dispatcher]
      (= expansion
         (post/by-slug*-expansion (assoc app ::bread/dispatcher dispatcher)))

      {:expansion/name ::db/query
       :expansion/description "Query for a single post matching the current request URI"
       :expansion/key :post
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields [*]}]) .]
          :where [(ancestry ?e ?slug_0)
                  [?e :post/status ?status]]
          :in [$ % ?slug_0 ?status]}
        '[[(ancestry ?child ?slug_0)
           [?child :thing/slug ?slug_0]
           (not-join [?child] [?_ :thing/children ?child])]]
        "hello"
        :post.status/published]}
      {:dispatcher/type ::post/post=>
       :dispatcher/pull '[:thing/slug {:thing/fields [*]}]
       :dispatcher/key :post
       :route/params {:lang "en" :thing/slug* "hello"}}

      )))

(deftest test-post-dispatcher
  (let [attrs-map {:thing/fields {:db/cardinality :db.cardinality/many}
                   :post/taxons  {:db/cardinality :db.cardinality/many}}
        app (plugins->loaded [(db->plugin ::FAKEDB)
                              (i18n/plugin {:rtl-langs nil
                                            :global-strings nil
                                            :query-global-strings? false
                                            :query-lang? false})
                              (dispatcher/plugin)
                              (route/plugin {:router (naive-router)})
                              {:hooks
                               {::bread/attrs-map
                                [{:action/name ::bread/value
                                  :aciton/value attrs-map}]}}])]

    (are
      [expansions dispatcher]
      (= expansions (-> (assoc app ::bread/dispatcher dispatcher)
                        (bread/hook ::bread/dispatch)
                        ::bread/expansions))

      [{:expansion/name ::db/query
        :expansion/description "Query for a single post matching the current request URI"
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [*]}]) .]
           :where [(ancestry ?e ?slug_0)
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?status]}
         '[[(ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :thing/children ?child])]]
         "hello"
         :post.status/published]}
       {:expansion/name ::i18n/fields
        :expansion/key :post
        :expansion/description "Process translatable fields."
        :spaths [[:thing/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type ::post/by-slug*=>
       :dispatcher/pull '[:thing/slug {:thing/fields [*]}]
       :dispatcher/key :post
       :route/params {:lang "en" :thing/slug* "hello"}}

      [{:expansion/name ::db/query
        :expansion/description "Query for a single post matching the current request URI"
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [*]}]) .]
           :where [(ancestry ?e ?slug_0)
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?status]}
         '[[(ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :thing/children ?child])]]
         "hello"
         :post.status/published]}
       {:expansion/name ::i18n/fields
        :expansion/key :post
        :expansion/description "Process translatable fields."
        :spaths [[:thing/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type ::post/post=>
       :dispatcher/pull '[:thing/slug {:thing/fields [*]}]
       :dispatcher/key :post
       :route/params {:lang "en" :thing/slug* "hello"}}

      [{:expansion/name ::db/query
        :expansion/description "Query for a single page matching the current request URI"
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [*]}]) .]
           :where [(ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?type ?status]}
         '[[(ancestry ?child ?slug_0)
            [?child :thing/slug ?slug_0]
            (not-join [?child] [?_ :thing/children ?child])]]
         "hello"
         :page
         :post.status/published]}
       {:expansion/name ::i18n/fields
        :expansion/key :post
        :expansion/description "Process translatable fields."
        :spaths [[:thing/fields]]
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}}]
      {:dispatcher/type ::post/page=>
       :dispatcher/pull '[:thing/slug {:thing/fields [*]}]
       :dispatcher/key :post
       :route/params {:lang "en" :thing/slug* "hello"}}

      ;; Post type, status are dynamic.
      [{:expansion/name ::db/query
        :expansion/description "Query for a single post matching the current request URI"
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [*]}]) .]
           :where [(ancestry ?e ?slug_0)
                   [?e :post/type ?type]
                   [?e :post/status ?status]]
           :in [$ % ?slug_0 ?type ?status]}
         '[[(ancestry ?child ?slug_0)
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
      {:dispatcher/type ::post/post=>
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
