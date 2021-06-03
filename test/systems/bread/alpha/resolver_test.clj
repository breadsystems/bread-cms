(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are testing]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :refer [datastore->loaded]]))

(defc my-component [{:keys [post]}]
  {:query [:post/title]}
  [:h1 (:post/title post)])

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

        {:post '{:query {:find [(pull ?e [:post/title])]
                         :in [$]
                         :where []}
                 :args [{:store :FAKE}]}}
        {:resolver/type :resolver.type/page
         :resolver/component my-component}

        )))
