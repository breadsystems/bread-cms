(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              use-datastore]]))

(def config {:datastore/type :datahike
             :store {:backend :mem
                     :id "test-i18n-db"}
             :datastore/initial-txns
             ;; TODO test locales e.g. en-gb
             [#:i18n{:key :one :string "One" :lang :en}
              #:i18n{:key :two :string "Two" :lang :en}
              #:i18n{:key :one :string "Uno" :lang :es}
              #:i18n{:key :two :string "Dos" :lang :es}]})

(use-datastore :each config)

(defmethod bread/action ::naive-params
  [{:keys [uri] :as req} _ _]
  (let [[lang & slugs] (filter (complement empty?)
                               (string/split (or uri "") #"/"))]
    {:lang lang :slugs slugs}))

(def naive-plugin
  {:hooks {::route/params [{:action/name ::naive-params}]}})

(defn- load-app [i18n-config]
  (plugins->loaded [(store/plugin config)
                    (i18n/plugin i18n-config)
                    naive-plugin]))


(deftest test-supported-langs
  (are
    [langs supported]
    (= langs (-> {:supported-langs supported} load-app i18n/supported-langs))

    nil nil
    #{} #{}
    #{:en} #{:en}
    #{:en :es} #{:en :es}))

(deftest test-lang
  (are
    [lang uri]
    (= lang (i18n/lang ((bread/handler
                          (load-app {:supported-langs #{:en :es}}))
                        {:uri uri})))

    :en "/" ;; No lang route; Defaults to :en.
    :en "/qwerty" ;; Ditto.
    :en "/en"
    :en "/en/qwerty"
    :es "/es"
    :es "/es/qwerty"
    :en "/fr" ;; Default to :en, since there's no :fr in the database.

    ))

(deftest test-strings-for
  (are
    [strings lang]
    (= strings (i18n/strings (load-app {:supported-langs #{:en :es}}) lang))

    {:one "Uno" :two "Dos"} :es
    {:one "One" :two "Two"} :en
    {} :fr
    {} :de))

(deftest test-strings
  (are
    [strings uri]
    (= strings (i18n/strings ((bread/handler
                                (load-app {:supported-langs #{:en :es}}))
                              {:uri uri})))

    {:one "Uno" :two "Dos"} "/es"
    {:one "One" :two "Two"} "/en"
    ;; These default to :en.
    {:one "One" :two "Two"} "/fr"
    {:one "One" :two "Two"} "/de"))

;; i18n/plugin loads I18n strings for the given language automatically.
(deftest test-add-i18n-query
  (let [app (plugins->loaded [(store/plugin config)
                              (i18n/plugin {:supported-langs #{:en :es}})
                              (query/plugin)
                              naive-plugin])]
    (are
      [strings uri]
      (= strings (get-in ((bread/handler app) {:uri uri})
                         [::bread/data :i18n]))

      {:one "Uno" :two "Dos"} "/es"
      {:one "One" :two "Two"} "/en"
      {:one "One" :two "Two"} "/"
      ;; These default to :en.
      {:one "One" :two "Two"} "/fr"
      {:one "One" :two "Two"} "/de")

    (are
      [lang uri]
      (= lang (get-in ((bread/handler app) {:uri uri})
                      [::bread/data :lang]))

      :es "/es"
      :en "/en"
      :en "/"
      :en "/fr"
      :en "/de")))

(deftest test-fallback
  (let [load-app #(plugins->loaded
                    [(store/plugin config)
                     (i18n/plugin (merge {:supported-langs #{:en :es}} %))
                     (query/plugin)
                     naive-plugin])]
    (are
      [strings fallback-lang]
      (= strings
         (get-in ((bread/handler (load-app fallback-lang)) {:uri "/"})
                 [::bread/data :i18n]))

      {:one "Uno" :two "Dos"} {:fallback-lang :es}
      {:one "One" :two "Two"} {:fallback-lang :en}

      ;; English is the fallback fallback.
      {:one "One" :two "Two"} {}
      {:one "One" :two "Two"} nil

      ;; Nothing in the database for the configured fallback lang.
      {} {:fallback-lang :fr}
      {} {:fallback-lang :de}

      ;; Support disabling fallback lang.
      {} {:fallback-lang nil}
      {} {:fallback-lang false})))

(deftest ^:kaocha/skip test-lang-param-config)

(deftest test-internationalize-query
  (are
    [queries args]
    (= queries (let [[attrs query lang] args
                     app (plugins->loaded
                           [(i18n/plugin {:supported-langs
                                          #{:en :fr :ru :es :de}
                                          :db-attrs
                                          attrs})
                            ;; Set up an ad-hoc plugin to hard-code lang.
                            {:hooks
                             {:hook/lang [{:action/name ::bread/value
                                           :action/value lang}]}}])]
                 (bread/hook app ::i18n/queries query)))

    ;; Without :field/content
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :whatever]

    ;; With :post/fields, but still without :field/content
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          {:post/fields [:field/key :field/lang]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          {:post/fields [:field/key :field/lang]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :whatever]

    ;; With :field/content
    [{:query/name ::store/query
      :query/key :post-with-content
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug :post/fields]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     {:query/name ::store/query
      :query/key [:post-with-content :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :field/key :field/content])]
         :in [$ ?e0 ?lang]
         :where [[?e :field/lang ?lang]
                 [?e0 :post/fields ?e]]}
       [::bread/data :post-with-content :db/id]
       :fr]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :post-with-content
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          {:post/fields [:field/key :field/content]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :fr]

    ;; With :field/content implicity as part of {:post/fields [*]}
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug :post/fields]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     {:query/name ::store/query
      :query/key [:post :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id *])]
         :in [$ ?e0 ?lang]
         :where [[?e :field/lang ?lang]
                 [?e0 :post/fields ?e]]}
       [::bread/data :post :db/id]
       :ru]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug {:post/fields [*]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :ru]

    ;; With :field/content implicity as part of {:taxon/fields [*]}
    [{:query/name ::store/query
      :query/key :taxon
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :taxon/slug :taxon/fields]) .]
         :in [$ ?taxonomy]
         :where [[?e :taxon/taxonomy ?taxonomy]]}
       :taxon.taxonomy/tag]}
     {:query/name ::store/query
      :query/key [:taxon :taxon/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id *])]
         :in [$ ?e0 ?lang]
         :where [[?e :field/lang ?lang]
                 [?e0 :taxon/fields ?e]]}
       [::bread/data :taxon :db/id]
       :es]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :taxon
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :taxon/slug {:taxon/fields [*]}]) .]
         :in [$ ?taxonomy]
         :where [[?e :taxon/taxonomy ?taxonomy]]}
       :taxon.taxonomy/tag]}
     :es]

    ;; With deeply nested, implicit :field/content
    [{:query/name ::store/query
      :query/key :post-with-fields-and-taxons
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          :post/fields
                          {:post/taxons [:taxon/slug
                                         :taxon/taxonomy
                                         :taxon/fields]}]) .]
         :in [$ ?slug ?type]
         :where [[?e :post/slug ?slug]
                 [?e :post/type ?type]]}
       "my-post"
       :post.type/page]}
     {:query/name ::store/query
      :query/key [:post-with-fields-and-taxons :post/taxons :taxon/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id *])]
         ;; Post ID gets passed in as ?e1.
         :in [$ ?e1 ?lang]
         :where [;; Go through the :post/taxons relationship to get to the
                 ;; :taxon/fields content.
                 [?e :field/lang ?lang]
                 [?e0 :taxon/fields ?e]
                 [?e1 :post/taxons ?e0]]}
       ;; Get the post ID to be passed in from this data path.
       [::bread/data :post-with-fields-and-taxons :db/id]
       :de]}
     {:query/name ::store/query
      :query/key [:post-with-fields-and-taxons :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id *])]
         :in [$ ?e0 ?lang]
         :where [[?e :field/lang ?lang]
                 [?e0 :post/fields ?e]]}
       [::bread/data :post-with-fields-and-taxons :db/id]
       :de]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :post-with-fields-and-taxons
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          {:post/fields [*]}
                          {:post/taxons [:taxon/slug
                                         :taxon/taxonomy
                                         {:taxon/fields [*]}]}]) .]
         :in [$ ?slug ?type]
         :where [[?e :post/slug ?slug]
                 [?e :post/type ?type]]}
       "my-post"
       :post.type/page]}
     :de]

    ;; With deeply nested, mixed implicit & explicit :field/content
    [{:query/name ::store/query
      :query/key :post-with-taxons-and-field-content
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          :post/fields
                          {:post/taxons [:taxon/slug
                                         :taxon/taxonomy
                                         :taxon/fields]}]) .]
         :in [$ ?slug ?type]
         :where [[?e :post/slug ?slug]
                 [?e :post/type ?type]]}
       "my-post"
       :post.type/page]}
     {:query/name ::store/query
      :query/key [:post-with-taxons-and-field-content :post/taxons :taxon/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id *])]
         ;; Post ID gets passed in as ?e1.
         :in [$ ?e1 ?lang]
         :where [;; Go through the :post/taxons relationship to get to the
                 ;; :taxon/fields content.
                 [?e :field/lang ?lang]
                 [?e0 :taxon/fields ?e]
                 [?e1 :post/taxons ?e0]]}
       ;; Get the post ID to be passed in from this data path.
       [::bread/data :post-with-taxons-and-field-content :db/id]
       :en]}
     {:query/name ::store/query
      :query/key [:post-with-taxons-and-field-content :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :field/key :field/content])]
         :in [$ ?e0 ?lang]
         :where [[?e :field/lang ?lang]
                 [?e0 :post/fields ?e]]}
       [::bread/data :post-with-taxons-and-field-content :db/id]
       :en]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query
      :query/key :post-with-taxons-and-field-content
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :post/slug
                          {:post/fields [:field/key :field/content]}
                          {:post/taxons [:taxon/slug
                                         :taxon/taxonomy
                                         {:taxon/fields [*]}]}]) .]
         :in [$ ?slug ?type]
         :where [[?e :post/slug ?slug]
                 [?e :post/type ?type]]}
       "my-post"
       :post.type/page]}
     :en]

    ;; With posts nested under a taxon
    [{:query/name ::store/query
      :query/key :nested-taxon
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id
                          :taxon/slug
                          {:post/_taxons [:post/slug :post/fields]}]) .]
         :in [$ % ?status ?type ?taxonomy ?slug]
         :where [[?e :taxon/slug ?slug]
                 [?e0 :post/status ?status]
                 [?e0 :post/type ?type]
                 (post-taxonomized ?e0 ?taxonomy ?slug)]}
       '[[(post-taxonomized ?post ?taxonomy ?taxon-slug)
          [?post :post/taxons ?t]
          [?t :taxon/taxonomy ?taxonomy]
          [?t :taxon/slug ?taxon-slug]]]
       :post.status/published
       :post.type/page
       :taxon.taxonomy/category
       "my-cat"]}
     {:query/name ::store/query
      :query/key [:nested-taxon :post/_taxons :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :field/key :field/content])]
         ;; Post ID gets passed in as ?e1.
         :in [$ ?e1 ?lang]
         :where [;; Go through the :post/taxons relationship to get to the
                 ;; :taxon/fields content. In this case we also need to
                 ;; recognize that the :post/_taxons attr is an inverse
                 ;; relationship, and reverse it back the :where clause.
                 [?e :field/lang ?lang]
                 [?e0 :post/fields ?e]
                 [?e0 :post/taxons ?e1]]}
       ;; Get the post ID to be passed in from this data path.
       [::bread/data :nested-taxon :db/id]
       :fr]}]
    [#{:post/fields :taxon/fields :user/fields}
     {:query/name ::store/query,
      :query/key :nested-taxon,
      :query/db ::FAKEDB,
      :query/args
      ['{:find
         [(pull ?e [:db/id
                    :taxon/slug
                    {:post/_taxons [:post/slug
                                    {:post/fields [:field/key
                                                   :field/content]}]}]) .]
         :in [$ % ?status ?type ?taxonomy ?slug]
         :where
         [[?e :taxon/slug ?slug]
          [?e0 :post/status ?status]
          [?e0 :post/type ?type]
          (post-taxonomized ?e0 ?taxonomy ?slug)]}
       '[[(post-taxonomized ?post ?taxonomy ?taxon-slug)
          [?post :post/taxons ?t]
          [?t :taxon/taxonomy ?taxonomy]
          [?t :taxon/slug ?taxon-slug]]]
       :post.status/published
       :post.type/page
       :taxon.taxonomy/category
       "my-cat"]}
     :fr]

    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
