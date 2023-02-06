(ns systems.bread.alpha.internal-query-inference-test
  (:require
    [clojure.test :refer [deftest are is]]
    [systems.bread.alpha.internal.query-inference :as i]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]))

(deftest test-binding-pairs
  (are
    [pairs args]
    (= pairs (apply i/binding-pairs args))

    []
    [[] :query-key []]

    []
    [{} :query-key []]

    []
    [[:a/b :c/d] :query-key [:db/id]]

    []
    [[:a/b :c/d] :query-key [:db/id {:x/y [:db/id]}]]

    []
    [{:a/b any? :c/d any?} :query-key [:db/id {:x/y [:db/id]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [[:a/b :c/d] :query-key [:db/id {:a/b [:db/id]}]]

    [[{:a/b [:db/id]} [:nested :key :a/b]]]
    [[:a/b :c/d] [:nested :key] [:db/id {:a/b [:db/id]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [{:a/b any?} :query-key [:db/id {:a/b [:db/id]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [{:a/b #(= % {:a/b [:db/id]})} :query-key [:db/id {:a/b [:db/id]}]]))

(deftest test-infer
  (are
    [queries args]
    (= queries (apply i/infer args))

    [{}]
    [{} nil #()]

    [{}]
    [{} {} #()]

    [{:query/args ['{:find [(pull ?e [:db/id])]}]}]
    [{:query/args ['{:find [(pull ?e [:db/id])]}]}
     {} #()]

    [{:query/args ['{:find [(pull ?e [:db/id :a/b])]}]
      :query/key :the-key}
     {:query/args ['{:find [(pull ?e [:b/c :b/d])]}]
      :query/key [:the-key :a/b]}]
    [{:query/args ['{:find [(pull ?e [:db/id {:a/b [:b/c :b/d]}])]}]
      :query/key :the-key}
     [:a/b]
     (fn construct-ab-query [{k :query/key :as query} spec path]
       (let [new-spec (get spec (last path))]
         {:query/args [{:find [(list 'pull '?e new-spec)]}]
          :query/key path}))]

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
