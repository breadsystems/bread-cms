(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :as h]))

(deftest test-resolve-post-queries
  (let [;; Datastore shows up directly in our args, so we need to mock it
        datastore {:FAKE :STORE}
        mock-datastore-plugin (h/datastore->plugin datastore)
        router-plugin (fn [app]
                        (bread/add-hook app :hook/route-params
                          (fn [_ match]
                            (:path-params match))))
        ->app (fn [resolver]
                (let [app (h/plugins->loaded [mock-datastore-plugin
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

        ;; {:uri "/en/simple"}
        ;; no i18n
        [[:post {:query '{:find [(pull ?e [:post/title :custom/key])]
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
                         "simple"]
                 ::bread/expand [post/expand-post]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/simple"}
        ;; :post/fields i18n
        [[:post {:query '{:find [(pull ?e [:post/title :post/fields])]
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
                         "simple"]
                 ::bread/expand [post/expand-post]}]
         [:post/fields {:query '{:find [(pull ?e [:db/id :field/key :field/content])]
                                 :in [$ ?p ?lang]
                                 :where [[?p :post/fields ?e]
                                         [?e :field/lang ?lang]]}
                        :args [{:FAKE :STORE}
                               :post/id
                               :en]
                        ::bread/expand []}
          {:post/id [:post :db/id]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :post/fields]
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/simple"}
        ;; :post/fields i18n w/ map
        [[:post {:query '{:find [(pull ?e [:post/title {:post/fields
                                                        [:field/key
                                                         :field/lang]}])]
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
                         "simple"]
                 ::bread/expand [post/expand-post]}]
         [:post/fields {:query '{:find [(pull ?e [:db/id :field/key :field/lang])]
                                 :in [$ ?p ?lang]
                                 :where [[?p :post/fields ?e]
                                         [?e :field/lang ?lang]]}
                        :args [{:FAKE :STORE}
                               :post/id
                               :en]
                        ::bread/expand []}
          {:post/id [:post :db/id]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title {:post/fields [:field/key :field/lang]}]
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/one"}
        ;; expand? disabled
        [[:post {:query '{:find [(pull ?e [:post/title :custom/key])]
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
                        "simple"]
                 ::bread/expand []}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :resolver/expand? false
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/one/two"}
        [[:post {:query '{:find [(pull ?e [:post/title :custom/key])]
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
                        "one"]
                 ::bread/expand [post/expand-post]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :route/params {:slugs "one/two" :lang "en"}}

        ;; {:uri "/en/one/two/three"}
        [[:post {:query '{:find [(pull ?e [:post/title :custom/key])]
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
                        "one"]
                 ::bread/expand [post/expand-post]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :route/params {:slugs "one/two/three" :lang "en"}}

        ;; {:uri "/en/one/two"}
        ;; ancestry? disabled
        [[:post {:query '{:find [(pull ?e [:post/title :custom/key])]
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
                        "two"]
                 ::bread/expand [post/expand-post]}]]
        {:resolver/type :resolver.type/page
         :resolver/ancestral? false
         :resolver/pull [:post/title :custom/key]
         :route/params {:slugs "one/two" :lang "en"}}

        ;; {:uri "/en"}
        ;; home page - no `slugs`
        [[:post {:query '{:find [(pull ?e [:post/title :custom/key])]
                         :in [$ ?type ?status ?slug]
                         ;; TODO i18n
                         :where [[?e :post/type ?type]
                                 [?e :post/status ?status]
                                 [?e :post/slug ?slug]]}
                 :args [{:FAKE :STORE}
                        :post.type/page
                        :post.status/published
                        ;; Empty slug!
                        ""]
                 ::bread/expand [post/expand-post]}]]
        {:resolver/type :resolver.type/page
         :resolver/ancestral? false
         :resolver/pull [:post/title :custom/key]
         :route/params {:lang "en"}}

        )))
