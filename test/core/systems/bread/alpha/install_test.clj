(ns systems.bread.alpha.install-test
  (:require
   [clojure.test :refer [deftest are is use-fixtures]]
   [systems.bread.alpha.database :as store]
   [systems.bread.alpha.schema :as schema]
   [systems.bread.alpha.plugin.datahike]
   [systems.bread.alpha.test-helpers :refer [datastore-config->loaded]]))

(defonce config {:db/type :datahike
                 :store {:backend :mem :id "install-db"}})

(defn- wrap-db-installation [run]
  ;; Clean up after any bad test runs
  (store/delete! config)
  (run)
  (store/delete! config))

(use-fixtures :each wrap-db-installation)

(deftest test-migrations
  (let [my-migration (with-meta
                       [{:migration/key :my/migration}]
                       {:migration/dependencies
                        #{:bread.migration/migrations
                          :bread.migration/posts}})]
    (store/create! config)
    (datastore-config->loaded (assoc config :db/migrations
                                     (conj schema/initial my-migration)))
    (are
      [pred migration] (pred (store/migration-ran?
                               (store/db (store/connect config))
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
    (store/create! config)
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Migration has one or more unmet dependencies!"
                          (datastore-config->loaded config)))
    (is (= #{:UNMET} (try
                       (datastore-config->loaded config)
                       (catch clojure.lang.ExceptionInfo ex
                         (:unmet-deps (ex-data ex))))))))
