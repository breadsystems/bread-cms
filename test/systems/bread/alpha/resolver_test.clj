(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :refer [datastore->loaded]]))

(deftest test-resolve-query
  (let [;; Datastore shows up directly in our args, so we need to mock it
        datastore {:store :FAKE}
        ->app (fn [resolver]
                (let [app (datastore->loaded datastore)]
                  (assoc app ::bread/resolver resolver)))]

      (are
        [query resolver] (= query (-> resolver
                                      ->app
                                      resolver/resolve-queries
                                      ::bread/queries))

        ;; 
        {:post {:query {:find []
                        :in ['$]
                        :where []}
                :args [datastore]}}
        {:resolver/type :resolver.type/post}

        )))
