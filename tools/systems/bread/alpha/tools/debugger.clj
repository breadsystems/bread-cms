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
    [rum.core :as rum])
  (:import
    [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(defonce stop-debugger (atom nil))
(defonce stop-ws-server (atom nil))
(defonce !ws-port (atom 1314))
(defonce !shadow-cljs-port (atom 9630))

(def <hooks (chan))

(comment
  (put! <hooks "456"))

(defn profile! []
  (bread/bind-profiler!
    (fn [hook-invocation]
      (go (>! <hooks hook-invocation)))))

(defn debugger-ui [req]
  {:status 200
   :headers {"Content-Security-Policy"
             (str "connect-src 'self' ws://localhost:" @!ws-port ";")}
   :body "hello!?"})

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

(defn ws-handler [req]
  (http/with-channel req ws-chan
    (println "Debug WebSocket connection created.")
    (http/on-close ws-chan (fn [status]
                             (println "channel closed:" status)))
    (http/on-receive ws-chan (fn [data]
                               (http/send! ws-chan data)))
    (go-loop []
             ;; TODO do this in a more dynamic way?
             (let [{:keys [hook f args detail uuid] :as inv} (<! <hooks)
                   {::bread/keys [from-ns file line column]} detail
                   event {:event/type :bread/hook
                          :uuid (str uuid)
                          :hook hook
                          :args (map prn-str args)
                          :f (str f)
                          :line line
                          :column column}]
               (swap! hooks!? conj event)
               (http/send! ws-chan
                           (prn-str event))
               (recur)))))

(comment
  (reset! hooks!? [])
  (-> @hooks!? last prn-str edn/read-string)
  (map :detail @hooks!?))

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
    (bread/add-hook app :hook/request #(assoc % :uuid (uuid)))))
