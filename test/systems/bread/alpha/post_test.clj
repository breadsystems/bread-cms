(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-resolve-post-queries
  (let [;; Datastore shows up directly in our args, so we need to mock it.
        ;; We're only checking for its presence in the Queryable, so it doesn't
        ;; need to be a realistic or usable value.
        app (plugins->loaded [(datastore->plugin ::MOCK_STORE)])
        ->app (fn [resolver]
                (assoc app ::bread/resolver resolver))]

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
        ;; i18n'd by default
        [[:post
          ::MOCK_STORE
          '{:find [(pull ?e [:post/title :custom/key]) .]
            :in [$ ?type ?status ?slug]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug]
                    (not-join [?e] [?e :post/parent ?root-ancestor])]}
          :post.type/page
          :post.status/published
          "simple"]]
        {:resolver/type :resolver.type/page
         ;; pull and key come from component
         :resolver/pull [:post/title :custom/key]
         :resolver/key :post
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/one/two"}
        [[:post
          ::MOCK_STORE
          '{:find [(pull ?e [:post/title :custom/key]) .]
            :in [$ ?type ?status ?slug ?slug_1]
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
          :post.type/page
          :post.status/published
          "two"
          "one"]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :resolver/key :post
         :route/params {:slugs "one/two" :lang "en"}}

        ;; {:uri "/en/one/two/three"}
        [[:post
          ::MOCK_STORE
          '{:find [(pull ?e [:post/title :custom/key]) .]
            :in [$ ?type ?status ?slug ?slug_1 ?slug_3]
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
          :post.type/page
          :post.status/published
          "three"
          "two"
          "one"]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :resolver/key :post
         :route/params {:slugs "one/two/three" :lang "en"}}

        ;; {:uri "/en/simple"}
        ;; :post/fields i18n
        [[:post
          ::MOCK_STORE
          '{:find [(pull ?e [:post/title :post/fields]) .]
            :in [$ ?type ?status ?slug]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug]
                    (not-join [?e] [?e :post/parent ?root-ancestor])]}
          :post.type/page
          :post.status/published
          "simple"]
         [:post/fields
          ::MOCK_STORE
          '{:find [(pull ?e [:db/id :field/key :field/content])]
            :in [$ ?p ?lang]
            :where [[?p :post/fields ?e]
                    [?e :field/lang ?lang]]}
          :post/id
          :en
          {:post/id [:post :db/id]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :post/fields]
         :resolver/key :post
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/simple"}
        ;; :post/fields i18n w/ nested pull clause
        [[:post
          ::MOCK_STORE
          '{:find [(pull ?e [:post/title {:post/fields
                                          [:field/key
                                           :field/lang]}]) .]
            :in [$ ?type ?status ?slug]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug]
                    (not-join
                      [?e]
                      [?e :post/parent ?root-ancestor])]}
          :post.type/page
          :post.status/published
          "simple"]
         [:post/fields
          ::MOCK_STORE
          '{:find [(pull ?e [:db/id :field/key :field/lang])]
            :in [$ ?p ?lang]
            :where [[?p :post/fields ?e]
                    [?e :field/lang ?lang]]}
          :post/id
          :en
          {:post/id [:post :db/id]}]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title {:post/fields [:field/key :field/lang]}]
         :resolver/key :post
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en"}
        ;; home page - no `slugs`
        [[:post
          ::MOCK_STORE
          '{:find [(pull ?e [:post/title :custom/key]) .]
            :in [$ ?type ?status ?slug]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug]
                    (not-join [?e] [?e :post/parent ?root-ancestor])]}
          :post.type/page
          :post.status/published
          ;; Empty slug!
          ""]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :resolver/key :post
         :route/params {:lang "en"}}

        )))
