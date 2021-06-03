(ns systems.bread.alpha.resolver-test
  (:require
    [clojure.test :refer [deftest are testing]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.post]
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
        [query resolver] (= query
                            (let [counter (atom 0)]
                              (with-redefs
                                [gensym (fn [prefix]
                                          (let [sym
                                                (symbol (str prefix @counter))]
                                            (swap! counter inc)
                                            sym))]
                                (-> resolver
                                    ->app
                                    resolver/resolve-queries
                                    ::bread/queries))))

        ;; {:uri "/en/one"}
        {:post '{:query {:find [(pull ?e [:post/title :custom/key])]
                         :in [$ ?type ?status ?slug]
                         ;; TODO i18n
                         :where [[?e :post/type ?type]
                                 [?e :post/status ?status]
                                 [?e :post/slug ?slug]
                                 (not-join
                                   [?e]
                                   [?e :post/parent ?root-ancestor])]}
                 :args [{:FAKE :STORE}
                        :post.type/page
                        :post.status/published
                        "simple"]}}
        {:resolver/type :resolver.type/page
         :resolver/component my-component
         :route/match {:path-params {:slugs "simple" :lang "en"}}}

        ;; {:uri "/en/one/two"}
        {:post '{:query {:find [(pull ?e [:post/title :custom/key])]
                         :in [$ ?type ?status ?slug ?slug_1]
                         ;; TODO i18n
                         :where [[?e :post/type ?type]
                                 [?e :post/status ?status]
                                 [?e :post/slug ?slug]
                                 ;; NOTE: ?parent_* symbols are where our
                                 ;; gensym override comes into play.
                                 [?e :post/parent ?parent_0]
                                 [?parent_0 :post/slug ?slug_1]
                                 (not-join
                                   [?parent_0]
                                   [?parent_0 :post/parent ?root-ancestor])]}
                 :args [{:FAKE :STORE}
                        :post.type/page
                        :post.status/published
                        "two"
                        "one"]}}
        {:resolver/type :resolver.type/page
         :resolver/component my-component
         :route/match {:path-params {:slugs "one/two" :lang "en"}}}

        ;; {:uri "/en/one/two/three"}
        {:post '{:query {:find [(pull ?e [:post/title :custom/key])]
                         :in [$ ?type ?status ?slug ?slug_1 ?slug_3]
                         ;; TODO i18n
                         :where [[?e :post/type ?type]
                                 [?e :post/status ?status]
                                 [?e :post/slug ?slug]
                                 ;; NOTE: ?parent_* symbols are where our
                                 ;; gensym override comes into play.
                                 [?e :post/parent ?parent_0]
                                 [?parent_0 :post/slug ?slug_1]
                                 [?parent_0 :post/parent ?parent_2]
                                 [?parent_2 :post/slug ?slug_3]
                                 (not-join
                                   [?parent_2]
                                   [?parent_2 :post/parent ?root-ancestor])]}
                 :args [{:FAKE :STORE}
                        :post.type/page
                        :post.status/published
                        "three"
                        "two"
                        "one"]}}
        {:resolver/type :resolver.type/page
         :resolver/component my-component
         :route/match {:path-params {:slugs "one/two/three" :lang "en"}}}

        ;; {:uri "/en/one/two"}
        ;; ancestry? disabled
        {:post '{:query {:find [(pull ?e [:post/title :custom/key])]
                         :in [$ ?type ?status ?slug]
                         ;; TODO i18n
                         :where [[?e :post/type ?type]
                                 [?e :post/status ?status]
                                 [?e :post/slug ?slug]]}
                 :args [{:FAKE :STORE}
                        :post.type/page
                        :post.status/published
                        ;; NOTE: the "one" part of the route just gets
                        ;; discarded.
                        "two"]}}
        {:resolver/type :resolver.type/page
         :resolver/ancestral? false
         :resolver/component my-component
         :route/match {:path-params {:slugs "one/two" :lang "en"}}}

        )))
