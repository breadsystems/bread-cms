(ns systems.bread.alpha.tools.debug.server
  (:require
    [clojure.core.async :as async :refer [<! chan go-loop mult put! tap untap]]
    [clojure.edn :as edn]
    [org.httpkit.server :as http]
    [reitit.ring :as ring]
    [systems.bread.alpha.tools.debug.middleware :as mid])
  (:import
    [java.util UUID]))

;; PUBLISH to events>
(def ^:private events> (chan 1))
;; SUBSCRIBE to <events
(def ^:private <events (mult events>))

(defn publish!
  "Publishes e to the client identified by cid (attached via subscribe!)"
  ([e]
   (put! events> e))
  ([e client-id]
   (put! events> (with-meta e {:client-id client-id}))))

(defn subscribe!
  "Subscribes (taps) to a mult of the <events channel, attaching f as a handler.
  Returns an unsubscribe callback that closes around the mult (calls untap)."
  [client-id f]
  (let [listener (chan 1 (filter (fn [e]
                                   (let [{ecid :client-id} (meta e)]
                                     (or (nil? ecid)
                                         (= client-id ecid))))))]
    (tap <events listener)
    (go-loop []
             (let [e (<! listener)]
               (f e)
               (recur)))
    (fn []
      (untap <events listener))))

(defn- ws-handler [ws-on-message]
  (fn [req]
    (http/with-channel req ws-chan
      (let [client-id (str (UUID/randomUUID))]
        (println "Debug WebSocket connection created...")
        (http/on-close ws-chan (fn [status]
                                 (println "channel closed:" status)))
        (http/on-receive ws-chan (fn [message]
                                   (let [msg (edn/read-string message)]
                                     (ws-on-message client-id msg))))
        (subscribe! client-id (fn [event]
                                ;; TODO transit
                                (http/send! ws-chan (prn-str event))))))))

(defn handler [{:keys [csp-ports ws-on-message]}]
  (ring/ring-handler
    (ring/router
      [["/ping" (constantly {:status 200 :body "pong"})]
       ["/ws" (ws-handler ws-on-message)]])

    (ring/routes
      (mid/wrap-websocket-csp-header
        (ring/create-resource-handler {:path "/"
                                       :root "debug"})
        csp-ports)
      (ring/create-default-handler
        ;; TODO why isn't this working?
        {:not-found (fn [_]
                      (prn 404)
                      {:status 404
                       :headers {"content-type" "text/plain"}
                       :body "404 Not Found"})}))))

(defn start [{:keys [http-port csp-ports ws-on-message]}]
  (loop [port (or http-port 1316)]
    (let [ports (concat [port] csp-ports)
          handler (handler {:csp-ports ports
                            :ws-on-message ws-on-message})
          stop-srv (try
                     ;; TODO proper logging
                     (printf "Starting debug server on port %d\n" port)
                     ;; TODO return [stop-srv port]
                     (http/run-server handler {:port port})
                     (catch java.net.BindException e
                       (printf "%s: %s\n" (.getMessage e) port)))]
      (or stop-srv (recur (inc port))))))
