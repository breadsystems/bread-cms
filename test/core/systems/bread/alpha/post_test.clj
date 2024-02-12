(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded]]))

(deftest test-create-post-ancestry-rule
  (are
    [rule n]
    (= rule (post/create-post-ancestry-rule n))

    '[(post-ancestry ?child ?slug_0)
      [?child :post/slug ?slug_0]
      (not-join [?child] [?_ :post/children ?child])]
    1

    '[(post-ancestry ?child ?slug_0 ?slug_1)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      (not-join [?ancestor_1] [?_ :post/children ?ancestor_1])]
    2

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :post/slug ?slug_2]
      (not-join [?ancestor_2] [?_ :post/children ?ancestor_2])]
    3

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :post/slug ?slug_2]
      [?ancestor_3 :post/children ?ancestor_2]
      [?ancestor_3 :post/slug ?slug_3]
      (not-join [?ancestor_3] [?_ :post/children ?ancestor_3])]
    4

    '[(post-ancestry ?child ?slug_0 ?slug_1 ?slug_2 ?slug_3 ?slug_4)
      [?child :post/slug ?slug_0]
      [?ancestor_1 :post/children ?child]
      [?ancestor_1 :post/slug ?slug_1]
      [?ancestor_2 :post/children ?ancestor_1]
      [?ancestor_2 :post/slug ?slug_2]
      [?ancestor_3 :post/children ?ancestor_2]
      [?ancestor_3 :post/slug ?slug_3]
      [?ancestor_4 :post/children ?ancestor_3]
      [?ancestor_4 :post/slug ?slug_4]
      (not-join [?ancestor_4] [?_ :post/children ?ancestor_4])]
    5))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
