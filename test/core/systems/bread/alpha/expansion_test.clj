(ns systems.bread.alpha.expansion-test
  (:require
    [clojure.test :as t :refer [are deftest is]]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-expand
  (are
    [data expansions]
    (= data (-> (plugins->loaded [(expansion/plugin)])
                (assoc ::bread/expansions expansions
                       ::bread/dispatcher
                       {:dispatcher/type :whatevs
                        :dispatcher/key (:expansion/key (first expansions))})
                (bread/hook ::bread/expand)
                ::bread/data))

    {:not-found? nil} []
    {:not-found? nil} [nil]
    {:not-found? nil} [nil nil]
    {:not-found? nil} [nil false]

    {:my/result false
     :not-found? true}
    [{:expansion/key :my/result
      :expansion/name ::bread/value
      :expansion/value false}]

    {:my/result {:nested false}
     :not-found? true}
    [{:expansion/key [:my/result :nested]
      :expansion/name ::bread/value
      :expansion/value false}]

    {:my/result "the result"
     :not-found? false}
    [{:expansion/key :my/result
      :expansion/name ::bread/value
      :expansion/value "the result"}]

    {:my/result "the result"
     :not-found? false}
    [{:expansion/key :my/result
      :expansion/name ::bread/value
      :expansion/value "the result"}]

    {:my/map {:my/result "the result"}
     :not-found? false}
    [{:expansion/key :my/map
      :expansion/name ::bread/value
      :expansion/value {}}
     {:expansion/key [:my/map :my/result]
      :expansion/name ::bread/value
      :expansion/value "the result"}]))

(deftest test-populate-in
  (are
    [data args]
    (= data (apply expansion/populate-in args))

    {:x :y}
    [{} :x :y]

    {:a :b :x :y}
    [{:a :b} :x :y]

    {:a :b :def :xyz}
    [{:a :b} [:def] :xyz]

    ;; Don't overwrite false values...
    {:a :b :x false}
    [{:a :b :x false} [:x :y] :xyz]

    ;; ...at any level.
    {:a :b :x {:y false}}
    [{:a :b :x {:y false}} [:x :y :z] :xyz]

    ;; And I mean ANY LEVEL.
    {:a :b :x {:y {:z false}}}
    [{:a :b :x {:y {:z false}}} [:x :y :z :a] :xyz]

    {:a {:b {:c :d}}}
    [{:a {:b {}}} [:a :b] {:c :d}]

    {:posts [{:post/fields [{:db/id 123
                             :field/key :one
                             :field/content "one"}]}
             {:post/fields [{:db/id 456
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

    {:posts [{:post/fields [nil]}
             {:post/fields [nil]}]}
    [{:posts [{:post/fields [{:db/id 123}]}
              {:post/fields [{:db/id 456}]}]}
     [:posts :post/fields]
     []]

    ;; Value being overwritten at k maybe be sequential, but v may be falsey.
    ;; In this case, we just want to short-circuit and keep whatever is there.
    {:posts [{:db/id 1 :post/fields [{:db/id 123}]}
             {:db/id 2 :post/fields [{:db/id 456}]}]}
    [{:posts [{:db/id 1 :post/fields [{:db/id 123}]}
              {:db/id 2 :post/fields [{:db/id 456}]}]}
     [:posts :post/fields]
     false]

    ;; Ditto above.
    {:posts [{:db/id 1 :post/fields [{:db/id 123}]}
             {:db/id 2 :post/fields [{:db/id 456}]}]}
    [{:posts [{:db/id 1 :post/fields [{:db/id 123}]}
              {:db/id 2 :post/fields [{:db/id 456}]}]}
     [:posts :post/fields]
     nil]

    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
