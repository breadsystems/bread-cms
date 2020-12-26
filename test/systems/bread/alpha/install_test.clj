(ns systems.bread.alpha.install-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [systems.bread.alpha.datastore :as store]
   [systems.bread.alpha.datastore.datahike]))

(defonce config {:datastore/type :datahike
                 :store          {:backend :mem :id "install-db"}})

(defn- wrap-db-installation [run]
  ;; Clean up after any bad test runs
  (store/delete-database! config)
  (store/install! config)
  (run)
  (store/delete-database! config))

(use-fixtures :each wrap-db-installation)

(deftest test-installation
  (is (true? (store/installed? config))))

(deftest test-uninstall
  (store/delete-database! config)
  (is (false? (store/installed? config))))