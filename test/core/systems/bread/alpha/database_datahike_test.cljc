;; Tests for the low-level functionality of the Datahike database integration.
(ns systems.bread.alpha.database-datahike-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [datahike.db]
    [systems.bread.alpha.database :as db]))

;; Set up a bunch of boilerplate to share between tests.
(let [config {:db/type :datahike
              :db/config {:store {:backend :mem :id "testdb"}
                          :initial-tx [{:db/ident :name
                                        :db/valueType :db.type/string
                                        :db/unique :db.unique/identity
                                        :db/index true
                                        :db/cardinality :db.cardinality/one}
                                       {:db/ident :age
                                        :db/valueType :db.type/number
                                        :db/cardinality :db.cardinality/one}]}}

      datahike-fixture (fn [f]
                         ;; Clean up after any prior failures, just in case.
                         (db/delete! config)
                         (db/create! config)
                         (f)
                         ;; Eagerly clean up after ourselves.
                         (db/delete! config))

      angela {:name "Angela" :age 76}
      bobby {:name "Bobby" :age 84}
      query-all '[:find ?n ?a
                  :where
                  [?e :name ?n]
                  [?e :age ?a]]
      init-db (fn []
                (let [conn (db/connect config)]
                  (db/transact conn [angela bobby])
                  conn))]

  ;; Start each test with a blank-slate database.
  (use-fixtures :each datahike-fixture)

  (deftest test-q

    (let [conn (init-db)]
      (is (= #{["Angela" 76] ["Bobby" 84]}
             (db/q @conn query-all)))))

  (deftest test-pull

    (let [conn (init-db)]
      (is (= {:name "Angela" :age 76 :db/id 3}
             (db/pull @conn '[*] [:name "Angela"])))))

  (deftest test-transact

    (let [conn (init-db)
          result (db/transact conn [{:db/id [:name "Angela"] :age 99}])]
      (is (instance? datahike.db.TxReport result))))

  (deftest test-as-of

    (let [conn (init-db)
          {{tx :max-tx} :db-before}
          (db/transact conn [{:db/id [:name "Angela"] :age 77}])]
      ;; Happy birthday, Angela!
      (is (= #{["Angela" 77] ["Bobby" 84]}
             (db/q @conn query-all)))
      ;; As of tx
      (is (= #{["Angela" 76] ["Bobby" 84]}
             (db/q (db/as-of @conn tx) query-all)))

      (testing "it composes with pull"
        (let [db (db/as-of @conn tx)]
          (is (= 76 (:age (db/pull db '[:age] [:name "Angela"]))))))))

  (deftest test-history

    (let [conn (init-db)
          query-ages '[:find ?a
                       :where
                       [?e :age ?a]
                       [?e :name "Angela"]]]
      (db/transact conn [{:db/id [:name "Angela"] :age 77}])
      (db/transact conn [{:db/id [:name "Angela"] :age 78}])
      (is (= #{[76] [77] [78]}
             (db/q (db/history @conn) query-ages)))))

  (deftest test-db-with

    (let [conn (init-db)
          db (db/db-with @conn [{:db/id [:name "Angela"] :age 77}])]
      (is (= {:name "Angela" :age 77 :db/id 3}
             (db/pull db '[*] [:name "Angela"])))

      (testing "it composes with pull"
        (let [with-db (db/db-with @conn
                                     [{:db/id [:name "Angela"] :age 77}])]
          (is (= 77 (:age (db/pull with-db '[:age] [:name "Angela"])))))))))
