(ns systems.bread.alpha.datahike-plugin-test
  (:require
    [clojure.test :refer :all]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as plugin]))


(let [config {:datastore/type :datahike
              :datastore/initial-txns []
              :store {:backend :mem
                      :id "plugin-db"}}
      config->handler (fn [conf]
                        (-> {:plugins [(store/config->plugin conf)]}
                            (bread/app)
                            (bread/app->handler)))
      handle (fn [req]
               ((config->handler config) req))]

  (use-fixtures :each (fn [f]
                        (store/delete-database! config)
                        (store/create-database! config)
                        (store/connect! config)
                        (f)
                        (store/delete-database! config)))

  (deftest test-datahike-plugin

    (testing "it configures as-of-param"
      (let [app (handle {})]
        (is (= :as-of (bread/config app :datastore/as-of-param)))))

    (testing "it honors custom as-of-param"
      (let [app (handle {})]
        (is (= :as-of (bread/config app :datastore/as-of-param)))))

    (testing "it configures db connection"
      (let [app (handle {})]
        (is (instance? clojure.lang.Atom (bread/config app :datastore/connection)))))

    (testing ":hook/datastore returns the present snapshot by default"
      (let [response ((config->handler config) {:url "/"})]
        (is (instance? datahike.db.DB (bread/hook response :hook/datastore)))))

    (testing ":hook/datastore.req->timepoint honors as-of param"
      (let [handler (config->handler config)
            response (handler {:url "/"
                               ;; pass a literal date here
                               :params {:as-of "2020-01-01 00:00:00 PDT"}})]
        (is (instance? datahike.db.AsOfDB (bread/hook response :hook/datastore)))))

    (testing ":hook/datastore.req->timepoint honors as-of-format config"
      (let [handler (config->handler (assoc config :as-of-format "yyyy-MM-dd"))
            response (handler {:url "/"
                               ;; pass a literal date here
                               :params {:as-of "2020-01-01"}})]
        (is (instance? datahike.db.AsOfDB (bread/hook response :hook/datastore)))))

    (testing ":hook/datastore honors as-of-tx")

    (testing ":hook/datastore.req->timepoint gracefully handles bad date strings"
      (let [handler (config->handler config)
            response (handler {:url "/"
                               :params {:as-of "nonsense date string"}})]
        (is (instance? datahike.db.DB (bread/hook response :hook/datastore)))))

    (testing "it honors a custom :hook/datastore.req->timepoint callback"
      (let [->timepoint (constantly (java.util.Date.))
            config (assoc config :req->timepoint ->timepoint)
            app (bread/app {:plugins [(store/config->plugin config)]})
            handler (bread/app->handler app)
            response (handler {})]
        (is (instance? datahike.db.AsOfDB (bread/hook response :hook/datastore)))))))
