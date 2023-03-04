(ns systems.bread.alpha.datastore-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]])
  (:import
    [clojure.lang ExceptionInfo]))


(deftest test-connect!
  (testing "it gives a friendly error message if you forget :datastore/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"No :datastore/type specified in datastore config!"
          (store/connect! {:datastore/typo :datahike}))))

  (testing "it gives a friendly error message if you pass a bad :datastore/type"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Unknown :datastore/type `:oops`! Did you forget to load a plugin\?"
          (store/connect! {:datastore/type :oops})))))

(deftest test-add-txs-adds-an-effect
  (let [conn {:fake :db}]
    (are
      [effects args]
      (= effects (let [app (plugins->loaded
                             [{:config
                               {:datastore/connection conn}}])]
                   (::bread/effects (apply store/add-txs app args))))

      [{:effect/name ::store/transact
        :effect/description "Run database transactions"
        :effect/key nil
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions]]

      [{:effect/name ::store/transact
        :effect/description "Custom description"
        :effect/key nil
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions] {:description "Custom description"}]

      [{:effect/name ::store/transact
        :effect/description "Custom description"
        :effect/key 1234
        :conn conn
        :txs [:some :fake :transactions]}]
      [[:some :fake :transactions] {:description "Custom description"
                                    :key 1234}])))

(deftest test-datastore-query
  (are
    [result state]
    (= result (let [{:keys [data query-args run-q]} state
                    db (reify store/TemporalDatastore
                         (q [_ a] (run-q a))
                         (q [_ a b] (run-q a b))
                         (q [_ a b c] (run-q a b c)))
                    query {:query/name ::store/query
                           :query/db db
                           :query/args query-args}]
                (bread/query query data)))

    false
    {:data {}
     :query-args [{}]
     :run-q (constantly nil)}

    {:db/id 123}
    {:data {}
     :query-args [{} {}]
     :run-q (constantly {:db/id 123})}

    ;; Test that data lookup paths in query args work. This mechanism gives us
    ;; a decomplected way to refer to data that was previously queried but
    ;; whose value we don't know at query generation time.
    :RESULT
    {:data {:x :RESULT}
     :query-args [{} [::bread/data :x]]
     :run-q (fn [_ arg] arg)}

    ;; Ditto above, except now the value at (butlast path) is a sequence. In
    ;; this case, we want to map the sequence over (last path).
    [1 2 3]
    {:data {:x [{:k 1} {:k 2} {:k 3}]}
     :query-args [{} [::bread/data :x :k]]
     :run-q (fn [_ arg] arg)}

    ;; Ditto above, except now the value at (butlast path) is a sequence. In
    ;; this case, we want to map the sequence over (last path).
    false
    {:data {:x :y}
     :query-args [{}]
     :run-q (fn [_] (throw (ex-info "something bad happened." {})))}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
