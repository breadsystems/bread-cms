(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are]]
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

#_ ;; TODO this belongs at a different level of abstraction.
(deftest test-pull-query
  (are [clause resolver] (= clause (-> resolver
                                       resolver/pull-query
                                       (get-in [0 :find])))

       ['(pull ?e [:post/title :post/slug])]
       {:resolver/component with-basic-pull}

       #_#_
       ['(pull ?e [:post/title :post/slug])
        '(pull ?fields [:field/key :field/content])]
       {:resolver/component with-multi-pull}
       ))
