(ns systems.bread.alpha.marx.api
  (:require
    ["react" :as react]

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

(deftype WebSocketBackend [^:unsynchronized-mutable ws]
  StatefulBackend
  (init-backend! [this config]
    (.addEventListener ws "open" (fn [x] (js/console.log "OPEN" x)))
    (.addEventListener ws "message" (fn [msg] (prn 'message msg)))
    (.addEventListener ws "close"
                       (fn []
                         (js/console.log "WEBSOCKET CLOSED")
                         (retry! this config))))
  (retry! [this config]
    (js/console.log "Retrying connection...")
    (js/setTimeout (fn []
                     (set! ws (js/WebSocket. (:uri config)))
                     (init-backend! this config))
                   1000))

  core/MarxBackend
  (persist! [_ data]
    (.send ws (pr-str data))))

(defmethod backend :bread/websocket [backend-config]
  (doto (WebSocketBackend. (js/WebSocket. (:uri backend-config)))
    (init-backend! backend-config)))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (core/fields-from-editor ed)
                 (core/fields-from-dom config))
        backend-inst (backend (:backend @ed))]
    (core/attach-backend! ed backend-inst)
    (doseq [field fields]
      (core/init-field ed field))))
