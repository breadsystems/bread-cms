(ns systems.bread.alpha.datastore-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [systems.bread.alpha.datastore :as d]))


(deftest test-map-datastore

  (testing "it implements get-key"
    (is (= :my/value
           (d/get-key {:my/key :my/value} :my/key))))

  (testing "it implements set-key"
    (is (= {:a :b :my/key :my/value}
           (d/set-key {:a :b} :my/key :my/value))))

  (testing "it implements delete-key"
    (is (= {:a :b}
           (d/delete-key {:a :b :my/key :my/value} :my/key)))))

(deftest test-atom-datastore

  (testing "it implements get-key"
    (let [store (atom {:my/key :my/value})]
      (is (= :my/value
             (d/get-key store :my/key)))))
  
  (testing "it implements set-key"
    (let [store (atom {:a :b})]
      (d/set-key store :my/key :my/value)
      (is (= {:a :b :my/key :my/value}
             @store))))
  
  (testing "it implements delete-key"
    (let [store (atom {:a :b :my/key :my/value})]
      (d/delete-key store :my/key)
      (is (= {:a :b}
             @store)))))