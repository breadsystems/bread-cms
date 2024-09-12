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

(defrecord WebSocketBackend [ws]
  core/MarxBackend
  (init-backend! [_]
    (.addEventListener ws "open" (fn [x] (js/console.log "OPEN" x)))
    (.addEventListener ws "message" (fn [msg] (prn 'message msg)))
    (.addEventListener ws "close" (fn [] (js/console.log "WEBSOCKET CLOSED"))))
  (persist! [_ data]
    (.send ws (pr-str data))))

(defmulti backend :type)

(defmethod backend :bread/websocket [backend-config]
  (WebSocketBackend. (js/WebSocket. (:uri backend-config))))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (core/fields-from-editor ed)
                 (core/fields-from-dom config))
        marx-backend (backend (:marx/backend @ed))]
    (core/init-backend! marx-backend)
    (swap! ed assoc :marx/backend marx-backend)
    (doseq [field fields]
      (core/init-field ed field))))
