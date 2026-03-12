(ns systems.bread.alpha.internal-query-inference-test
  (:require
    [clojure.test :refer [deftest are is]]
    [com.rpl.specter :as s]
    [systems.bread.alpha.internal.query-inference :as qi]
    [systems.bread.alpha.i18n :as i18n]))

(deftest test-attrs-walker
  ;; Simple keyword kmod
  (are
    [selections data]
    (= selections (s/select (qi/attrs-walker :a :b) data))

    [] {}
    [] []
    [] [[]]
    [] [{}]
    [:A] {:a :A}
    [:A] {:a :A :b {:c :C}}
    [:A :AA] {:a :A :b [{:a :AA}]}
    [:A :AA :AAA] [{:a :A :b [{:a :AA}]} {:b [{:a :AAA}]}]

    ;;
    )

  ;; Passing a set
  (are
    [selections data]
    (= selections (s/select (qi/attrs-walker :a #{:b}) data))

    [] {}
    [] []
    [] [[]]
    [] [{}]
    [:A] {:a :A}
    [:A] {:a :A :b {:c :C}}
    [:A :AA] {:a :A :b [{:a :AA}]}
    [:A :AA :AAA] [{:a :A :b [{:a :AA}]} {:b [{:a :AAA}]}]

    ;;
    ))

(deftest test-binding-clauses
  (are
    [clauses query attr pred]
    (= clauses (qi/binding-clauses query attr pred))

    [] nil nil nil
    [] [] nil nil
    [] {} nil nil
    [] {} :whatever (constantly true)

    ;; no match
    []
    '{:find [(pull ?e [{:ATTR [:x :y]}])]}
    :ATTR
    #(some #{:a} %)

    ;; multiple clauses, still no match
    []
    '{:find [(pull ?e [{:ATTR [:x :y]}]) (pull ?f [{:ATTR [:y :z]}])]}
    :ATTR
    #(some #{:a} %)

    ;; single match
    [{:index 0
      :sym '?e
      :ops [[[0] {:ATTR [:x :y]}]]
      :clause '(pull ?e [{:ATTR [:x :y]}])}]
    '{:find [(pull ?e [{:ATTR [:x :y]}])]}
    :ATTR
    #(some #{:y} %)

    ;; a pull-expr can be a LazySeq, which is not a list!
    [{:index 0
      :sym '?e
      :ops [[[0] {:ATTR [:x :y]}]]
      :clause '(pull ?e [{:ATTR [:x :y]}])}]
    (update-in '{:find [(pull ?e [{:ATTR [:x :y]}])]}
               [:find 0] #(lazy-seq %))
    :ATTR
    #(some #{:y} %)

    ;; single match at non-zero position
    [{:index 0
      :sym '?e
      :ops [[[1] {:ATTR [:x :y]}]]
      :clause '(pull ?e [:other {:ATTR [:x :y]}])}]
    '{:find [(pull ?e [:other {:ATTR [:x :y]}])]}
    :ATTR
    #(some #{:y} %)

    ;; multiple matches in the same clause
    [{:index 0
      :sym '?e
      :ops [[[0] {:ATTR [:x :y]}]
            [[1 :child 0] {:ATTR [:y :z]}]]
      :clause '(pull ?e [{:ATTR [:x :y]}
                         {:child [{:ATTR [:y :z]}]}])}]
    '{:find [(pull ?e [{:ATTR [:x :y]}
                       {:child [{:ATTR [:y :z]}]}])]}
    :ATTR
    #(some #{:y} %)

    ;; match pulls even when they're not the first clause
    [{:index 2
      :sym '?x
      :ops [[[0] {:ATTR [:x :y]}]]
      :clause '(pull ?x [{:ATTR [:x :y]}])}]
    '{:find [?extra ?stuff (pull ?x [{:ATTR [:x :y]}])]}
    :ATTR
    #(some #{:y} %)

    ;; match multiple pulls, with stuff between
    [{:index 1
      :sym '?a
      :ops [[[0 :a 1] {:ATTR [:x :y]}]]
      :clause '(pull ?a [{:a [:aa {:ATTR [:x :y]}]}])}
     {:index 3
      :sym '?b
      :ops [[[0] {:ATTR [:y :z]}]]
      :clause '(pull ?b [{:ATTR [:y :z]}])}]
    '{:find [?stuff
             (pull ?a [{:a [:aa {:ATTR [:x :y]}]}])
             ?between
             (pull ?b [{:ATTR [:y :z]}])]}
    :ATTR
    #(some #{:y} %)

    ;;
    ))

(deftest test-infer-query-bindings
  (are
    [result attr pred query]
    (= result (let [counter (atom 0)]
                (qi/infer-query-bindings attr pred query)))

    {:bindings []} nil nil nil
    {:bindings []} :attr nil {}
    {:bindings []} :attr (constantly false) {}

    {:bindings []}
    :attr (constantly false) {:find []}

    ;; predicate "matches", but no attr present
    {:bindings []}
    :attr (constantly true) {:find []}

    ;; querying for fields with a wildcard binding
    {:bindings [{:binding-sym '?e
                 :attr :thing/fields
                 :entity-index 0
                 :binding-path [0 :thing/fields]
                 :relation [:thing/fields]}]}
    :thing/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:thing/fields [*]}])]
      :in [$ ?slug]
      :where [[?e :thing/slug ?slug]]}

    ;; querying for fields with key & content bindings
    {:bindings [{:binding-sym '?e
                 :attr :thing/fields
                 :entity-index 0
                 :binding-path [0 :thing/fields]
                 :relation [:thing/fields]}]}
    :thing/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:thing/fields [:field/key :field/content]}])]
      :in [$ ?slug]
      :where [[?e :thing/slug ?slug]]}

    ;; when construct returns a vector-style query
    {:bindings [{:binding-sym '?e
                 :attr :thing/fields
                 :entity-index 0
                 :binding-path [0 :thing/fields]
                 :relation [:thing/fields]}]}
    :thing/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:thing/fields [:field/key :field/content]}])]
      :in [$ ?slug]
      :where [[?e :thing/slug ?slug]]}

    ;; querying for a menu with deeply nested fields clause
    {:bindings [{:binding-sym '?e
                 :attr :thing/fields
                 :entity-index 0
                 :binding-path [0 :menu/items
                                0 :menu.item/entity
                                0 :thing/fields]
                 :relation [:menu/items
                            :menu.item/entity
                            :thing/fields]}]}
    :thing/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:menu/items
                        [{:menu.item/entity
                          [{:thing/fields
                            [:field/key :field/content]}]}]}])]
      :in [$ ?menu-key]
      :where [[?e :menu/key ?menu-key]]}

    ;; querying for menu item AND entity fields...
    {:bindings [{:binding-sym '?e
                 :attr :thing/fields
                 :entity-index 0
                 :binding-path [0 :menu.item/entity
                                0 :thing/fields]
                 :relation [:menu.item/entity
                            :thing/fields]}
                {:binding-sym '?e
                 :attr :thing/fields
                 :entity-index 0
                 :binding-path [1 :thing/fields]
                 :relation [:thing/fields]}]}
    :thing/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:menu.item/entity
                        [{:thing/fields
                          [:field/key :field/content]}]}
                       {:thing/fields
                        [:field/key :field/content]}])]
      :in [$ ?menu-key]
      :where [[?e :menu/key ?menu-key]]}

    ;; Finding recursive bindings with a keyword predicate.
    {:bindings [{:binding-sym '?e
                 :attr :thing/children
                 :entity-index 0
                 :binding-path [1 :thing/children]
                 :relation [:thing/children]}]}
    keyword? ;; any keyword key
    (fn [b]
      (or (= '... b) (integer? b)))
    '{:find [(pull ?e [{:thing/fields [:field/key :field/content]}
                       {:thing/children ...}])]
      :in [$ ?menu-key]
      :where [[?e :menu/key ?menu-key]]}

    ;;
    ))

