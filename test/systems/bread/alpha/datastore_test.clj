(ns systems.bread.alpha.datastore-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as d])
  (:import
    [clojure.lang ExceptionInfo]))


(deftest test-connect!

  (testing "it gives a friendly error message if you forget :datastore/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"No :datastore/type specified in datastore config!"
          (d/connect! {:datastore/typo :datahike}))))

  (testing "it gives a friendly error message if you pass a bad :datastore/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Unknown :datastore/type `:oops`! Did you forget to load a plugin\?"
          (d/connect! {:datastore/type :oops})))))
