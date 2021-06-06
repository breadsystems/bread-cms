(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [rum.core :as rum]))

;(defonce !ws (atom nil))

(defonce db (atom {:request/uuid {}}))

(def requests (rum/cursor-in db [:request/uuid]))

(rum/defc ui < rum/reactive []
  (let [reqs (rum/react requests)]
    [:<>
     (if (seq reqs)
       [:ul
        (map (fn [[uuid req]]
               [:li {:key uuid}
                [:label {:for uuid} uuid]
                [:div {:id uuid}
                 [:details
                  [:summary "Raw request..."]
                  [:pre (with-out-str (pprint req))]]]])
             reqs)]
       [:p "No requests yet!"])]))

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (js/console.log "start!")
  (rum/mount (ui) (js/document.getElementById "app")))

(defn conjv [v x]
  (conj (or v []) x))

(defmulti on-event :event/type)
(defmethod on-event :default [e]
  (js/console.log "Unknown event type:" (:event/type e)))
(defmethod on-event :bread/hook [e]
  (let [{:keys [uuid]} e]
    (swap! db update-in [:request/uuid uuid :request/hooks] conjv e)))

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
