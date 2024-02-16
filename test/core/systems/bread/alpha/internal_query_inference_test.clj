(ns systems.bread.alpha.internal-query-inference-test
  (:require
    [clojure.test :refer [deftest are is]]
    [com.rpl.specter :as s]
    [systems.bread.alpha.internal.query-inference :as qi]
    [systems.bread.alpha.i18n :as i18n]))

(deftest test-binding-pairs
  (are
    [pairs args]
    (= pairs (apply qi/binding-pairs args))

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

    [[{:a/b [:b/x :b/y]} [:nested :key :a/b]]
     [{:c/d [:d/x :d/y]} [:nested :key :c/d]]]
    [[:a/b :c/d] [:nested :key] [:db/id
                                 {:a/b [:b/x :b/y]}
                                 {:c/d [:d/x :d/y]}]]

    [[{:a/b [:b/x :b/y]} [:query-key :a/b]]
     [{:c/d [:d/x :d/y]} [:query-key :c/d]]]
    [{:a/b any? :c/d any?} :query-key [:db/id
                                       {:a/b [:b/x :b/y]}
                                       {:c/d [:d/x :d/y]}]]

    [[{:a/b [:b/x :b/y {:b/c [:c/d]}]} [:query-key :a/b]]
     [{:b/c [:c/d]} [:query-key :a/b :b/c]]]
    [{:a/b any? :b/c any?} :query-key [:db/id
                                       {:a/b [:b/x :b/y {:b/c [:c/d]}]}]]

    [[{:a/b [:db/id]} [:query-key :a/b]]]
    [{:a/b #(= % {:a/b [:db/id]})} :query-key [:db/id {:a/b [:db/id]}]]))

(deftest test-infer
  (are
    [queries args]
    (= queries (apply qi/infer args))

    []
    [[] nil #()]

    [{}]
    [[{}] {} #()]

    [{:query/args ['{:find [(pull ?e [:db/id])]}]}]
    [[{:query/args ['{:find [(pull ?e [:db/id])]}]}]
     {} #()]

    [{:query/args ['{:find [(pull ?e [:db/id :a/b])]}]
      :query/key :the-key}
     {:query/args ['{:find [(pull ?e [:b/c :b/d])]}]
      :query/key [:the-key :a/b]}]
    [[{:query/args ['{:find [(pull ?e [:db/id {:a/b [:b/c :b/d]}])]}]
       :query/key :the-key}]
     [:a/b]
     (fn construct-ab-query [{k :query/key :as query} spec path]
       (let [new-spec (get spec (last path))]
         {:query/args [{:find [(list 'pull '?e new-spec)]}]
          :query/key path}))]

    ;; With multiple, arbitrarily nested instances of attr :a/b
    [{:query/args ['{:find [(pull ?e [:db/id
                                      :a/b
                                      {:e/f [:db/id
                                             :a/b]}])]}]
      :query/key :the-key}
     {:query/args ['{:find [(pull ?e [*])]}]
      :query/key [:the-key :a/b]}
     {:query/args ['{:find [(pull ?e [*])]}]
      :query/key [:the-key :e/f :a/b]}]
    [[{:query/args ['{:find [(pull ?e [:db/id
                                       {:a/b [*]}
                                       {:e/f [:db/id
                                              {:a/b [*]}]}])]}]
       :query/key :the-key}]
     [:a/b]
     (fn construct-ab-query [{k :query/key :as query} spec path]
       (let [new-spec (get spec (last path))]
         {:query/args [{:find [(list 'pull '?e new-spec)]}]
          :query/key path}))]

    ;;
    ))

;; TODO DELETE ABOVE

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
    (= result (let [counter (atom 0)
                    gensym* (fn [prefix]
                              (symbol (str prefix (swap! counter inc))))]
                (with-redefs [gensym gensym*]
                  (qi/infer-query-bindings attr pred query))))

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
                 :attr :translatable/fields
                 :entity-index 0
                 :relation [:translatable/fields]}]}
    :translatable/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:translatable/fields [*]}])]
      :in [$ ?slug]
      :where [[?e :post/slug ?slug]]}

    ;; querying for fields with key & content bindings
    {:bindings [{:binding-sym '?e
                 :attr :translatable/fields
                 :entity-index 0
                 :relation [:translatable/fields]}]}
    :translatable/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:translatable/fields [:field/key :field/content]}])]
      :in [$ ?slug]
      :where [[?e :post/slug ?slug]]}

    ;; when construct returns a vector-style query
    {:bindings [{:binding-sym '?e
                 :attr :translatable/fields
                 :entity-index 0
                 :relation [:translatable/fields]}]}
    :translatable/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:translatable/fields [:field/key :field/content]}])]
      :in [$ ?slug]
      :where [[?e :post/slug ?slug]]}

    ;; querying for a menu with deeply nested fields clause
    {:bindings [{:binding-sym '?e
                 :attr :translatable/fields
                 :entity-index 0
                 :relation [:menu/items
                            :menu.item/entity
                            :translatable/fields]}]}
    :translatable/fields
    i18n/translatable-binding?
    '{:find [(pull ?e [{:menu/items
                        [{:menu.item/entity
                          [{:translatable/fields
                            [:field/key :field/content]}]}]}])]
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
