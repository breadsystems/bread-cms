(ns systems.bread.alpha.expansion-test
  (:require
    [clojure.test :as t :refer [are deftest]]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(defmethod bread/expand ::higher-order
  [{:as expansion :keys [numbers max-len]} _]
  (let [numbers (conj numbers (count numbers))]
    (if (>= (count numbers) max-len)
      numbers
      {::bread/expansions [(assoc expansion :numbers numbers)]})))

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

    ;; Arbitrary functions are supported via ::bread/fn.
    {:my/result {:nested "some value"}
     :not-found? false}
    [{:expansion/key :my/result
      :expansion/name ::bread/value
      :expansion/value "some value"}
     {:expansion/name ::bread/fn
      :expansion/key :my/result
      :expansion/f (fn nest [v] {:nested v})}]

    ;; Any arity-1 function is OK.
    {:my/result 3
     :not-found? false}
    [{:expansion/key :my/result
      :expansion/name ::bread/value
      :expansion/value {:init 2}}
     {:expansion/name ::bread/fn
      :expansion/key :my/result
      :expansion/f (comp inc :init)}]

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

    {:numbers [0]
     :not-found? false}
    [{:expansion/key :numbers
      :expansion/name ::higher-order
      :numbers []
      :max-len 1}]

    {:numbers [0 1]
     :not-found? false}
    [{:expansion/key :numbers
      :expansion/name ::higher-order
      :numbers []
      :max-len 2}]

    {:numbers [0 1 2]
     :not-found? false}
    [{:expansion/key :numbers
      :expansion/name ::higher-order
      :numbers []
      :max-len 3}]

    ,))

(defonce state (atom 0))

(defmethod bread/effect ::tick! [_ _]
  (swap! state inc))

(defmethod bread/expand ::tick [expansion _]
  {::bread/effects [{:effect/name ::tick!
                     :effect/key (:effect/key expansion)}]})

(defmethod bread/effect ::initialize! [_ {:keys [initial]}]
  (when initial
    (reset! state initial)))

(defmethod bread/effect ::evaluate! [{:keys [op x]} _]
  (swap! state op x))

(defmethod bread/expand ::evaluate [{:keys [initial op x k]} _]
  {;; First, (assoc data :initial initial) ...
   ::bread/expansions [(when initial {:expansion/key :initial
                                      :expansion/name ::bread/value
                                      :expansion/value initial})]
   ;; ...and now our effect can consume (:initial data)
   ::bread/effects [(when initial {:effect/name ::initialize!})
                    {:effect/name ::evaluate!
                     :effect/key k
                     :op op
                     :x x}]})

(defmethod bread/expand ::downstream [{:keys [from]} data]
  (let [x (get data from)
        x' (inc x)]
    (format "%d + 1 = %d" x x')))

(deftest test-expansion-that-returns-effects
  (are
    [data expansions]
    (= data (do
              (reset! state 0)
              (-> (plugins->loaded [(expansion/plugin {:expand-not-found? false})])
                  (assoc ::bread/expansions expansions)
                  (bread/hook ::bread/expand)
                  ::bread/data)))

    {}
    [{:expansion/name ::tick}]

    {:tick 1}
    [{:expansion/name ::tick :effect/key :tick}]

    {:tick-one 1 :tick-two 2}
    [{:expansion/name ::tick :effect/key :tick-one}
     {:expansion/name ::tick :effect/key :tick-two}]

    ;; Only effects with keys are persisted.
    {:tick-two 2}
    [{:expansion/name ::tick}
     {:expansion/name ::tick :effect/key :tick-two}]

    ;; Only effects with keys are persisted.
    {:tick-two 2}
    [{:expansion/name ::tick}
     {:expansion/name ::tick :effect/key :tick-two}]

    ;; Effects can depend on prior expansions.
    {:initial 4 :sum 7}
    [{:expansion/name ::evaluate :initial 4 :op + :x 3 :k :sum}]

    ;; Higher-order expansions can interleave with effects.
    {:initial 4 :sum 7 :product 14 :difference 13}
    [{:expansion/name ::evaluate :initial 4 :op + :x 3 :k :sum}
     {:expansion/name ::evaluate :op * :x 2 :k :product}
     {:expansion/name ::evaluate :op - :x 1 :k :difference}]

    ;; Interleaving works without intermediate keys.
    {:initial 4 :difference 13}
    [{:expansion/name ::evaluate :initial 4 :op + :x 3} ;; no :k
     {:expansion/name ::evaluate :op * :x 2}            ;; no :k
     {:expansion/name ::evaluate :op - :x 1 :k :difference}]

    ;; Recursion is depth-first. This allows expansions to consume the results of
    ;; upstream effects.
    {:initial 4 :product 14 :downstream "14 + 1 = 15"}
    [{:expansion/name ::evaluate :initial 4 :op + :x 3}
     {:expansion/name ::evaluate :op * :x 2 :k :product}
     {:expansion/name ::downstream :expansion/key :downstream :from :product}]

    ,))

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
