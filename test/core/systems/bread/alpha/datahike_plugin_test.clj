(ns systems.bread.alpha.datahike-plugin-test
  (:require
    [clojure.test :refer :all]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as store]
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

  (testing "it configures as-of-param"
    (let [app ((db-config->handler config) {})]
      (is (= :as-of (bread/config app :db/as-of-param)))))

  (testing "it honors custom as-of-param"
    (let [app (db-config->loaded
                (assoc config :db/as-of-param :my/param))]
      (is (= :my/param (bread/config app :db/as-of-param)))))

  (testing "it configures db connection"
    (let [app (db-config->loaded config)]
      (is (instance? clojure.lang.Atom
                     (bread/config app :db/connection)))))

  (testing "::store/db returns the present snapshot by default"
    (let [app (db-config->loaded config)]
      (is (instance? datahike.db.DB (bread/hook app ::store/db)))))

  (testing "db-tx honors as-of-param"
    (let [app (db-config->loaded config)
          db (store/datastore app)
          handler (bread/handler app)
          max-tx (store/max-tx app)
          response (handler {:uri "/" :params {:as-of (dec max-tx)}})]
      (is (instance? datahike.db.AsOfDB (store/datastore response)))))

  (testing "db-tx gracefully handles non-numeric strings"
    (let [handler (db-config->handler config)
          response (handler {:uri "/" :params {:as-of "garbage"}})]
      (is (instance? datahike.db.DB (store/datastore response)))))

  (testing "db-datetime honors as-of param"
    (let [handler (db-config->handler
                    (assoc config
                           :db/req->timepoint store/db-datetime))
          response (handler {:uri "/"
                             ;; pass a literal date here
                             :params {:as-of "2020-01-01 00:00:00 PDT"}})]
      (is (instance? datahike.db.AsOfDB (store/datastore response)))))

  (testing "db-datetime honors as-of-format config"
    (let [handler (db-config->handler
                    (assoc config
                           :db/as-of-format "yyyy-MM-dd"
                           :db/req->timepoint store/db-datetime))
          response (handler {:uri "/"
                             ;; pass a literal date here
                             :params {:as-of "2020-01-01"}})]
      (is (instance? datahike.db.AsOfDB (store/datastore response)))))

  (testing "db-datetime gracefully handles bad date strings"
    (let [handler (db-config->handler
                    (assoc config
                           :db/req->timepoint store/db-datetime))
          response (handler {:uri "/"
                             :params {:as-of "nonsense date string"}})]
      (is (instance? datahike.db.DB (store/datastore response)))))

  (testing "it honors a custom ::timepoint callback"
    (let [->timepoint (constantly (java.util.Date.))
          config (assoc config :db/req->timepoint ->timepoint)
          app (plugins->loaded [(store/plugin config)])]
      (is (instance? datahike.db.AsOfDB (store/datastore app))))))

(comment
  (require '[kaocha.repl :as k])
  (k/run *ns*))
