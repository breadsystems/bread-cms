(ns systems.bread.alpha.util-datalog-test
  (:require
    [clojure.test :refer [deftest are is]]
    [systems.bread.alpha.util.datalog :as d]
    [systems.bread.alpha.i18n :as i18n]))

(deftest test-relation-reversed?
  (is (false? (d/relation-reversed? :a/b)))
  (is (true? (d/relation-reversed? :a/_b))))

(deftest test-reverse-relation
  (is (= :a/b (d/reverse-relation :a/_b)))
  (is (= :a/_b (d/reverse-relation :a/b))))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
