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

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
