(ns systems.bread.alpha.tools.debug
  (:require
    [asami.core :as d]
    [clojure.datafy :refer [datafy nav]]
    [clojure.core.protocols :as proto :refer [Datafiable Navigable]]
    [clojure.tools.logging :as log]
    [systems.bread.alpha.tools.debug.server :as srv])
  (:import
    [java.util UUID]))

(defprotocol BreadDebugger
  (start [debugger opts])
  (replay [debugger req]))

(defrecord HttpDebugger [conn replay-handler]
  BreadDebugger
  (start [this opts]
    (let [stop-server (srv/start opts)]
      ;; TODO add-tap
      (fn []
        ;; TODO remove-tap
        (stop-server))))
  (replay [this req]
    (when (fn? replay-handler)
      (replay-handler req))))

(defn debugger
  ([]
   (debugger {}))
  ([{:keys [db-uri replay-handler]}]
   (let [db-uri (or db-uri (format "asami:mem://%s"
                                   (str (UUID/randomUUID))))]
     (printf "Connecting to Asami: %s\n" db-uri)
     (HttpDebugger. (d/connect db-uri) replay-handler))))

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
  )
