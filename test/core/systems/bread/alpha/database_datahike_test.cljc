;; Tests for the low-level functionality of the Datahike datastore integration.
(ns systems.bread.alpha.database-datahike-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [datahike.db]
    [systems.bread.alpha.database :as store]))

;; Set up a bunch of boilerplate to share between tests.
(let [config {:db/type :datahike
              :store {:backend :mem :id "testdb"}
              :initial-tx [{:db/ident :name
                            :db/valueType :db.type/string
                            :db/unique :db.unique/identity
                            :db/index true
                            :db/cardinality :db.cardinality/one}
                           {:db/ident :age
                            :db/valueType :db.type/number
                            :db/cardinality :db.cardinality/one}]}

      datahike-fixture (fn [f]
                         ;; Clean up after any prior failures, just in case.
                         (store/delete! config)
                         (store/create! config)
                         (f)
                         ;; Eagerly clean up after ourselves.
                         (store/delete! config))

      angela {:name "Angela" :age 76}
      bobby {:name "Bobby" :age 84}
      query-all '[:find ?n ?a
                  :where
                  [?e :name ?n]
                  [?e :age ?a]]
      init-db (fn []
                (let [conn (store/connect config)]
                  (store/transact conn [angela bobby])
                  conn))]

  ;; Start each test with a blank-slate database.
  (use-fixtures :each datahike-fixture)

  (deftest test-q

    (let [conn (init-db)]
      (is (= #{["Angela" 76] ["Bobby" 84]}
             (store/q @conn query-all)))))

  (deftest test-pull

    (let [conn (init-db)]
      (is (= {:name "Angela" :age 76 :db/id 3}
             (store/pull @conn '[*] [:name "Angela"])))))

  (deftest test-transact

    (let [conn (init-db)
          result (store/transact conn [{:db/id [:name "Angela"] :age 99}])]
      (is (instance? datahike.db.TxReport result))))

  (deftest test-as-of

    (let [conn (init-db)
          {{tx :max-tx} :db-before}
          (store/transact conn [{:db/id [:name "Angela"] :age 77}])]
      ;; Happy birthday, Angela!
      (is (= #{["Angela" 77] ["Bobby" 84]}
             (store/q @conn query-all)))
      ;; As of tx
      (is (= #{["Angela" 76] ["Bobby" 84]}
             (store/q (store/as-of @conn tx) query-all)))

      (testing "it composes with pull"
        (let [db (store/as-of @conn tx)]
          (is (= 76 (:age (store/pull db '[:age] [:name "Angela"]))))))))

  (deftest test-history

    (let [conn (init-db)
          query-ages '[:find ?a
                       :where
                       [?e :age ?a]
                       [?e :name "Angela"]]]
      (store/transact conn [{:db/id [:name "Angela"] :age 77}])
      (store/transact conn [{:db/id [:name "Angela"] :age 78}])
      (is (= #{[76] [77] [78]}
             (store/q (store/history @conn) query-ages)))))

  (deftest test-db-with

    (let [conn (init-db)
          db (store/db-with @conn [{:db/id [:name "Angela"] :age 77}])]
      (is (= {:name "Angela" :age 77 :db/id 3}
             (store/pull db '[*] [:name "Angela"])))

      (testing "it composes with pull"
        (let [with-db (store/db-with @conn
                                     [{:db/id [:name "Angela"] :age 77}])]
          (is (= 77 (:age (store/pull with-db '[:age] [:name "Angela"])))))))))
