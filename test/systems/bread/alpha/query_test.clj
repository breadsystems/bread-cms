(ns systems.bread.alpha.query-test
  (:require
    [clojure.test :as t :refer [are deftest is]]
    [kaocha.repl :as k]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-query-expand

  (are
    [data queries]
    (= data (-> (plugins->loaded [(query/plugin)])
                (assoc ::bread/queries queries
                       ::bread/dispatcher
                       {:dispatcher/type :whatevs
                        :dispatcher/key (:query/key (first queries))})
                (bread/hook ::bread/expand)
                ::bread/data))

    {:my/result "the result"
     :not-found? false}
    [{:query/key :my/result
      :query/name ::bread/value
      :value "the result"}]

    {:my/result "the result"
     :not-found? false}
    [{:query/key :my/result
      :query/name ::bread/value
      :value "the result"}]

    {:my/map {:my/result "the result"}
     :not-found? false}
    [{:query/key :my/map
      :query/name ::bread/value
      :value {}}
     {:query/key [:my/map :my/result]
      :query/name ::bread/value
      :value "the result"}]))

(comment
  (k/run))
