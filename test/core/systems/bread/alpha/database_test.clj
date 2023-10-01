(ns systems.bread.alpha.database-test
  (:require
    [kaocha.repl :as k]
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]])
  (:import
    [clojure.lang ExceptionInfo]))


(deftest test-connect

  (testing "it gives a friendly error message if you forget :db/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"No :db/type specified in database config!"
          (db/connect {:db/typo :datahike}))))

  (testing "it gives a friendly error message if you pass a bad :db/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Unknown :db/type `:oops`! Did you forget to load a plugin\?"
          (db/connect {:db/type :oops})))))

(deftest test-add-txs-adds-an-effect
  (let [conn {:fake :db}]
    (are
      [effects args]
      (= effects (let [app (plugins->loaded [{:config {:db/connection conn}}])]
                   (::bread/effects (apply db/add-txs app args))))

      [{:effect/name ::db/transact
        :effect/description "Run database transactions"
        :effect/key nil
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions]]

      [{:effect/name ::db/transact
        :effect/description "Custom description"
        :effect/key nil
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions] {:description "Custom description"}]

      [{:effect/name ::db/transact
        :effect/description "Custom description"
        :effect/key 1234
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions] {:description "Custom description"
                                    :key 1234}])))

(comment
  (k/run))
