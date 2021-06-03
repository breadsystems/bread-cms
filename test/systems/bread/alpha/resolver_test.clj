(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are testing]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              datastore->plugin]]))

(defc my-component [{:keys [post]}]
  {:query [:post/title :custom/key]}
  [:h1 (:post/title post)])

(deftest test-resolve-query
  (let [;; Datastore shows up directly in our args, so we need to mock it
        datastore {:FAKE :STORE}
        mock-datastore-plugin (datastore->plugin datastore)
        router-plugin (fn [app]
                        (bread/add-hook app :hook/route-params
                          (fn [_ match]
                            (:path-params match))))
        ->app (fn [resolver]
                (let [app (plugins->loaded [mock-datastore-plugin
                                            router-plugin])]
                  (assoc app ::bread/resolver resolver)))]

      (are
        [query resolver] (= query (-> resolver
                                      ->app
                                      resolver/resolve-queries
                                      ::bread/queries))

        ;; {:uri "/en/one"}
        {:post '{:query {:find [(pull ?e [:post/title :custom/key])]
                         :in [$ ?type ?status ?slug]
                         :where [[?e :post/type ?type]
                                 [?e :post/status ?status]
                                 [?e :post/slug ?slug]]}
                 :args [{:FAKE :STORE}
                        :post.type/page
                        :post.status/published
                        "one"]}}
        {:resolver/type :resolver.type/page
         :resolver/component my-component
         :route/match {:path-params {:slugs "one" :lang "en"}}}

        )))
