(ns systems.bread.alpha.query-test
  (:require
    [clojure.test :as t :refer [are deftest is]]
    [kaocha.repl :as k]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defmethod bread/query* ::passthru
  [query _]
  (:v query))

(deftest test-query-expand

  (are
    [data queries]
    (= data (-> (plugins->loaded [(query/plugin)])
                (assoc ::bread/queries queries
                       ;; Assume the first thing in
                       ;; ::queries is for our main key.
                       ::bread/dispatcher
                       {:dispatcher/type :whatevs
                        :dispatcher/key (let [q (first queries)]
                                          (if (map? q)
                                            (:query/key q)
                                            (first q)))})
                (bread/hook ::bread/expand)
                ::bread/data))

    {:my/result "the result"
     :not-found? false}
    [{:query/key :my/result
      :query/name ::passthru
      :v "the result"}]

    {:my/result "the result"
     :not-found? false}
    [{:query/key :my/result
      :query/name ::passthru
      :v "the result"}]

    ;; TODO DELETE THESE vvv

    {:my/result "the result"
     :not-found? false}
    [[:my/result (constantly "the result")]]

    {:my/nums [1 2 3]
     :not-found? false}
    [[:my/nums (fn [_data one two three]
                 [one two three]) 1 2 3]]

    ;; Query expansion is sequential, in the order listed in ::bread/queries.
    {:my/nums [1 2 3 4]
     :my/sum 10
     :not-found? false}
    [[:my/nums (fn [_ & nums] (vec nums)) 1 2 3 4]
     [:my/sum #(reduce + (:my/nums %))]]

    ;; Results of previous queries can be overwritten by subsequent queries.
    {:my/nums 10
     :not-found? false}
    [[:my/nums (constantly [1 2 3 4])]
     [:my/nums #(reduce + (:my/nums %))]]

    {:my/meta-expanded "whoa, meta"
     :not-found? false}
    [[:my/meta-expanded (with-meta {} {`bread/query
                                       (constantly "whoa, meta")})]]

    ;; Keys can be paths (sequences), which are automatically merged into
    ;; the existing ::data tree.
    {:compacted {:compacted/fields [:nested :values]
                 :some :value}
     :not-found? false}
    [[:compacted (constantly {:some :value})]
     [[:compacted :compacted/fields] (constantly [:nested :values])]]

    ;; Qualified keywords are treated as paths IFF the existing ::data tree
    ;; has an associable structure at (keyword (namespace qualified-keyword)).
    {:compacted {:compacted/fields [:nested :values]
                 :some :value}
     :not-found? false}
    [[:compacted (constantly {:some :value})]
     [:compacted/fields (constantly [:nested :values])]]

    ;; Qualified keywords are treated as regular keywords if the thing at
    ;; (keyword (namespace qualified-keyword)) within ::data is not
    ;; associative.
    {:stuff {:some :value}
     :compacted/fields [:nested :values]
     :not-found? false}
    [[:stuff (constantly {:some :value})]
     [:compacted/fields (constantly [:nested :values])]]

    ;; Ditto above.
    {:compacted "a string"
     :compacted/fields [:nested :values]
     :not-found? false}
    [[:compacted (constantly "a string")]
     [:compacted/fields (constantly [:nested :values])]]))

(comment
  (k/run))
