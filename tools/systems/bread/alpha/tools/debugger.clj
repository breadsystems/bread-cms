(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as http]
    [systems.bread.alpha.core :as bread]
    [reitit.core :as reitit]
    [reitit.ring :as ring]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.reload :refer [wrap-reload]]
    [systems.bread.alpha.tools.impl :as impl :refer [publish!
                                                     subscribe!
                                                     subscribe-db
                                                     on-event]])
  (:import
    [java.util Date UUID]))

(declare db)

(defn- subscribe-debugger []
  (let [[db' unsub!] (subscribe-db)]
    (def db db')
    unsub!))

(defonce !port (atom 1313))
(defonce !shadow-cljs-port (atom 9630))
(defonce !replay-handler (atom nil))

(defn- websocket-host []
  (str "ws://localhost:" @!port))

(defn- publish-request! [req]
  (let [uuid (str (:request/uuid req))
        req (->
              (assoc req :request/uuid uuid :request/id (subs uuid 0 8)))
        ;; TODO better serialization to avoid this
        req (-> req
                (dissoc
                  ::bread/hooks ::bread/plugins ::bread/config :async-channel))
        event {:event/type :bread/request
               :event/request req}]
    (publish! event)))

(defn- publish-response! [res]
  (let [event {:event/type :bread/response
               :event/response (dissoc res
                                       ::bread/hooks ::bread/plugins
                                       ::bread/config :async-channel
                                       ::bread/queries)}]
    (publish! event)))

(defn- hook->event [invocation]
  (when-let [rid (get-in invocation [:app :request/uuid])]
    (let [{:keys [hook f args detail app]} invocation
          {::bread/keys [from-ns file line column]} detail]
      {:event/type :bread/hook
       :request/uuid (str rid)
       :app (prn-str app)
       :hook hook
       :args (map prn-str args)
       :f (str f)
       :file file
       :line line
       :column column})))

(defn- wrap-csp-header [handler]
  (fn [req]
    (update
      (handler req) :headers merge
      {"Content-Security-Policy"
       (format
         "connect-src 'self' ws://localhost:%s ws://localhost:%s;"
         @!port
         @!shadow-cljs-port)})))

(defn- ws-handler [req]
  (http/with-channel req ws-chan
    (println "Debug WebSocket connection created...")
    (http/on-close ws-chan (fn [status]
                             (println "channel closed:" status)))
    (http/on-receive ws-chan (fn [message]
                               (let [msg (edn/read-string message)]
                                 (on-event (assoc msg :channel ws-chan)))))
    ;; Broadcast over our WebSocket whenever there's an event!
    (subscribe! (fn [event]
                  (http/send! ws-chan (prn-str event))))))

(def ^:private handler
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200 :body "pong"})]
       ["/ws" ws-handler]])

    (ring/routes
      (wrap-csp-header
        (ring/create-resource-handler {:path "/"
                                       :root "debugger"}))
      (ring/create-default-handler
        {:not-found (constantly {:status 404
                                 :body "404 Not Found"})}))))

(defmethod on-event :ui/init [{:keys [channel]}]
  (http/send! channel (prn-str
                        {:event/type :init
                         :ui/state (assoc @db
                                          :ui/websocket (websocket-host))})))

(defmethod on-event :request/replay [{req :event/request}]
  (when-let [handler @!replay-handler]
    (if (fn? handler)
      (handler req)
      (throw (ex-info "replay-handler is not a function"
                      {:replay-handler handler})))))

(defn start!
  "Starts a debug web server on the specified port and attaches the profiler.
  Returns a shutdown function that stops the server and detaches the profiler."
  [{:keys [port shadow-cljs-port replay-handler]}]
  (reset! !replay-handler replay-handler)

  ;; TODO automatically find open ports
  ;; https://github.com/thheller/shadow-cljs/blob/ba0a02aec050c6bc8db1932916009400f99d3cce/src/main/shadow/cljs/devtools/server.clj#L176
  (let [port (Integer. (or port 1313))
        shadow-cljs-port (Integer. (or shadow-cljs-port 9630))
        stop-debugger (atom nil)
        unsub (subscribe-debugger)]

    (reset! !port port)
    (reset! !shadow-cljs-port shadow-cljs-port)

    (println (str "Running debug server at localhost:" port))
    (as-> #'handler $
      (wrap-reload $)
      (wrap-keyword-params $)
      (wrap-params $)
      (http/run-server $ {:port port})
      (reset! stop-debugger $))

    (println "Binding debug profiler...")
    (bread/bind-profiler!
      (fn [invocation]
        (when-let [event (hook->event invocation)]
          (publish! event))))

    (fn []
      (println "Stopping debug server.")
      (@stop-debugger)
      (println "Unbinding profiler.")
      (bread/bind-profiler! nil)
      (unsub))))

(defn plugin []
  (fn [app]
    (bread/add-hooks->
      app
      (:hook/request
        (fn [req]
          (let [rid (UUID/randomUUID)
                req (assoc req
                           :profiler/profiled? true
                           :request/uuid rid
                           :request/timestamp (Date.))]
            (publish-request! req)
            req))
        {:precedence Double/NEGATIVE_INFINITY})
      (:hook/response
        (fn [res]
          (let [res (assoc res :response/timestamp (Date.))]
            (publish-response! res)
            res))
        {:precedence Double/POSITIVE_INFINITY}))))

(comment
  ;; RESET THE DEBUGGER DB
  (publish! {:event/type :init})

  (slurp "http://localhost:1312"))
