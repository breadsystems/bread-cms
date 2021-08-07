(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :as t :refer [deftest are]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.resolver :as resolver]))

(deftest test-pull-query
  (are [clause resolver] (= clause (-> resolver
                                       resolver/pull-query
                                       (get-in [0 :find])))

       ['(pull ?e [:db/id :post/title :post/slug])]
       {:resolver/pull [:post/title :post/slug]}

       ))

(comment
  (k/run))