(deftest test-relation->spath
  (is (= [] (qi/relation->spath nil nil)))
  (is (= [] (qi/relation->spath nil ())))
  (is (= [] (qi/relation->spath nil [])))
  (is (= [] (qi/relation->spath {} [])))
  (is (= [:x :y :z] (qi/relation->spath {} (list :x :y :z))))
  (is (= [:x :y :z] (qi/relation->spath {} [:x :y :z])))

  (is (= [:x :y :z]
         (qi/relation->spath {:x {:db/cardinality :db.cardinality/one}}
                             [:x :y :z])))

  (is (= [:x s/ALL :y :z]
         (qi/relation->spath {:x {:db/cardinality :db.cardinality/many}}
                             [:x :y :z])))

  (is (= [:x s/ALL :y s/ALL :z]
         (qi/relation->spath {:x {:db/cardinality :db.cardinality/many}
                              :y {:db/cardinality :db.cardinality/many}}
                             [:x :y :z])))

  ;; if (get-at entity spath) is a collection, we want to be able to operate
  ;; on it directly, so don't return a Specter operator for the last attr in
  ;; the relation even if it's *-cardinality.
  (is (= [:x s/ALL :y s/ALL :z]
         (qi/relation->spath {:x {:db/cardinality :db.cardinality/many}
                              :y {:db/cardinality :db.cardinality/many}
                              :z {:db/cardinality :db.cardinality/many}}
                             [:x :y :z])))

  ;; Assume :db.cardinality/many means *-* by default
  ;; TODO make this more configurable
  (is (= [:x/_y s/ALL :z]
      (qi/relation->spath {:x/y {:db/cardinality :db.cardinality/many}}
                          [:x/_y :z])))

  ;;
  )

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
