(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :as t :refer [deftest are]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.resolver :as resolver]))

(defc with-basic-pull [_]
  {:query [:post/title :post/slug]
   :key :basic}
  [:p "whatever"])

(defc with-multi-pull [_]
  {:query {'?e [:post/title :post/slug]
           '?fields [:field/key :field/content]}
   :key :multi}
  [:p "whatever"])

(deftest test-query-key
  (are [k resolver] (= k (resolver/query-key resolver))

    :basic {:resolver/component with-basic-pull}
    :multi {:resolver/component with-multi-pull}))

(deftest test-pull-query
  (are [clause resolver] (= clause (-> resolver
                                       resolver/pull-query
                                       (get-in [0 :find])))

       ['(pull ?e [:db/id :post/title :post/slug])]
       {:resolver/pull [:post/title :post/slug]}

       ))

(comment
  (t/run-all-tests #"systems\.bread\.*")
  (t/run-tests))
