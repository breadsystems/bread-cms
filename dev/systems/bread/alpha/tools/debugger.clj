(ns systems.bread.alpha.tools.debugger
  (:require
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [reitit.core :as reitit]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]]
    [rum.core :as rum])
  (:import
    [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(defonce stop-debugger (atom nil))
(defonce stop-ws-server (atom nil))
(defonce !ws-port (atom 1314))

(defn handler [req]
  {:status 200
   :headers {"Content-Security-Policy"
             (str "connect-src 'self' ws://localhost:" @!ws-port ";")}
   :body "hello?"})

(defn ws-handler [req]
  (http/with-channel req channel
    (http/on-close channel (fn [status]
                             (println "channel closed:" status)))
    (http/on-receive channel (fn [data]
                               (http/send! channel data)))))

(defn start! [{:keys [port websocket-port]}]
  (let [port (Integer. (or port 1313))
        ws-port (Integer. (or websocket-port 1314))]
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
  (start! (merge {:port 1313 :websocket-port 1314} opts)))
