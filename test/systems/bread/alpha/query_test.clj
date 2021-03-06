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

    ;; Results of previous queries can be overwritten by subsequent queries.
    {:my/nums 10}
    [[:my/nums (constantly [1 2 3 4])]
     [:my/nums #(reduce + (:my/nums %))]]

    {:my/meta-expanded "whoa, meta"}
    [[:my/meta-expanded (with-meta {} {`bread/query
                                       (constantly "whoa, meta")})]]

    ;; Keys can be paths (sequences), which are automatically merged into
    ;; the existing ::data tree.
    {:compacted {:compacted/fields [:nested :values]
                 :some :value}}
    [[:compacted (constantly {:some :value})]
     [[:compacted :compacted/fields] (constantly [:nested :values])]]

    ;; Qualified keywords are treated as paths IFF the existing ::data tree
    ;; has an associable structure at (keyword (namespace qualified-keyword)).
    {:compacted {:compacted/fields [:nested :values]
                 :some :value}}
    [[:compacted (constantly {:some :value})]
     [:compacted/fields (constantly [:nested :values])]]

    ;; Qualified keywords are treated as regular keywords if the thing at
    ;; (keyword (namespace qualified-keyword)) within ::data is not
    ;; associative.
    {:stuff {:some :value}
     :compacted/fields [:nested :values]}
    [[:stuff (constantly {:some :value})]
     [:compacted/fields (constantly [:nested :values])]]

    ;; Ditto above.
    {:compacted "a string"
     :compacted/fields [:nested :values]}
    [[:compacted (constantly "a string")]
     [:compacted/fields (constantly [:nested :values])]]))
