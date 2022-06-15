(ns systems.bread.alpha.post-test
  (:require
    [clojure.test :refer [deftest are]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.test-helpers :refer [datastore->plugin
                                              plugins->loaded]]))

(deftest test-resolve-post-queries
  (let [;; Datastore shows up directly in our args, so we need to mock it.
        ;; We're only checking for its presence in the ::queries spec, so
        ;; while it doesn't need to be a realistic or usable value, it DOES
        ;; need to be a valid Queryable.
        db (reify bread/Queryable (bread/query [_ _ _]))
        app (plugins->loaded [(datastore->plugin db)
                              (resolver/plugin)])
        ->app (fn [resolver]
                (assoc app ::bread/resolver resolver))]

      (are
        [query resolver]
        (= query (-> resolver
                     ->app
                     (bread/hook ::bread/resolve)
                     ::bread/queries))

        ;; {:uri "/en/simple"}
        ;; i18n'd by default
        [[:post
          db
          '{:find [(pull ?e [:db/id :post/title :custom/key]) .]
            :in [$ ?type ?status ?slug_0]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    (not-join [?e] [?root-ancestor :post/children ?e])]}
          :post.type/page
          :post.status/published
          "simple"]]
        {:resolver/type :resolver.type/page
         ;; pull and key come from component
         :resolver/pull [:post/title :custom/key]
         :resolver/key :post
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/one/two"}
        ;; Default key -> :post
        [[:post
          db
          '{:find [(pull ?e [:db/id :post/title :custom/key]) .]
            :in [$ ?type ?status ?slug_0 ?slug_1]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    [?parent_1 :post/children ?e]
                    [?parent_1 :post/slug ?slug_1]
                    (not-join
                      [?parent_1]
                      [?root-ancestor :post/children ?parent_1])]}
          :post.type/page
          :post.status/published
          "two"
          "one"]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         ;; default key -> :post
         :route/params {:slugs "one/two" :lang "en"}}

        ;; {:uri "/en/one/two"}
        ;; Default key -> :post
        [[:post
          db
          '{:find [(pull ?e [:db/id :post/title :custom/key]) .]
            :in [$ ?type ?status ?slug_0 ?slug_1]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    [?parent_1 :post/children ?e]
                    [?parent_1 :post/slug ?slug_1]
                    (not-join
                      [?parent_1]
                      [?root-ancestor :post/children ?parent_1])]}
          :post.type/page
          :post.status/published
          "two"
          "one"]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :resolver/key nil ;; default -> :post
         :route/params {:slugs "one/two" :lang "en"}}

        ;; {:uri "/en/one/two"}
        [[:post
          db
          '{:find [(pull ?e [:db/id :post/title :custom/key]) .]
            :in [$ ?type ?status ?slug_0 ?slug_1]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    [?parent_1 :post/children ?e]
                    [?parent_1 :post/slug ?slug_1]
                    (not-join
                      [?parent_1]
                      [?root-ancestor :post/children ?parent_1])]}
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
          db
          '{:find [(pull ?e [:db/id :post/title :custom/key]) .]
            :in [$ ?type ?status ?slug_0 ?slug_1 ?slug_2]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    [?parent_1 :post/children ?e]
                    [?parent_1 :post/slug ?slug_1]
                    [?parent_2 :post/children ?parent_1]
                    [?parent_2 :post/slug ?slug_2]
                    (not-join
                      [?parent_2]
                      [?root-ancestor :post/children ?parent_2])]}
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
          db
          '{:find [(pull ?e [:db/id :post/title :post/fields]) .]
            :in [$ ?type ?status ?slug_0]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    (not-join [?e] [?root-ancestor :post/children ?e])]}
          :post.type/page
          :post.status/published
          "simple"]
         [:post/fields
          db
          '{:find [(pull ?e [:db/id :field/key :field/content])]
            :in [$ ?p ?lang]
            :where [[?p :post/fields ?e]
                    [?e :field/lang ?lang]]}
          [::bread/data :post :db/id]
          :en]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :post/fields]
         :resolver/key :post
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en/simple"}
        ;; :post/fields i18n w/ nested pull clause
        [[:post
          db
          '{:find [(pull ?e [:db/id :post/title {:post/fields
                                                 [:field/key
                                                  :field/lang]}]) .]
            :in [$ ?type ?status ?slug_0]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    (not-join
                      [?e]
                      [?root-ancestor :post/children ?e])]}
          :post.type/page
          :post.status/published
          "simple"]
         [:post/fields
          db
          '{:find [(pull ?e [:db/id :field/key :field/lang])]
            :in [$ ?p ?lang]
            :where [[?p :post/fields ?e]
                    [?e :field/lang ?lang]]}
          [::bread/data :post :db/id]
          :en]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title {:post/fields [:field/key :field/lang]}]
         :resolver/key :post
         :route/params {:slugs "simple" :lang "en"}}

        ;; {:uri "/en"}
        ;; home page - no `slugs`
        [[:post
          db
          '{:find [(pull ?e [:db/id :post/title :custom/key]) .]
            :in [$ ?type ?status ?slug_0]
            :where [[?e :post/type ?type]
                    [?e :post/status ?status]
                    [?e :post/slug ?slug_0]
                    (not-join [?e] [?root-ancestor :post/children ?e])]}
          :post.type/page
          :post.status/published
          ;; Empty slug!
          ""]]
        {:resolver/type :resolver.type/page
         :resolver/pull [:post/title :custom/key]
         :resolver/key :post
         :route/params {:lang "en"}}

        )))

(comment
  (k/run))
