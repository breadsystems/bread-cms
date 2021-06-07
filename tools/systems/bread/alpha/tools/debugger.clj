(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [clojure.core.async :as async :refer [<! >! chan go go-loop mult put! tap]]
    [clojure.walk :as walk]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [systems.bread.alpha.core :as bread]
    [reitit.core :as reitit]
    [reitit.ring :as ring]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]]
    [systems.bread.alpha.tools.impl :as impl :refer [publish!
                                                     subscribe-db
                                                     on-event]])
  (:import
    [java.util Date UUID]))

(declare db)

(defn subscribe! []
  (let [[db' unsub!] (subscribe-db)]
    (def db db')
    unsub!))

(defn uuid []
  (UUID/randomUUID))

(defonce stop-debugger (atom nil))
(defonce stop-ws-server (atom nil))
(defonce !ws-port (atom 1314))
(defonce !shadow-cljs-port (atom 9630))

(def ^:private <hooks (chan 1))

(defn profile! []
  (bread/bind-profiler!
    (fn [hook-invocation]
      (go (>! <hooks hook-invocation)))))

(defn wrap-csp-header [handler]
  (fn [req]
    (update
      (handler req) :headers merge
      {"Content-Security-Policy"
       (format
         "connect-src 'self' ws://localhost:%s ws://localhost:%s;"
         @!ws-port
         @!shadow-cljs-port)})))

(def handler
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200 :body "pong"})]
       ["/ws-host" (fn [_]
                     {:status 200 :body (format "ws://localhost:%s"
                                                @!ws-port)})]])

    (ring/routes
      (wrap-csp-header
        (ring/create-resource-handler {:path "/"
                                       :root "debugger"}))
      (ring/create-default-handler
        {:not-found (constantly {:status 404
                                 :body "404 Not Found"})}))))

(comment
  (deref db))

(defmethod on-event :ui/init [{:keys [channel]}]
  (http/send! channel (prn-str
                        {:event/type :init
                         :state @db})))

(defn ws-handler [req]
  (http/with-channel req ws-chan
    (println "Debug WebSocket connection created...")
    (http/on-close ws-chan (fn [status]
                             (println "channel closed:" status)))
    (http/on-receive ws-chan (fn [message]
                               (let [msg (edn/read-string message)]
                                 (on-event (assoc msg :channel ws-chan)))))
    ;; TODO subscribe!
    (go-loop []
             (let [{:keys [hook f args detail app] :as inv} (<! <hooks)
                   {::bread/keys [from-ns file line column]} detail
                   ;; TODO datafy/serialize Events generically
                   event {:event/type :bread/hook
                          :request/uuid (str (:request/uuid app))
                          :app (prn-str app)
                          :hook hook
                          :args (map prn-str args)
                          :f (str f)
                          :line line
                          :column column}]
               (publish! event)
               (http/send! ws-chan (prn-str event))
               (recur)))))

(defn start! [{:keys [port websocket-port shadow-cljs-port]}]
  (let [port (Integer. (or port 1313))
        ws-port (Integer. (or websocket-port 1314))
        shadow-cljs-port (Integer. (or shadow-cljs-port 9630))]
    (println (str "Running DEBUG server at localhost:" port))
    (as-> #'handler $
      (wrap-reload $)
      (wrap-keyword-params $)
      (wrap-params $)
      (http/run-server $ {:port port})
      (reset! stop-debugger $))
    (println (str "Running Websocket server at localhost:" ws-port))
    (reset! !ws-port ws-port)
    (as-> #'ws-handler $
      (wrap-reload $)
      (http/run-server $ {:port ws-port})
      (reset! stop-ws-server $))
    (reset! !shadow-cljs-port shadow-cljs-port)
  nil))

(defn stop! []
  (println "Stopping DEBUG & Websocket server")
  (when (fn? @stop-debugger)
    (@stop-debugger))
  (when (fn? @stop-ws-server)
    (@stop-ws-server))
  (reset! stop-debugger nil)
  (reset! stop-ws-server nil))

(defn restart! [opts]
  (stop!)
  (start! (merge {:port 1313
                  :websocket-port 1314
                  :shadow-cljs-port 9630}
                 opts)))

(defn plugin []
  (fn [app]
    (bread/add-hooks->
      app
      (:hook/request #(assoc % :request/uuid (uuid)))
      (:hook/request #(assoc % :request/timestamp (Date.)))
      (:hook/response #(assoc % :response/timestamp (Date.))))))
