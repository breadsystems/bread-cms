;; Implement the WebSocket backend for the Marx editor.
(ns systems.bread.alpha.marx.websocket
  (:require
    [clojure.math :refer [pow]]

    [systems.bread.alpha.marx.core :as core]))

(deftype WebSocketBackend [^:unsynchronized-mutable ws
                           ^:unsynchronized-mutable retry-count]
  core/StatefulBackend
  (init-backend! [this config]
    (.addEventListener ws "open" #(set! retry-count 0))
    (.addEventListener ws "close" #(core/retry! this config)))
  (retry! [this config]
    (let [retry-delay (* 125 (pow 2 retry-count))]
      (js/setTimeout (fn []
                       (set! ws (js/WebSocket. (:uri config)))
                       (set! retry-count (inc retry-count))
                       (core/init-backend! this config))
                     retry-delay)))

  core/MarxBackend
  (persist! [_ data]
    (.send ws (pr-str data))))

(defmethod core/backend :bread/websocket [config]
  (doto (WebSocketBackend. (js/WebSocket. (:uri config)) 0)
    (core/init-backend! config)))
