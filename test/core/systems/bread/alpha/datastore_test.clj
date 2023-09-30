(ns systems.bread.alpha.datastore-test
  (:require
    [kaocha.repl :as k]
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]])
  (:import
    [clojure.lang ExceptionInfo]))


(deftest test-connect

  (testing "it gives a friendly error message if you forget :datastore/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"No :datastore/type specified in datastore config!"
          (store/connect {:datastore/typo :datahike}))))

  (testing "it gives a friendly error message if you pass a bad :datastore/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Unknown :datastore/type `:oops`! Did you forget to load a plugin\?"
          (store/connect {:datastore/type :oops})))))

(deftest test-add-txs-adds-an-effect
  (let [conn {:fake :db}]
    (are
      [effects args]
      (= effects (let [app (plugins->loaded
                             [{:config
                               {:datastore/connection conn}}])]
                   (::bread/effects (apply store/add-txs app args))))

      [{:effect/name ::store/transact
        :effect/description "Run database transactions"
        :effect/key nil
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions]]

      [{:effect/name ::store/transact
        :effect/description "Custom description"
        :effect/key nil
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions] {:description "Custom description"}]

      [{:effect/name ::store/transact
        :effect/description "Custom description"
        :effect/key 1234
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions] {:description "Custom description"
                                    :key 1234}])))

(comment
  (k/run))
