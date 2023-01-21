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
    (= queries (apply i18n/internationalize-query args))

    ;; Without :post/content
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}]
    [#{:post/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :whatever]

    ;; With :post/fields, but still without :post/content
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug {:post/fields
                                             [:field/key
                                              :field/lang]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}]
    [#{:post/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug {:post/fields
                                             [:field/key
                                              :field/lang]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :whatever]

    ;; With :post/content
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     {:query/name ::store/query
      :query/key [:post :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :field/key :field/content])]
         :in [$ ?p ?lang]
         :where [[?p :post/fields ?e]
                 [?e :field/lang ?lang]]}
       [::bread/data :post :db/id]
       :fr]}]
    [#{:post/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug {:post/fields
                                             [:field/key
                                              :field/content]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :fr]

    ;; With :post/content implicity as part of {:post/fields [*]}
    [{:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     {:query/name ::store/query
      :query/key [:post :post/fields]
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id *])]
         :in [$ ?p ?lang]
         :where [[?p :post/fields ?e]
                 [?e :field/lang ?lang]]}
       [::bread/data :post :db/id]
       :fr]}]
    [#{:post/fields}
     {:query/name ::store/query
      :query/key :post
      :query/db ::FAKEDB
      :query/args
      ['{:find [(pull ?e [:db/id :post/slug {:post/fields [*]}]) .]
         :in [$ ?type]
         :where [[?e :post/type ?type]]}
       :post.type/page]}
     :fr]

    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
