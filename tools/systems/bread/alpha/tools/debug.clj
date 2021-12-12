(ns systems.bread.alpha.tools.debug
  (:require
    [clojure.datafy :refer [datafy nav]]
    [clojure.core.protocols :as proto :refer [Datafiable Navigable]]
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.tools.protocols]
    [systems.bread.alpha.tools.debug.server :as srv])
  (:import
    [java.util UUID Date]))

(defprotocol BreadDebugger
  (start [debugger opts])
  (profile [debugger e] [debugger e opts])
  (event-log [debugger]))

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

(defrecord WebsocketDebugger [config]
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
    ;; TODO fix log persistence
    (swap! (:event-log config) conj pe)
    (srv/publish!
      [(::bread/profile.type pe)
       (walk/prewalk datafy (event-data pe))]))
  (profile [this e _]
    (profile this e))
  (event-log [this]
    (deref (:event-log config))))

(defn debugger
  ([]
   (debugger {}))
  ([config]
   (let [config (merge {:event-log (atom [])} config)]
     (WebsocketDebugger. config))))

(comment
  (let [dbg (debugger)]
    (profile dbg {::bread/profile.type :something ::bread/profile {}})
    (event-log (debugger)))
  (event-log (debugger))
  (event-log (debugger {:event-log (atom [:stuff])})))

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
  (srv/publish! [:test (walk/prewalk datafy {:fn (fn [] 'hello)})])

  (slurp "http://localhost:1312/en/")
  (slurp "http://localhost:1312/fr/")
  )
