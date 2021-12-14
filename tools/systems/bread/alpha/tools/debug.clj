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
  (profile [debugger e] [debugger e opts]))

(def event-data nil)
(defmulti event-data (fn [[event-type]]
                       event-type))

(defmethod event-data :default [[_ e]]
  e)

(defmethod event-data :profile.type/request
  [[_ {uuid :request/uuid :as req}]]
  (assoc req
         :request/uuid (str uuid)
         ;; TODO support extending these fields via metadata
         :request/datastore (store/datastore req)))

(defmethod event-data :profile.type/response [[_ res]]
  (select-keys res [:request/uuid #_#_:request-method :uri]))

(defmethod event-data :profile.type/hook
  [[_ {:keys [hook args app f]
       {::bread/keys [file line column from-ns precedence]} :detail}]]
  {:hook/uuid (UUID/randomUUID)
   :hook/request app
   :hook/name hook
   :hook/args args
   :hook/f f
   :hook/file file
   :hook/line line
   :hook/column column
   :hook/from-ns from-ns
   :hook/precedence precedence})

;; TODO client ID?
(defmulti handle-message (fn [_ [k]] k))

(defmethod handle-message :replay-event-log [debugger _]
  (doseq [entry @(.log debugger)]
    (srv/publish! entry)))

(defrecord WebsocketDebugger [log config]
  BreadDebugger
  (start [this opts]
    (let [stop-server (srv/start
                        (assoc opts
                               :ws-on-message
                               (fn [msg]
                                 (handle-message this msg))))
          tap (bread/add-profiler
                (fn [hook-event]
                  (profile this hook-event)))]
      (fn []
        (remove-tap tap)
        (stop-server))))
  ;; Publish the given profiler event to the Websocket connection.
  (profile [this pe]
    (let [{t ::bread/profile.type e ::bread/profile} pe
          entry [t (walk/prewalk datafy (event-data [t e]))]]
      (swap! (.log this) conj entry)
      (srv/publish! entry)))
  (profile [this e _]
    (profile this e)))

(defn debugger
  ([log]
   (debugger log {}))
  ([log config]
   (WebsocketDebugger. log config)))

(comment
  (def events (atom []))
  (let [dbg (debugger events)]
    (profile dbg {::bread/profile.type :something ::bread/profile {}})
    @(.log dbg)))

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
