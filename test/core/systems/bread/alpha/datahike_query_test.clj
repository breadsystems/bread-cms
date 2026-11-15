(ns systems.bread.alpha.datahike-query-test
  (:require
    [clojure.test :refer [are deftest is]]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded use-db]]))

(def config {:db/type :datahike
             :db/config {:store {:backend :mem :id "expand-db"}}
             :db/query-attrs? false
             :db/initial-txns
             [;; init simplified schema
              {:db/ident :attr/format
               :db/valueType :db.type/keyword
               :db/cardinality :db.cardinality/one
               :db/doc "Attribute format"}
              {:db/ident :thing/slug
               :db/valueType :db.type/string
               :db/cardinality :db.cardinality/one}
              {:db/ident :post/parent
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/one}
              {:db/ident :post/fields
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/many}
              {:db/ident :field/key
               :db/valueType :db.type/keyword
               :db/cardinality :db.cardinality/one}
              {:db/ident :field/lang
               :db/valueType :db.type/keyword
               :db/cardinality :db.cardinality/one}
              {:db/ident :field/content
               :db/valueType :db.type/string
               :db/cardinality :db.cardinality/one
               :attr/format :edn}

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

(def $attrs-map
  {:attr/format
   {:db/ident :attr/format
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "Attribute format"}
   :thing/slug
   {:db/ident :thing/slug
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   :post/parent
   {:db/ident :post/parent
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   :post/fields
   {:db/ident :post/fields
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}
   :field/key
   {:db/ident :field/key
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   :field/lang
   {:db/ident :field/lang
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   :field/content
   {:db/ident :field/content
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :attr/format :edn}})

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
        :content-found? false}
       [{:expansion/name ::db/query
         :expansion/key :post
         :expansion/db db
         :expansion/args
         ['{:find [(pull ?e [:thing/slug {:post/fields
                                          [:field/key :field/lang]}]) .]
            :in [$ ?slug]
            :where [[?e :thing/slug ?slug]]}
          "non-existent-slug"]}]

       {:post {:thing/slug "parent-post"
               :post/fields [{:field/key :stuff :field/lang :en}
                             {:field/key :thingy :field/lang :en}
                             {:field/key :stuff :field/lang :fr}
                             {:field/key :thingy :field/lang :fr}]}
        :content-found? true}
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
        :content-found? true}
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

        ,)))

(deftest test-attrs

  (let [app (plugins->loaded [(db/plugin config)])
        attrs (db/attrs (db/database app))]
    (is (= $attrs-map
           (into {} (map (juxt key (comp #(dissoc % :db/id) val)) attrs))))))

(deftest test-attrs-expansion

  (let [app (plugins->loaded [(db/plugin (assoc config :db/query-attrs? true))])
        data (::bread/data app)]
    (is (= $attrs-map
           (into {} (map (juxt key (comp #(dissoc % :db/id) val)) (:db/attrs data)))))
    (is (= {:field/content :edn} (:attr/format data)))))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
