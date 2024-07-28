(ns systems.bread.alpha.datahike-query-test
  (:require
    [clojure.test :refer [are deftest is]]
    [kaocha.repl :as k]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded use-db]]))

(def config {:db/type :datahike
             :store {:backend :mem :id "expand-db"}
             :db/initial-txns
             [;; init simplified schema
              {:db/ident :thing/slug
               :db/valueType :db.type/string
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :post/parent
               :db/valueType :db.type/ref
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :post/fields
               :db/valueType :db.type/ref
               :db/index true
               :db/cardinality :db.cardinality/many}
              {:db/ident :field/key
               :db/valueType :db.type/keyword
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :field/lang
               :db/valueType :db.type/keyword
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :field/content
               :db/valueType :db.type/string
               :db/index true
               :db/cardinality :db.cardinality/one}

              ;; init post content
              {:db/id 100
               :thing/slug "parent-post"
               :post/fields [{:field/key :stuff
                              :field/lang :en
                              :field/content "hello"}
                             {:field/key :thingy
                              :field/lang :en
                              :field/content "thing"}
                             {:field/key :stuff
                              :field/lang :fr
                              :field/content "bonjour"}
                             {:field/key :thingy
                              :field/lang :fr
                              :field/content "chose"}]}]})

(use-db :each config)

(deftest test-datahike-query

  (let [app (plugins->loaded [(db/plugin config) (expansion/plugin)])
        db (db/database app)]
    (are
      [data expansions]
      (= data (-> app
                  (assoc ::bread/expansions expansions
                         ::bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                           :dispatcher/key :post})
                  (bread/hook ::bread/expand)
                  ::bread/data))

       ;; Querying for a non-existent post
       {:post false
        :not-found? true}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:thing/slug {:post/fields
                                          [:field/key :field/lang]}]) .]
            :in [$ ?slug]
            :where [[?e :thing/slug ?slug]]}
          "non-existent-slug"]}]

       ;; Querying for a non-existent post and its fields
       {:post false
        :not-found? true}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:thing/slug {:post/fields
                                          [:field/key :field/lang]}]) .]
            :in [$ ?slug]
            :where [[?e :thing/slug ?slug]]}
          "non-existent-slug"]}
        {:expansion/key [:post :post/fields]
         :expansion/name ::db/query
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:field/key :field/content])]
            :in [$ ?p ?lang]
            :where [[?p :post/fields ?e]
                    [?e :field/lang ?lang]]}
          [::bread/data :post :db/id]
          :en]}]

       {:post {:thing/slug "parent-post"
               :post/fields [{:field/key :stuff :field/lang :en}
                             {:field/key :thingy :field/lang :en}
                             {:field/key :stuff :field/lang :fr}
                             {:field/key :thingy :field/lang :fr}]}
        :not-found? false}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:thing/slug {:post/fields
                                          [:field/key :field/lang]}]) .]
            :in [$]
            :where [[?e :thing/slug ?slug]]}]}]

       ;; with query input args (slug)
       {:post {:thing/slug "parent-post"
               :post/fields [{:field/key :stuff :field/lang :en}
                             {:field/key :thingy :field/lang :en}
                             {:field/key :stuff :field/lang :fr}
                             {:field/key :thingy :field/lang :fr}]}
        :not-found? false}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:thing/slug {:post/fields
                                          [:field/key
                                           :field/lang]}]) .]
            :in [$ ?slug]
            :where [[?e :thing/slug ?slug]]}
          "parent-post"]}]

       ;; with explicit input args (for i18n)
       {:post {:thing/slug "parent-post"
               :post/fields [[{:field/key :thingy
                               :field/content "thing"}]
                             [{:field/key :stuff
                              :field/content "hello"}]]}
        :not-found? false}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:thing/slug]) .]
            :in [$ ?slug]
            :where [[?e :thing/slug ?slug]]}
          "parent-post"]}
        {:expansion/name ::db/query
         :expansion/key [:post :post/fields]
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:field/key :field/content])]
            :in [$ ?p ?lang]
            :where [[?p :post/fields ?e]
                    [?e :field/lang ?lang]]}
          100
          :en]}]

       ;; deriving input args from previous data (for i18n)
       {:post {:db/id 100 :thing/slug "parent-post"
               :post/fields [[{:field/key :thingy
                               :field/content "thing"}]
                             [{:field/key :stuff
                               :field/content "hello"}]]}
        :not-found? false}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:db/id :thing/slug]) .]
            :in [$ ?slug]
            :where [[?e :thing/slug ?slug]]}
          "parent-post"]}
        {:expansion/name ::db/query
         :expansion/key [:post :post/fields]
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:field/key :field/content])]
           :in [$ ?p ?lang]
           :where [[?p :post/fields ?e]
                   [?e :field/lang ?lang]]}
          [::bread/data :post :db/id]
          :en]}])))

(comment
  (k/run))
