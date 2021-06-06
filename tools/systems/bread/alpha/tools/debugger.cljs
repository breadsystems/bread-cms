(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [rum.core :as rum]))

;(defonce !ws (atom nil))

(defonce db (atom {:counter 0}))

(def counter-state (rum/cursor-in db [:counter]))

(rum/defc counter < rum/reactive []
  [:<>
   [:div [:button {:on-click #(swap! db update :counter inc)}
          "Increment!"]]
   [:p "count: " (rum/react counter-state)]])

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (js/console.log "start!")
  (rum/mount (counter) (js/document.getElementById "app")))

(defmulti on-event :event/type)
(defmethod on-event :default [e]
  (js/console.log "Unknown event type:" (:event/type e)))
(defmethod on-event :bread/hook [e]
  (prn (select-keys e [:hook :f :uuid])))

(defn on-message [message]
  (let [event (edn/read-string (.-data message))]
    (on-event event)))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  ;; TODO get WS host/port dynamically
  (let [ws (js/WebSocket. "ws://localhost:1314")]
    (set! (.-onmessage ws) on-message))
  (prn 'hello)
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "stop"))
