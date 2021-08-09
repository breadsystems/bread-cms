(ns systems.bread.alpha.tools.debug.server
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [org.httpkit.server :as http]
    [reitit.ring :as ring]))

(defn- wrap-csp-header [handler ports]
  (fn [req]
    (update
      (handler req) :headers merge
      {"Content-Security-Policy"
       (str
         "connect-src 'self' "
         (string/join " " (map #(format "ws://localhost:%s" %) ports)))})))

(defn- ws-handler [req]
  (http/with-channel req ws-chan
    (println "Debug WebSocket connection created...")
    (http/on-close ws-chan (fn [status]
                             (println "channel closed:" status)))
    (http/on-receive ws-chan (fn [message]
                               (let [msg (edn/read-string message)]
                                 (prn 'message msg)
                                 #_
                                 (on-event (assoc msg :channel ws-chan)))))
    ;; TODO Broadcast over our WebSocket whenever there's an event!
    #_
    (impl/subscribe! (fn [event]
                       (http/send! ws-chan (prn-str event))))))

(comment
  (def h (ring/create-resource-handler {:path "/"
                                        :root "debug"}))
  (alength (.getParameterTypes (first (.getDeclaredMethods (class h))))))

(defn handler [{:keys [csp-ports]}]
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200 :body "pong"})]
       ["/ws" ws-handler]])

    (ring/routes
      (wrap-csp-header
        (ring/create-resource-handler {:path "/"
                                       :root "debug"})
        csp-ports)
      #_
      (ring/create-default-handler
        {:not-found (fn [_]
                      {:status 404
                                 :body "404 Not Found"})}))))

(defn start [{:keys [http-port shadow-port] :as opts}]
  (loop [port (or http-port 1316)]
    (prn 'port= port)
    (let [handler (handler {:csp-ports [port (or shadow-port 9630)]})
          stop-srv (try
                     ;; TODO accept alternative shadow-cljs port
                     ;; TODO proper logging
                     (printf "Starting debug server on port %d.\n" port)
                     (http/run-server handler {:port port})
                     (catch java.net.BindException e
                       (printf "%s: %s\n" (.getMessage e) port)))]
      (or stop-srv (recur (inc port))))))
