(ns systems.bread.alpha.marx.api
  (:require
    ["react" :as react]
    [clojure.math :refer [pow]]

    [systems.bread.alpha.marx.field.bar :as bar]
    [systems.bread.alpha.marx.field.rich-text]
    [systems.bread.alpha.marx.core :as core]))

(defn from-meta-element
    ([editor-name]
   (let [selector (str "meta[name=\"" (name editor-name) "\"]")]
     (core/read-attr (js/document.querySelector selector) "content")))
  ([]
   (from-meta-element "marx-editor")))

(defn editor [config]
  (assoc config :marx/fields {}))

(defn elements [ed]
  (map :elem (vals (:marx/fields ed))))

(def bar-section bar/bar-section)

(defmulti backend :type)

(defprotocol StatefulBackend
  (init-backend! [this config])
  (retry! [this config]))

(deftype WebSocketBackend [^:unsynchronized-mutable ws
                           ^:unsynchronized-mutable retry-count]
  StatefulBackend
  (init-backend! [this config]
    (.addEventListener ws "open" #(set! retry-count 0))
    (.addEventListener ws "close" #(retry! this config)))
  (retry! [this config]
    (let [retry-delay (* 125 (pow 2 retry-count))]
      (js/setTimeout (fn []
                       (set! ws (js/WebSocket. (:uri config)))
                       (set! retry-count (inc retry-count))
                       (init-backend! this config))
                     retry-delay)))

  core/MarxBackend
  (persist! [_ data]
    (.send ws (pr-str data))))

(defmethod backend :bread/websocket [backend-config]
  (doto (WebSocketBackend. (js/WebSocket. (:uri backend-config)) 0)
    (init-backend! backend-config)))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (core/fields-from-editor ed)
                 (core/fields-from-dom config))
        ed-state @ed]
    (when (nil? (:marx/backend ed-state))
      (core/attach-backend! ed (backend (:backend ed-state))))
    (doseq [field fields]
      (core/init-field ed field))))
