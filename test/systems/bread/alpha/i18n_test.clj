(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.test-helpers :as h])
  (:import
    [java.util UUID]))

(let [config {:datastore/type :datahike
              :store {:backend :mem
                      :id "i18n-db"}
              :datastore/initial-txns
              [#:i18n{:key :i18n/one :string "One" :lang :en}
               #:i18n{:key :i18n/two :string "Two" :lang :en}
               #:i18n{:key :i18n/one :string "Uno" :lang :es}
               #:i18n{:key :i18n/two :string "Dos" :lang :es}]}
      app #(h/plugins->loaded [(store/plugin config)
                               (i18n/plugin)])]

  (use-fixtures :each (fn [f]
                        (store/delete-database! config)
                        (store/install! config)
                        (store/connect! config)
                        (f)
                        (store/delete-database! config)))

  (deftest test-lang
    (is (= :en (i18n/lang ((bread/handler (app)) {:uri "/en"}))))
    (is (= :en (i18n/lang ((bread/handler (app)) {:uri "/en/qwerty"}))))
    (is (= :en (i18n/lang ((bread/handler (app)) {:uri "/"}))))
    (is (= :en (i18n/lang ((bread/handler (app)) {:uri "/qwerty"}))))
    #_
    (is (= :es (i18n/lang ((bread/handler (app)) {:uri "/es"}))))
    (is (= :fr (i18n/lang ((bread/handler (app)) {:uri "/fr"})))))

  #_
  (deftest test-strings-for

    (is (= {:one "Uno" :two "Dos"} (bread/strings-for )))))
