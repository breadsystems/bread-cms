(ns systems.bread.alpha.schema-test
  (:require
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.datastore.datahike :as plugin]
    [systems.bread.alpha.schema :as schema]
    [clojure.test :refer [deftest is use-fixtures]])
  (:import
    [java.util UUID]))

(defn- uuid [] (UUID/randomUUID))

;; Set up a bunch of boilerplate to share between tests.
(let [config {:datastore/type :datahike
              :store          {:backend :mem :id "postdb"}}

      datahike-fixture (fn [run]
                         ;; Clean up after any prior failures, just in case.
                         (store/delete-database! config)
                         (store/create-database! config)
                         (try
                           (let [conn (store/connect! config)]
                             (store/transact conn (schema/initial-schema)))
                           (run)
                           (catch Throwable e
                             (throw e))
                           ;; Eagerly clean up after ourselves.
                           (finally (store/delete-database! config))))

      angela #:post{:uuid  (uuid)
                    :slug  "angela"
                    :title "Angela Davis"
                    :type  :post.type/revolutionary}
      bobby #:post{:uuid  (uuid)
                   :slug  "bobby"
                   :title "Bobby Seal"
                   :type  :post.type/revolutionary}
      init-db (fn []
                (let [conn (store/connect! config)]
                  (prn 'transact angela bobby)
                  (store/transact conn [angela bobby])
                  conn))]

  ;; Start each test with a blank-slate database.
  (use-fixtures :each datahike-fixture)

  (deftest test-q

    (let [conn (init-db)]
      (is (= #{["angela" :post.type/revolutionary "Angela Davis"]}
             (store/q (store/db conn)
                      '[:find ?slug ?type ?title
                        :where
                        [?e :post/slug "angela"]
                        [?e :post/slug ?slug]
                        [?e :post/type ?type]
                        [?e :post/title ?title]]
                      [])))))

  #_(deftest test-pull

    (let [conn (init-db)]
      (is (= #:post{:title "Angela Davis"
                    :slug  "angela"
                    :type  :post.type/revolutionary}
             (store/pull (store/db conn)
                         '[:post/title :post/slug :post/type]
                         [:post/uuid (:post/uuid angela)])))))

  #_(deftest test-post-api-basic

    (let [app (-> (bread/app {:plugins [(plugin/datahike-plugin
                                         {:datahike config})]})
                  bread/load-plugins)]
      (prn ((juxt :db-before :db-after) (store/add-post! app angela)))
      (prn (store/datastore app))
      (let [post (store/slug->post app "angela")]
        (is (= "Angela Davis" (:post/title post)))))))