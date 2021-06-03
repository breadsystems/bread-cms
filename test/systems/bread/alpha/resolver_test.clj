(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are testing]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              datastore->plugin]]))

(defc my-component [{:keys [post]}]
  {:query [:post/title]}
  [:h1 (:post/title post)])

(deftest test-resolve-query
  (let [;; Datastore shows up directly in our args, so we need to mock it
        datastore {:FAKE :STORE}
        mock-datastore-plugin (datastore->plugin datastore)
        ->app (fn [resolver]
                (let [app (plugins->loaded [mock-datastore-plugin])]
                  (assoc app ::bread/resolver resolver)))]

      (are
        [query resolver] (= query (-> resolver
                                      ->app
                                      resolver/resolve-queries
                                      ::bread/queries))

        {:post '{:query {:find [(pull ?e [:post/title])]
                         :in [$]
                         :where []}
                 :args [{:FAKE :STORE}]}}
        {:resolver/type :resolver.type/page
         :resolver/component my-component}

        )))
