(ns systems.bread.alpha.util-datalog-test
  (:require
    [clojure.test :refer [deftest are is]]
    [systems.bread.alpha.util.datalog :as d]
    [systems.bread.alpha.i18n :as i18n]))

(deftest test-normalize-query
  (are
    [normalized query] (= normalized (d/normalize-query query))

    {} {}
    {} []

    '{:find [?e] :in [$] :where [[?e :attr]]}
    '[:find ?e :in $ :where [?e :attr]]

    '{:find [?e] :in [$ ?attr] :where [[?e :attr ?attr]]}
    '[:find ?e :in $ ?attr :where [?e :attr ?attr]]

    '{:find [?e] :where [[?e :attr]]}
    '[:find ?e :where [?e :attr]]

    ;;
    ))

(deftest test-relation-reversed?
  (is (false? (d/relation-reversed? :a/b)))
  (is (true? (d/relation-reversed? :a/_b))))

(deftest test-reverse-relation
  (is (= :a/b (d/reverse-relation :a/_b)))
  (is (= :a/_b (d/reverse-relation :a/b))))

(deftest test-query-syms
  (is (= [] (d/query-syms 0)))
  (is (= ['?e0] (d/query-syms 1)))
  (is (= ['?e0 '?e1] (d/query-syms 2)))
  (is (= ['?e0 '?e1 '?e2] (d/query-syms 3)))
  (is (= ['?e1 '?e2 '?e3] (d/query-syms 1 3)))
  (is (= ['?x1 '?x2 '?x3] (d/query-syms "?x" 1 3))))

(deftest test-ensure-db-id
  (are
    [expected pull] (= expected (d/ensure-db-id pull))
    ['*] ['*]
    [:db/id] []
    [:db/id] [:db/id]
    [:db/id :xyz] [:xyz]
    [:db/id :xyz] [:db/id :xyz]))

(deftest test-ensure-attrs
  (are
    [expected attrs pull] (= expected (d/ensure-attrs attrs pull))
    ['*] [:db/id] ['*]
    ['*] [:db/id :abc] ['*]
    ['*] [:db/id :abc :xyz] ['*]
    [:db/id] [:db/id] []
    [:abc :db/id] [:db/id :abc] []
    [:db/id :abc] [:db/id] [:abc]
    [:db/id :abc :xyz] [:db/id] [:abc :xyz]))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
