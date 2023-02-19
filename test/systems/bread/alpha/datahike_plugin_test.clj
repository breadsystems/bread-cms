(ns systems.bread.alpha.datahike-plugin-test
  (:require
    [clojure.test :refer :all]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.plugin.datahike :as plugin]
    [systems.bread.alpha.test-helpers :as h :refer [datastore-config->loaded
                                                    plugins->loaded]]))


(let [config {:datastore/type :datahike
              :store {:backend :mem
                      :id "plugin-db"}}
      ->loaded #(h/datastore-config->loaded %)
      ->handler #(h/datastore-config->handler %)
      handle (fn [req]
               ((->handler config) req))]

  (h/use-datastore :each config)

  (deftest test-datahike-plugin

    (testing "it configures as-of-param"
      (let [app (handle {})]
        (is (= :as-of (bread/config app :datastore/as-of-param)))))

    (testing "it honors custom as-of-param"
      (let [app (->loaded (assoc config :datastore/as-of-param :my/param))]
        (is (= :my/param (bread/config app :datastore/as-of-param)))))

    (testing "it configures db connection"
      (let [app (->loaded config)]
        (is (instance? clojure.lang.Atom
                       (bread/config app :datastore/connection)))))

    (testing ":hook/datastore returns the present snapshot by default"
      (let [app (->loaded config)]
        (is (instance? datahike.db.DB (bread/hook app :hook/datastore)))))

    (testing "db-tx honors as-of-param"
      (let [app (datastore-config->loaded config)
            db (store/datastore app)
            handler (bread/handler app)
            max-tx (store/max-tx app)
            response (handler {:uri "/" :params {:as-of (dec max-tx)}})]
        (is (instance? datahike.db.AsOfDB (store/datastore response)))))

    (testing "db-tx gracefully handles non-numeric strings"
      (let [handler (->handler config)
            response (handler {:uri "/" :params {:as-of "garbage"}})]
        (is (instance? datahike.db.DB (store/datastore response)))))

    (testing "db-datetime honors as-of param"
      (let [handler (->handler
                      (assoc config
                             :datastore/req->timepoint store/db-datetime))
            response (handler {:uri "/"
                               ;; pass a literal date here
                               :params {:as-of "2020-01-01 00:00:00 PDT"}})]
        (is (instance? datahike.db.AsOfDB (store/datastore response)))))

    (testing "db-datetime honors as-of-format config"
      (let [handler (->handler
                      (assoc config
                             :datastore/as-of-format "yyyy-MM-dd"
                             :datastore/req->timepoint store/db-datetime))
            response (handler {:uri "/"
                               ;; pass a literal date here
                               :params {:as-of "2020-01-01"}})]
        (is (instance? datahike.db.AsOfDB (store/datastore response)))))

    (testing "db-datetime gracefully handles bad date strings"
      (let [handler (->handler
                      (assoc config
                             :datastore/req->timepoint store/db-datetime))
            response (handler {:uri "/"
                               :params {:as-of "nonsense date string"}})]
        (is (instance? datahike.db.DB (store/datastore response)))))

    (testing "it honors a custom :hook/datastore.req->timepoint callback"
      (let [->timepoint (constantly (java.util.Date.))
            config (assoc config :datastore/req->timepoint ->timepoint)
            app (h/plugins->loaded [(store/plugin config)])]
        (is (instance? datahike.db.AsOfDB (store/datastore app)))))))

(comment
  (require '[kaocha.repl :as k])
  (k/run *ns*))
