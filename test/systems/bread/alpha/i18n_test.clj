(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              use-datastore]]))

(def config {:datastore/type :datahike
             :store {:backend :mem
                     :id "something-else"}
             :datastore/initial-txns
             ;; TODO test locales e.g. en-gb
             [#:i18n{:key :one :string "One" :lang :en}
              #:i18n{:key :two :string "Two" :lang :en}
              #:i18n{:key :one :string "Uno" :lang :es}
              #:i18n{:key :two :string "Dos" :lang :es}]})

(use-datastore :each config)

(defn naive-params [{:keys [uri] :as req} _]
  (let [[lang & slugs] (filter (complement empty?)
                             (string/split (or uri "") #"/"))]
    {:lang lang :slugs slugs}))

(defn naive-string-parsing-route-plugin []
  (fn [app]
    (bread/add-hook app :hook/route-params naive-params)))

(comment
  (string/split "a/b/c" #"/" 2)
  (def i18n-string-plugin (i18n-string-route-plugin))
  )

(let [load-app #(plugins->loaded [(store/plugin config)
                                  (i18n/plugin)
                                  (naive-string-parsing-route-plugin)])]

  (deftest test-supported-langs
    (is (= #{:en :es}
           (i18n/supported-langs (load-app)))))

  (deftest test-lang
    (are
      [lang uri]
      (= lang (i18n/lang ((bread/handler (load-app)) {:uri uri})))

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
      (= strings (i18n/strings-for (load-app) lang))

      {:one "Uno" :two "Dos"} :es
      {:one "One" :two "Two"} :en
      {} :fr
      {} :de))

  (deftest test-strings
    (are
      [strings uri]
      (= strings (i18n/strings ((bread/handler (load-app)) {:uri uri})))

      {:one "Uno" :two "Dos"} "/es"
      {:one "One" :two "Two"} "/en"
      ;; These default to :en.
      {:one "One" :two "Two"} "/fr"
      {:one "One" :two "Two"} "/de"))

  )

;; i18n/plugin loads I18n strings for the given language automatically.
(deftest test-add-i18n-query
  (let [app (plugins->loaded [(store/plugin config)
                              (i18n/plugin)
                              (query/plugin)
                              (naive-string-parsing-route-plugin)])]
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
      :en "/de"
      )))

(deftest test-fallback
  (let [load-app #(plugins->loaded [(store/plugin config)
                                    (i18n/plugin {:i18n/fallback %})
                                    (query/plugin)
                                    (naive-string-parsing-route-plugin)])]
    (are
      [strings fallback-lang]
      (= strings
         (get-in ((bread/handler (load-app fallback-lang)) {:uri "/"})
                 [::bread/data :i18n]))

      {:one "Uno" :two "Dos"} :es
      {:one "One" :two "Two"} :en

      ;; English is the fallback fallback.
      {:one "One" :two "Two"} nil

      ;; Nothing in the database for the configured fallback lang.
      {} :fr
      {} :de)))

(deftest ^:kaocha/skip test-lang-param-config)

(comment
  (k/run))
