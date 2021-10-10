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

(defonce subscriptions (atom {:name "Coby"}))

(defn sub [query]
  (get (rum/react subscriptions) query))

(rum/defc ui
  < rum/reactive
  []
  [:main
   (str "hello, " (:name (rum/react subscriptions)))
   [:ul
    (map
      (fn [{:request/keys [uuid uri]}]
        [:li {:key (str uuid)} uri])
      (sub [:request/uuid :request/uri]))]])

(defn ^:dev/after-load start []
  (rum/mount (ui) (js/document.getElementById "app")))

(defmulti on-event first)

(defmethod on-event :subscription [[_ query value]]
  (swap! subscriptions assoc query value))

(defn on-message [message]
  (when-let [event (try
                     (edn/read-string (.-data message))
                     (catch js/Error ^js/Error err
                       (js/console.error err)
                       (prn (.-data message))
                       nil))]
    (on-event event)
    #_
    (publish! event)))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  (js/console.log "Initializing...")
  (let [ws (js/WebSocket. (str "ws://" js/location.host "/ws"))]
    (.addEventListener ws "open"
                       (fn [_]
                         (.send ws (prn-str [:subscribe [:request/uuid :request/uri]]))))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close"
                       #(do
                          (js/setTimeout
                            (fn []
                              (js/console.log "Attempting to re-initialize...")
                              (init))
                            1000)
                          (js/console.error "WebSocket connection closed!"))))
  #_
  (on-event {:event/type :ui/loading!})
  (start))
