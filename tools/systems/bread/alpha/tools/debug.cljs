(ns systems.bread.alpha.tools.debug
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [editscript.core :as ed]
    [rum.core :as rum]
    #_#_#_
    [systems.bread.alpha.tools.debugger.diff :as diff]
    [systems.bread.alpha.tools.impl :as impl :refer [publish!
                                                     subscribe-db
                                                     on-event]]
    [systems.bread.alpha.tools.util :refer [ago
                                            date-fmt
                                            date-fmt-ms
                                            join-some
                                            pp
                                            req->url
                                            shorten-uuid]]))

(defonce event-log (atom []))

(rum/defc ui
  < rum/reactive
  []
  [:main
   [:a {:href (str "data:text/plain;charset=utf-8,"
                   (js/encodeURIComponent (prn-str (rum/react event-log))))
        :download "debug-log.edn"}
    "DOWNLOAD DEBUG EVENT LOG"]
   (map-indexed
     (fn [idx e]
       [:div {:key idx}
        (prn-str e)])
     (rum/react event-log))])

(defn ^:dev/after-load start []
  (rum/mount (ui) (js/document.getElementById "app")))

(defmulti on-event first)

(defn on-message [message]
  (when-let [event (try
                     (edn/read-string (.-data message))
                     (catch js/Error ^js/Error err
                       (js/console.error (.-message err))
                       (prn (.-data message))
                       nil))]
    (swap! event-log conj event)
    (on-event event)))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  (js/console.log "Initializing debugger...")
  (let [ws (js/WebSocket. (str "ws://" js/location.host "/ws"))]
    (.addEventListener ws "open"
                       (fn [_]
                         (js/console.log "Connected to WebSocket.")
                         (when (empty? @event-log)
                           (.send ws (prn-str [:replay-event-log])))))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close"
                       #(do
                          (js/setTimeout
                            (fn []
                              (js/console.log "Attempting to re-initialize...")
                              (init))
                            1000)
                          (js/console.error "WebSocket connection closed!"))))
  (start))
