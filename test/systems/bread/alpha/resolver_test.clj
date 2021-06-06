(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.resolver :as resolver]))

(defc with-basic-pull [_]
  {:query [:post/title :post/slug]}
  [:p "whatever"])

(defc with-multi-pull [_]
  {:query {'?e [:post/title :post/slug]
           '?fields [:field/key :field/content]}}
  [:p "whatever"])

(deftest test-pull-query
  (are [clause resolver] (= clause (-> resolver
                                       resolver/pull-query
                                       (get-in [:query :find])))

       ['(pull ?e [:post/title :post/slug])]
       {:resolver/component with-basic-pull}

       #_#_
       ['(pull ?e [:post/title :post/slug])
        '(pull ?fields [:field/key :field/content])]
       {:resolver/component with-multi-pull}
       ))
