(ns systems.bread.alpha.tools.debug
  (:require
    [asami.core :as d]
    [clojure.datafy :refer [datafy nav]]
    [clojure.core.protocols :as proto :refer [Datafiable Navigable]]
    [clojure.tools.logging :as log]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.tools.debug.db :as db]
    [systems.bread.alpha.tools.debug.server :as srv])
  (:import
    [java.util UUID Date]))

(defprotocol BreadDebugger
  (start [debugger opts])
  (profile [debugger e] [debugger e opts]))

(defprotocol ObservableDebugger
  (subscribe [debugger query])
  (broadcast [debugger])
  (unsubscribe [debugger query]))

(def event-data nil)
(defmulti event-data (fn [e]
                       (::bread/profile.type e)))

(defmethod event-data :profile.type/request [{req ::bread/profile}]
  ;(prn (keys req))
  {:request/uuid (:request/uuid req)
   :request/method (:request-method req)
   :request/uri (:uri req)})

(defmethod event-data :profile.type/response [{res ::bread/profile}]
  ;(prn (keys res))
  (select-keys res [:request/uuid #_#_:request-method :uri]))

;; TODO client ID
(defmulti handle-message (fn [_ [k]] k))

(defmethod handle-message :subscribe [debugger [_ query]]
  (prn :subscribe query)
  (subscribe debugger query))

(defmethod handle-message :unsubscribe [debugger [_ query]]
  (prn :unsubscribe query)
  (unsubscribe debugger query))

(defrecord HttpDebugger [conn replay-handler subscriptions]
  BreadDebugger
  (start [this opts]
    (let [stop-server (srv/start (assoc opts
                                        :ws-on-message
                                        (fn [msg]
                                          ;; TODO accept client ID here
                                          (handle-message this msg))))
          tap (bread/add-profiler (fn [e]
                                    (profile this e)))]
      (fn []
        (remove-tap tap)
        (stop-server))))
  (profile [this e]
    (d/transact conn {:tx-data [(event-data e)]})
    (broadcast this))
  (profile [this e _]
    (profile this e))

  ;; TODO make this a normal event handler
  #_
  (replay [this req]
    (when (fn? replay-handler)
      (replay-handler req)))

  ObservableDebugger
  (subscribe [this query]
    (swap! subscriptions assoc query nil))
  (broadcast [this]
    (doall (for [[query old] @subscriptions]
             (let [v (db/pull query (d/db conn))]
               (when (not= (hash v) old)
                 (srv/publish! [query v]))
               (swap! subscriptions assoc query (hash v))))))
  (unsubscribe [this query]
    (swap! subscriptions dissoc query)))

(defn debugger
  ([]
   (debugger {}))
  ([{:keys [db-uri replay-handler]}]
   (let [db-uri (or db-uri (format "asami:mem://%s"
                                   (str (UUID/randomUUID))))]
     (printf "Connecting to Asami: %s\n" db-uri)
     (HttpDebugger. (d/connect db-uri) replay-handler (atom {})))))

(comment
  (db/find-pull [:request/uuid :request/uri :request/method])

  (def conn (d/connect "asami:mem://debugdb"))

  (def reqs' [:request/uuid :request/uri :request/method])
  (subscribe reqs')
  (broadcast conn)
  (unsubscribe reqs')

  (deref subscribers)
  (reset! subscribers {})

  (d/q (db/find-pull reqs')
       (d/db conn)))

(defn plugin []
  (fn [app]
    (bread/add-hooks->
      app
      (:hook/request
        (fn [req]
          (let [rid (UUID/randomUUID)
                as-of-param (bread/config req :datastore/as-of-param)
                ;; The request either has a timepoint set by virtue of having
                ;; an `as-of` param, OR its db is a vanilla DB instance from
                ;; which we can grab a max-tx.
                as-of (or (store/timepoint req) (store/max-tx req))
                req (assoc req
                           :profiler/profiled? true
                           :profiler/as-of-param as-of-param
                           :request/uuid rid
                           :request/as-of as-of
                           :request/timestamp (Date.))]
            (bread/profile> :profile.type/request req)
            req))
        {:precedence Double/NEGATIVE_INFINITY})
      (:hook/response
        (fn [res]
          (let [res (assoc res :response/timestamp (Date.))]
            (bread/profile> :profile.type/response res)
            res))
        {:precedence Double/POSITIVE_INFINITY}))))

(comment
  (def stop (start (debugger {}) {:http-port 1316
                                  :csp-ports [9630]}))

  (stop)


  ;; Navigable sandbox
  (def reqs (with-meta
              [{:uri "/"}]
              {`proto/nav (fn [reqs k v]
                            (assoc v :nav/at k :nav/reqs reqs))}))
  (def req (first reqs))
  (nav reqs 0 req)

  ;; Let's get wild...
  (nav (:nav/reqs (nav reqs 0 req)) 0 req)


  (import '[java.util UUID])

  (def db-uri (format "asami:mem://%s" (str (UUID/randomUUID))))
  (def conn (d/connect db-uri))

  (def u1 (UUID/randomUUID))
  (def u2 (UUID/randomUUID))
  (def res (d/transact conn {:tx-data [{:request/uuid u1
                                        :request/uri "/"}
                                       {:request/uuid u2
                                        :request/uri "/en"}]}))
  (deref res)

  (def db (d/db conn))

  (defn $requests []
    (d/q '{:find [?uuid ?uri]
           :where [[?e :request/uri ?uri]
                   [?e :request/uuid ?uuid]]}
         db))

  (defn $uuid->request [uuid]
    (d/q '{:find [?uuid ?uri]
           :in [$ ?uuid]
           :where [[?e :request/uri ?uri]
                   [?e :request/uuid ?uuid]]}
         db uuid))

  ($requests)
  ($uuid->request u1)
  ($uuid->request u2)

  (slurp "http://localhost:1312/en/")
  (slurp "http://localhost:1312/fr/")
  )
