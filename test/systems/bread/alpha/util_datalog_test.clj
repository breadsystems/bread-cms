(ns systems.bread.alpha.util-datalog-test
  (:require
    [clojure.test :refer [deftest are is]]
    [systems.bread.alpha.util.datalog :as d]
    [systems.bread.alpha.i18n :as i18n]))

(deftest test-reverse-relation
  (is (= :a/b (d/reverse-relation :a/_b)))
  (is (= :a/_b (d/reverse-relation :a/b))))

(deftest test-extract-pull
  (is (= [:db/id :post/slug]
         (d/extract-pull
           {:query/args [{:find ['(pull ?e [:db/id :post/slug])]}]}))))

(deftest test-binding-pairs
  (are
    [pairs args]
    (= pairs (apply d/binding-pairs args))

    []
    [[] :query-key []]

    []
    [[:a/b :c/d] :query-key [:db/id]]

    []
    [[:a/b :c/d] :query-key [:db/id {:x/y [:db/id]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [[:a/b :c/d] :query-key [:db/id {:a/b [:db/id]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [{:a/b any?} :query-key [:db/id {:a/b [:db/id]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [{:a/b #(= % {:a/b [:db/id]})} :query-key [:db/id {:a/b [:db/id]}]]))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
