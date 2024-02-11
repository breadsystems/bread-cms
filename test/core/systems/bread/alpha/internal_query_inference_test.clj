(ns systems.bread.alpha.internal-query-inference-test
  (:require
    [clojure.test :refer [deftest are is]]
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
  (let [i18n-query (fn [{:keys [origin target attr]}]
                     {:in ['?lang]
                      :where [[origin attr target]
                              [target :field/lang '?lang]]})]
    (are
      [result attr construct pred query]
      (= result (let [counter (atom 0)
                      gensym* (fn [prefix]
                                (symbol (str prefix (swap! counter inc))))]
                  (with-redefs [gensym gensym*]
                    (qi/infer-query-bindings attr construct pred query))))

      {:query nil :bindings []} nil nil nil nil
      {:query nil :bindings []} {} nil nil nil
      {:query nil :bindings []} {:find []} nil nil nil

      ;; querying for fields with a wildcard binding
      {:query '{:find [(pull ?e [:translatable/fields])
                       (pull ?e1 [:db/id *])]
                :in [$ ?slug ?lang]
                :where [[?e :post/slug ?slug]
                        [?e :translatable/fields ?e1]
                        [?e1 :field/lang ?lang]]}
       :bindings [{:binding-sym '?e1
                   :attr :translatable/fields
                   :entity-index 0
                   :relation-index 1
                   :relation [:translatable/fields]}]}
      :translatable/fields
      i18n-query
      i18n/translatable-binding?
      '{:find [(pull ?e [{:translatable/fields [*]}])]
        :in [$ ?slug]
        :where [[?e :post/slug ?slug]]}

      ;; querying for fields with key & content bindings
      {:query '{:find [(pull ?e [:translatable/fields])
                       (pull ?e1 [:db/id :field/key :field/content])]
                :in [$ ?slug ?lang]
                :where [[?e :post/slug ?slug]
                        [?e :translatable/fields ?e1]
                        [?e1 :field/lang ?lang]]}
       :bindings [{:binding-sym '?e1
                   :attr :translatable/fields
                   :entity-index 0
                   :relation-index 1
                   :relation [:translatable/fields]}]}
      :translatable/fields
      i18n-query
      i18n/translatable-binding?
      '{:find [(pull ?e [{:translatable/fields [:field/key :field/content]}])]
        :in [$ ?slug]
        :where [[?e :post/slug ?slug]]}

      ;; querying for a menu with deeply nested fields clause
      {:query '{:find [(pull ?e [{:menu/items
                                  [{:menu.item/entity
                                    [:translatable/fields]}]}])
                       (pull ?e1 [:db/id :field/key :field/content])]
                :in [$ ?menu-key ?lang]
                :where [[?e :menu/key ?menu-key]
                        [?e :translatable/fields ?e1]
                        [?e1 :field/lang ?lang]]}
       :bindings [{:binding-sym '?e1
                   :attr :translatable/fields
                   :entity-index 0
                   :relation-index 1
                   :relation [:menu/items
                              :menu.item/entity
                              :translatable/fields]}]}
      :translatable/fields
      i18n-query
      i18n/translatable-binding?
      '{:find [(pull ?e [{:menu/items
                          [{:menu.item/entity
                            [{:translatable/fields
                              [:field/key :field/content]}]}]}])]
        :in [$ ?menu-key]
        :where [[?e :menu/key ?menu-key]]}

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
