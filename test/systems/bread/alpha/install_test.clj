(ns systems.bread.alpha.install-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [systems.bread.alpha.datastore :as store]
   [systems.bread.alpha.plugin.datahike]))

(defonce config {:datastore/type :datahike
                 :store {:backend :mem :id "install-db"}})

(defn- wrap-db-installation [run]
  ;; Clean up after any bad test runs
  (store/delete-database! config)
  (run)
  (store/delete-database! config))

(use-fixtures :each wrap-db-installation)

(deftest test-uninstalled
  (is (false? (store/installed? config))))

(deftest test-installation
  (store/install! config)
  (is (true? (store/installed? config))))

(deftest test-delete-database
  (store/delete-database! config)
  (is (false? (store/installed? config))))
