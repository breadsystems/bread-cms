(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [rum.core :as rum]
    [systems.bread.alpha.tools.impl :as impl :refer [publish!
                                                     subscribe-db
                                                     on-event]]))

(let [[db' _] (subscribe-db)]
  (def db db'))

(def requests (rum/cursor-in db [:request/uuid]))

(rum/defc ui < rum/reactive []
  (let [reqs (rum/react requests)]
    [:<>
     (if (seq reqs)
       [:ul
        (map (fn [[uuid req]]
               [:li {:key uuid}
                [:label {:for uuid} (if (empty? uuid) "[No UUID]" uuid)]
                [:div {:id uuid}
                 [:details
                  [:summary "Raw request..."]
                  [:pre (with-out-str (pprint req))]]]])
             reqs)]
       [:p "No requests yet!"])]))

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (js/console.log "Starting debug session...")
  (rum/mount (ui) (js/document.getElementById "app")))

(defn on-message [message]
  (let [event (edn/read-string (.-data message))]
    (publish! event)))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  ;; TODO get WS host/port dynamically
  (let [ws (js/WebSocket. "ws://localhost:1314")]
    (.addEventListener ws "open"
                       (fn [_]
                         (.send ws (prn-str {:event/type :frontend/init}))))
    (.addEventListener ws "message" on-message))
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "Reloading..."))

(comment
  (deref db))
