(ns systems.bread.alpha.datahike-plugin-test
  (:require
    [clojure.test :refer :all]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.plugin.datahike :as plugin]
    [systems.bread.alpha.test-helpers :as h :refer [db-config->loaded
                                                    db-config->handler
                                                    plugins->loaded]]))

(def config
  {:db/type :datahike
   :store {:backend :mem
           :id "plugin-db"}})

(h/use-db :each config)

(deftest test-datahike-plugin

  (testing "it configures a default as-of-param"
    (let [app ((db-config->handler config) {})]
      (is (= :as-of (bread/config app :db/as-of-param)))))

  (testing "it configures db connection"
    (let [app (db-config->loaded config)]
      (is (instance? clojure.lang.Atom
                     (bread/config app :db/connection)))))

  (testing "database returns the present snapshot by default"
    (let [app (db-config->loaded config)]
      (is (instance? datahike.db.DB (db/database app)))))

  (testing "database honors as-of-tx? config"
    (let [app (db-config->loaded (assoc config :db/as-of-tx? true))
          handler (bread/handler app)
          max-tx (:max-tx (db/database app))
          res (handler {:uri "/" :params {:as-of (dec max-tx)}})]
      (is (instance? datahike.db.AsOfDB (db/database res)))))

  (testing "database honors as-of-param"
    (let [app (db-config->loaded config)
          handler (bread/handler app)
          res (handler {:uri "/" :params {:as-of
                                          "2020-01-01 12:34:56 PDT"}})]
      (is (instance? datahike.db.AsOfDB (db/database res))))

    (let [app (db-config->loaded (assoc config :db/as-of-param :my-param))
          handler (bread/handler app)
          res (handler {:uri "/" :params {:my-param
                                          "2020-01-01 12:34:56 PDT"}})]
      (is (instance? datahike.db.AsOfDB (db/database res)))))

  (testing "database gracefully handles non-numeric strings"
    (let [handler (db-config->handler config)
          res (handler {:uri "/" :params {:as-of "garbage"}})]
      (is (instance? datahike.db.DB (db/database res)))))

  (testing "database calls the ::db/timepoint hook"
    (let [app (plugins->loaded [(db/plugin config)
                                {:hooks
                                 {::db/timepoint
                                  [{:action/name ::bread/value
                                    :action/value (java.util.Date.)}]}}])
          handler (bread/handler app)
          res (handler {:uri "/"})]
      (is (instance? datahike.db.AsOfDB (db/database res)))))

  (testing "database honors as-of-format config"
    (let [handler (db-config->handler
                    (assoc config :db/as-of-format "yyyy-MM-dd"))
          res (handler {:uri "/" :params {:as-of "2020-01-01"}})]
      (is (instance? datahike.db.AsOfDB (db/database res))))))

(comment
  (require '[kaocha.repl :as k])
  (k/run *ns*))
