(ns systems.bread.alpha.thing-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.thing :as thing]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              naive-router
                                              plugins->loaded]]))

(deftest test-create-ancestry-rule
  (are
    [rule n]
    (= rule (thing/create-ancestry-rule n))

    '[(ancestry ?child ?slug_0)
      [?child :thing/slug ?slug_0]
      (not-join [?child] [?_ :thing/children ?child])]
    1

    '[(ancestry ?child ?slug_0 ?slug_1)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      (not-join [?ancestor_1] [?_ :thing/children ?ancestor_1])]
    2

    '[(ancestry ?child ?slug_0 ?slug_1 ?slug_2)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :thing/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      (not-join [?ancestor_2] [?_ :thing/children ?ancestor_2])]
    3

    '[(ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :thing/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      [?ancestor_3 :thing/children ?ancestor_2]
      [?ancestor_3 :thing/slug ?slug_3]
      (not-join [?ancestor_3] [?_ :thing/children ?ancestor_3])]
    4

    '[(ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3 ?slug_4)
      [?child :thing/slug ?slug_0]
      [?ancestor_1 :thing/children ?child]
      [?ancestor_1 :thing/slug ?slug_1]
      [?ancestor_2 :thing/children ?ancestor_1]
      [?ancestor_2 :thing/slug ?slug_2]
      [?ancestor_3 :thing/children ?ancestor_2]
      [?ancestor_3 :thing/slug ?slug_3]
      [?ancestor_4 :thing/children ?ancestor_3]
      [?ancestor_4 :thing/slug ?slug_4]
      (not-join [?ancestor_4] [?_ :thing/children ?ancestor_4])]
    5))

(deftest test-ancestralize
  (are
    [expected query-args slugs args]
    (= expected (apply thing/ancestralize query-args slugs args))

    ;; Querying for a top-level thing should just assert that
    ;; that thing has no parent.
    '[{:find [?e]
       :in [$ % ?slug_0]
       :where [(ancestry ?e ?slug_0)]}
      ::DB
      [[(ancestry ?child ?slug_0)
        [?child :thing/slug ?slug_0]
        (not-join [?child] [?_ :thing/children ?child])]]
      "a"]
    '[{:find [?e]
       :in [$]
       :where []}
      ::DB]
    ["a"]
    nil

    ;; Existing :in and :where should be left intact.
    '[{:find [?e]
       :in [$ ?status % ?slug_0]
       :where [[?e :post/status ?status]
               (ancestry ?e ?slug_0)]}
      ::DB
      :published
      [[(ancestry ?child ?slug_0)
        [?child :thing/slug ?slug_0]
        (not-join [?child] [?_ :thing/children ?child])]]
      "a"]
    '[{:find [?e]
       :in [$ ?status]
       :where [[?e :post/status ?status]]}
      ::DB
      :published]
    ["a"]
    nil

    ;; Querying for a level-2 thing asserts a single level of ancestry.
    '[{:find [?e]
       ;; NOTE: URL-ordering of slugs is preserved here.
       :in [$ % ?slug_1 ?slug_0]
       :where [(ancestry ?e ?slug_0 ?slug_1)]}
      ::DB
      [[(ancestry ?child ?slug_0 ?slug_1)
        [?child :thing/slug ?slug_0]
        [?ancestor_1 :thing/children ?child]
        [?ancestor_1 :thing/slug ?slug_1]
        (not-join [?ancestor_1] [?_ :thing/children ?ancestor_1])]]
      "a"
      "b"]
    '[{:find [?e]
       :in [$]
       :where []}
      ::DB]
    ["a" "b"]
    nil

    ;; Level-3, etc.
    '[{:find [?e]
       ;; NOTE: URL-ordering of slugs is preserved here.
       :in [$ % ?slug_2 ?slug_1 ?slug_0]
       :where [(ancestry ?e ?slug_0 ?slug_1 ?slug_2)]}
      ::DB
      [[(ancestry ?child ?slug_0 ?slug_1 ?slug_2)
        [?child :thing/slug ?slug_0]
        [?ancestor_1 :thing/children ?child]
        [?ancestor_1 :thing/slug ?slug_1]
        [?ancestor_2 :thing/children ?ancestor_1]
        [?ancestor_2 :thing/slug ?slug_2]
        (not-join [?ancestor_2] [?_ :thing/children ?ancestor_2])]]
      "a"
      "b"
      "c"]
    '[{:find [?e]
       :in [$]
       :where []}
      ::DB]
    ["a" "b" "c"]
    nil

    ;; Supports specifying an entity symbol.
    '[{:find [?EEEEE]
       :in [$ ?status % ?slug_0]
       :where [[?EEEEE :post/status ?status]
               (ancestry ?EEEEE ?slug_0)]}
      ::DB
      :published
      [[(ancestry ?child ?slug_0)
        [?child :thing/slug ?slug_0]
        (not-join [?child] [?_ :thing/children ?child])]]
      "a"]
    '[{:find [?EEEEE]
       :in [$ ?status]
       :where [[?EEEEE :post/status ?status]]}
      ::DB
      :published]
    ["a"]
    [:e-sym '?EEEEE]

    ;; Should find existing % input and place rules in the correct input slots.
    '[{:find [?e]
       :in [$ ?status % ?type ?slug_0]
       :where [[?e :post/status ?status]
               (my-type-rule ?type)
               (ancestry ?e ?slug_0)]}
      ::DB
      :published
      [[(my-type-rule ?type)
        [?e :post/type ?type]]
       [(ancestry ?child ?slug_0)
        [?child :thing/slug ?slug_0]
        (not-join [?child] [?_ :thing/children ?child])]]
      :my-type
      "a"]
    '[{:find [?e]
       :in [$ ?status % ?type]
       :where [[?e :post/status ?status]
               (my-type-rule ?type)]}
      ::DB
      :published
      [[(my-type-rule ?type)
        [?e :post/type ?type]]]
      :my-type]
    ["a"]
    nil

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
