(ns systems.bread.alpha.query-test
  (:require
    [clojure.test :as t :refer [are deftest is]]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

;; TODO what is this even testing??
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
      :query/value "the result"}]

    {:my/result "the result"
     :not-found? false}
    [{:query/key :my/result
      :query/name ::bread/value
      :query/value "the result"}]

    {:my/map {:my/result "the result"}
     :not-found? false}
    [{:query/key :my/map
      :query/name ::bread/value
      :query/value {}}
     {:query/key [:my/map :my/result]
      :query/name ::bread/value
      :query/value "the result"}]))

(deftest test-populate-in
  (are
    [data args]
    (= data (apply query/populate-in args))

    {:x :y}
    [{} :x :y]

    {:a :b :x :y}
    [{:a :b} :x :y]

    ;; Non-existent paths DO NOT get updated.
    {:a :b}
    [{:a :b} [:def] :xyz]

    {:a {:b {:c :d}}}
    [{:a {:b {}}} [:a :b] {:c :d}]

    {:posts [{:post/fields [ {:db/id 123
                            :field/key :one
                            :field/content "one"}]}
             {:post/fields [ {:db/id 456
                            :field/key :two
                            :field/content "two"}]}]}
    [{:posts [{:post/fields [{:db/id 123}]}
              {:post/fields [{:db/id 456}]}]}
     [:posts :post/fields]
     [{:db/id 123
       :field/key :one
       :field/content "one"}
      {:db/id 456
       :field/key :two
       :field/content "two"}]]

    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
