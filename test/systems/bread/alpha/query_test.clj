(ns systems.bread.alpha.query-test
  (:require
    [clojure.test :refer [are deftest is]]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-query-expand
  (are [data queries] (= data (-> (plugins->loaded [(query/plugin)])
                                  (assoc ::bread/queries queries)
                                  (bread/hook :hook/expand)
                                  ::bread/data))

    {:my/result "the result"}
    [[:my/result (constantly "the result")]]

    {:my/nums [1 2 3]}
    [[:my/nums (fn [_data one two three]
                 [one two three]) 1 2 3]]

    ;; Query expansion is sequential, in the order listed in ::bread/queries.
    {:my/nums [1 2 3 4]
     :my/sum 10}
    [[:my/nums (fn [_ & nums] (vec nums)) 1 2 3 4]
     [:my/sum #(reduce + (:my/nums %))]]

    {:my/meta-expanded "whoa, meta"}
    [[:my/meta-expanded (with-meta {} {`bread/query
                                       (constantly "whoa, meta")})]]

       ))
