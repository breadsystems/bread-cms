(ns systems.bread.alpha.tools.debug
  (:require
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

;; TODO client ID?
(defmulti handle-message (fn [_ [k]] k))

(defrecord WebsocketDebugger [_config]
  BreadDebugger
  (start [this opts]
    (let [stop-server (srv/start
                        (assoc opts
                               :ws-on-message
                               (fn [msg]
                                 ;; TODO accept client ID here?
                                 (handle-message this msg))))
          tap (bread/add-profiler
                (fn [profile-event]
                  (profile this profile-event)))]
      (fn []
        (remove-tap tap)
        (stop-server))))
  ;; Publish the given profiler event to the Websocket connection.
  (profile [this pe]
    (srv/publish! [(::bread/profile.type pe) (::bread/profile.type pe)]))
  (profile [this e _]
    (profile this e)))

(defn debugger
  ([]
   (debugger {}))
  ([config]
   (WebsocketDebugger. config)))

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

  (slurp "http://localhost:1312/en/")
  (slurp "http://localhost:1312/fr/")
  )
