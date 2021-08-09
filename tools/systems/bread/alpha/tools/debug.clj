(ns systems.bread.alpha.tools.debug
  (:require
    [asami.core :as d]
    [clojure.datafy :refer [datafy nav]]
    [clojure.core.protocols :as proto :refer [Datafiable Navigable]]
    [org.httpkit.server :as http]
    [reitit.ring :as ring]))

(defprotocol BreadDebugger
  (start [debugger port])
  (replay [debugger req]))

(def ^:private handler
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200 :body "pong"})]
       #_
       ["/ws" ws-handler]])

    (ring/routes
      #_
      (wrap-csp-header
        (ring/create-resource-handler {:path "/"
                                       :root "debug"}))
      ;; TODO remove this in favor or wrapped CSP resource handler
      (ring/create-resource-handler {:path "/"
                                     :root "debug"})
      (ring/create-default-handler
        {:not-found (constantly {:status 404
                                 :body "404 Not Found"})}))))

(deftype HttpDebugger [conn replay-handler]
  BreadDebugger
  (start [this port]
    (let [stop-server
          (http/run-server handler {:port port})
          ]
      (fn []
        (stop-server))))
  (replay [this req]
    (when (fn? replay-handler)
      (replay-handler req))))

(defn debugger [{:keys [db-uri replay-handler]}]
  (let [db-uri (or db-uri (format "asami:mem://%s" (str (UUID/randomUUID))))]
    (HttpDebugger. (d/connect db-uri) replay-handler)))

(comment
  (test-debug-srv)
  (defn test-debug-srv []
    (let [stop (start (debugger {}) 1397)
          html (slurp "http://localhost:1397")]
      (stop)
      (prn html)
      (assert (= "Hello, Bread!"))))


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
