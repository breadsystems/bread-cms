(ns systems.bread.alpha.tools.debug.core
  (:refer-clojure :exclude [*e])
  (:require
    [clojure.datafy :refer [datafy nav]]
    [clojure.core.protocols :as proto :refer [Datafiable Navigable]]
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.tools.protocols]
    [systems.bread.alpha.tools.debug.server :as srv])
  (:import
    [java.util UUID Date]))

(def ^:dynamic *e nil)

(defprotocol BreadDebugger
  (start [debugger opts])
  (profile [debugger e] [debugger e opts])
  (replay [debugger req opts]))

(def event-data nil)
(defmulti event-data (fn [[event-type]]
                       event-type))

(defmethod event-data :default [[_ e]]
  e)

(defmethod event-data :profile.type/request
  [[_ {uuid :request/uuid :as req}]]
  (assoc req
         :request/uuid (str uuid)
         :request/millis (.getTime (Date.))
         ;; TODO support extending these fields via metadata
         :request/database (db/database req)))

(defmethod event-data :profile.type/response
  [[_ {uuid :request/uuid :as res}]]
  (assoc res
         :request/uuid (str uuid)
         ;; TODO compute duration?
         :response/database (db/database res)))

(defmethod event-data :profile.type/hook
  [[_ {:keys [hook args app f result millis]
       {::bread/keys [file line column from-ns precedence]} :detail}]]
  {:hook/uuid (str (UUID/randomUUID))
   :hook/name hook
   :hook/result result
   :hook/request (update app :request/uuid str)
   :hook/args args
   :hook/f f
   :hook/file file
   :hook/line line
   :hook/column column
   :hook/from-ns from-ns
   :hook/precedence precedence
   :hook/millis millis})

(defmulti handle-message (fn [_ _ [k]] k))

(defmethod handle-message :replay-event-log [debugger client-id _]
  (doseq [entry @(.log debugger)]
    (srv/publish! entry client-id)))

(defmethod handle-message :clear-debug-log [debugger & _]
  (reset! (.log debugger) []))

(defmethod handle-message :replay-requests [debugger _ [_ reqs opts]]
  (replay debugger reqs opts))

(defrecord WebsocketDebugger [log config]
  BreadDebugger
  (start [this opts]
    (when-not (false? (:enable-profiling opts))
      (alter-var-root #'bread/*enable-profiling* (constantly true)))
    (let [stop-server (srv/start
                        (assoc opts
                               :ws-on-message
                               (fn [client-id msg]
                                 (handle-message this client-id msg))))
          tap (bread/add-profiler
                (fn [hook-event]
                  (profile this hook-event)))]
      (fn []
        (println "Stopping debug server.")
        (remove-tap tap)
        (stop-server))))
  ;; Publish the given profiler event to the Websocket connection.
  (profile [this pe]
    (let [{t ::bread/profile.type e ::bread/profile} pe
          entry [t (walk/prewalk datafy (event-data [t e]))]]
      (swap! (.log this) conj entry)
      (srv/publish! entry)))
  (profile [this e _]
    (profile this e))
  (replay [this reqs opts]
    (let [handler (:replay-handler config)]
      (when-not (fn? handler)
        (throw (ex-info "replay-handler is not a function"
                        {:replay-handler handler})))
      (let [{:replay/keys [as-of?]} opts
            reqs (if as-of?
                   (map (fn [{param :profiler/as-of-param
                              as-of :request/as-of
                              :as req}]
                          (assoc-in req [:params param] as-of))
                          reqs)
                   reqs)]
        (doseq [req reqs]
          (handler req))))))

(defn debugger
  ([log]
   (debugger log {}))
  ([log config]
   (WebsocketDebugger. log config)))

(defmethod bread/action ::request
  [req _ _]
  ;; TODO support passing a in a fn for filtering request data
  (let [;; TODO parameterize getting IDs
        rid (UUID/randomUUID)
        as-of-param (bread/config req :db/as-of-param)
        as-of (or (db/timepoint req) (db/max-tx req))
        req (assoc req
                   :profiler/profiled? true
                   :profiler/as-of-param as-of-param
                   :request/uuid rid
                   :request/as-of as-of
                   :request/timestamp (Date.))]
    (bread/profile> :profile/type.request req)
    req))

(defmethod bread/action ::response
  [res _ _]
  (let [res (assoc res :response/timestamp (Date.))]
    (bread/profile> :profile.type/response res)
    res))

(defn plugin []
  {:hooks
   {::bread/request
    [{:action/name ::request
      :action/description
      "Profile each new request to Bread, to send it to the debugger"}]
    ::bread/response
    [{:action/name ::response
      :action/description
      "Profile the full response, for correlating in the debugger"}]}})

(comment
  (srv/publish! [:test (walk/prewalk datafy {:fn (fn [] 'hello)})])

  (slurp "http://localhost:1312/en/")
  (slurp "http://localhost:1312/fr/")
  )
