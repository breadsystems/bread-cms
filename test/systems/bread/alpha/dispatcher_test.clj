(ns systems.bread.alpha.dispatcher-test
  (:require
    [clojure.test :as t :refer [deftest are]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]))

(deftest test-pull-query
  (are [clause dispatcher] (= clause (-> dispatcher
                                       dispatcher/pull-query
                                       (get-in [0 :find])))

       ['(pull ?e [:db/id :post/title :post/slug])]
       {:dispatcher/pull [:post/title :post/slug]}

       ))

(comment
  (k/run))
