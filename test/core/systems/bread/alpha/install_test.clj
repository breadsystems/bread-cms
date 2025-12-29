(ns systems.bread.alpha.install-test
  (:require
   [clojure.test :refer [deftest are is use-fixtures]]
   [systems.bread.alpha.database :as db]
   [systems.bread.alpha.schema :as schema]
   [systems.bread.alpha.plugin.datahike]
   [systems.bread.alpha.test-helpers :refer [db-config->loaded]]))

(defonce config {:db/type :datahike
                 :db/config {:store {:backend :mem :id "install-db"}}})

(defn- wrap-db-installation [run]
  ;; Clean up after any bad test runs
  (db/delete! config)
  (run)
  (db/delete! config))

(use-fixtures :each wrap-db-installation)

(deftest test-migrations
  (let [my-migration (with-meta
                       [{:migration/key :my/migration}]
                       {:migration/dependencies
                        #{:bread.migration/migrations
                          :bread.migration/posts}})]
    (db/create! config)
    (db-config->loaded (assoc config :db/migrations
                                     (conj schema/initial my-migration)))
    (are
      [pred migration] (pred (db/migration-ran?
                               (db/db (db/connect config))
                               migration))
      true? schema/migrations
      true? schema/posts
      true? schema/i18n
      true? schema/taxons
      true? schema/menus
      true? schema/revisions
      true? schema/comments
      true? schema/users
      true? my-migration
      false? [{:migration/key :NOPE}])))

(deftest test-migration-dependencies
  (let [my-migration (with-meta
                       [{:migration/key :my/migration}]
                       {:migration/dependencies #{:UNMET}})
        config (assoc config :db/migrations [my-migration])]
    (db/create! config)
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Migration has one or more unmet dependencies!"
                          (db-config->loaded config)))
    (is (= #{:UNMET} (try
                       (db-config->loaded config)
                       (catch clojure.lang.ExceptionInfo ex
                         (:unmet-deps (ex-data ex))))))))
