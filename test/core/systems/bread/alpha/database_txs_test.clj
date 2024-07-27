(ns systems.bread.alpha.database-txs-test
  (:require
    [clojure.test :refer [are deftest testing use-fixtures]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.test-helpers :refer [db-config->loaded
                                              use-db]]))

(def config {:db/type :datahike
              :store {:backend :mem
                      :id "plugin-db"}
              ;; Printing the whole schema slows down results on error/failure,
              ;; so just use the mininum viable post schema for what we need
              ;; to run this test.
              ;; TODO get Kaocha not to print debug logs?
              :db/initial-txns
              [{:db/ident :thing/slug
                :db/valueType :db.type/string
                :db/index true
                :db/cardinality :db.cardinality/one}
               {:db/ident :post/fields
                :db/valueType :db.type/ref
                :db/cardinality :db.cardinality/many}
               {:db/ident :field/key
                :db/valueType :db.type/keyword
                :db/cardinality :db.cardinality/one}
               {:db/ident :field/content
                :db/valueType :db.type/string
                :db/cardinality :db.cardinality/one}]})

(use-db :each config)

(deftest test-add-txs

  (testing "it runs zero or more transactions on the database"
    (are [page args]
      (= page (let [[slug txs] args
                    app (-> (db-config->loaded config)
                            (db/add-txs txs))
                    handler (bread/handler app)
                    query
                    '{:find
                      [(pull ?e [:db/id
                                 :thing/slug
                                 {:post/fields
                                  [:field/key :field/content]}])]
                      :in [$ ?slug]
                      :where [[?e :thing/slug ?slug]]}]
                (-> (db/database (handler {:uri "/"}))
                    (db/q query slug)
                    ffirst)))

      ;; Without fields.
      {:db/id 1000
       :thing/slug "hello"}
      ["hello"
       [{:db/id 1000
         :thing/slug "hello"
         :post/fields []}]]

      ;; With two fields in a single tx.
      {:db/id 2000
       :thing/slug "goodbye"
       :post/fields [{:field/key :one :field/content "ONE!"}
                     {:field/key :two :field/content "TWO!"}]}
      ["goodbye"
       [{:db/id 2000
         :thing/slug "goodbye"
         :post/fields [{:field/key :one :field/content "ONE!"}
                       {:field/key :two :field/content "TWO!"}]}]]

      ;; With fields and slug in separate txs.
      {:db/id 3000
       :thing/slug "separate"
       :post/fields [{:field/key :one :field/content "ONE!"}
                     {:field/key :two :field/content "TWO!"}]}
      ["separate"
       [{:db/id 3000
         :thing/slug "separate"}
        {:db/id 3000
         :post/fields [{:field/key :one :field/content "ONE!"}
                       {:field/key :two :field/content "TWO!"}]}]])))
