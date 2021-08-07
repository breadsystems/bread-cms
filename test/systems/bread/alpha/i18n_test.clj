(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              use-datastore]]))

(def config {:datastore/type :datahike
             :store {:backend :mem
                     :id "something-else"}
             :datastore/initial-txns
             [#:i18n{:key :one :string "One" :lang :en}
              #:i18n{:key :two :string "Two" :lang :en}
              #:i18n{:key :one :string "Uno" :lang :es}
              #:i18n{:key :two :string "Dos" :lang :es}]})

(use-datastore :each config)

(let [load-app #(plugins->loaded [(store/plugin config)
                                  (i18n/plugin)])]

  (deftest test-supported-langs
    (is (= #{:en :es}
           (i18n/supported-langs (load-app)))))

  (deftest test-lang
    (is (= :en (i18n/lang ((bread/handler (load-app)) {:uri "/en"}))))
    (is (= :en (i18n/lang ((bread/handler (load-app)) {:uri "/en/qwerty"}))))
    (is (= :en (i18n/lang ((bread/handler (load-app)) {:uri "/"}))))
    (is (= :en (i18n/lang ((bread/handler (load-app)) {:uri "/qwerty"}))))
    (is (= :es (i18n/lang ((bread/handler (load-app)) {:uri "/es"}))))
    ;; No :fr in database: defaults to :en
    (is (= :en (i18n/lang ((bread/handler (load-app)) {:uri "/fr"})))))

  (deftest test-strings-for
    (is (= {:one "Uno" :two "Dos"} (i18n/strings-for (load-app) :es)))
    (is (= {:one "One" :two "Two"} (i18n/strings-for (load-app) :en)))
    (is (= {} (i18n/strings-for (load-app) :fr))))

  (deftest test-strings
    (is (= {:one "Uno" :two "Dos"}
           (i18n/strings ((bread/handler (load-app)) {:uri "/es"}))))
    (is (= {:one "One" :two "Two"}
           (i18n/strings ((bread/handler (load-app)) {:uri "/en"}))))
    )
  )

(comment
  (k/run))
