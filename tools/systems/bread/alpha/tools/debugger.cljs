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
(def loading? (rum/cursor-in db [:ui/loading?]))

(def req-uuid (rum/cursor-in db [:ui/selected-req]))
(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(comment
  (deref requests)
  (deref loading?)
  (deref req))

(rum/defc ui < rum/reactive []
  (let [reqs (rum/react requests)
        loading? (rum/react loading?)]
    [:main
     [:div.flex
      [:div
       (cond
         (seq reqs)
         [:ul
          (map (fn [[uuid req]]
                 [:li {:key uuid}
                  [:label {:on-click #(publish! {:event/type :ui/select-req
                                                 :request/uuid uuid})}
                   (if (empty? uuid) "[No UUID]" uuid)]])
               reqs)]
         loading?
         [:p "Loading..."]
         :else
         [:p "No requests yet!"])]
      (let [{:request/keys [hooks uuid uri] :as req}
            (uuid->req (rum/react req-uuid))]
        [:div
         [:h2 uuid]
         [:h3 uri]
         [:div
          [:h3 "Hooks"]
          [:ul
           (map-indexed (fn [idx {:keys [hook args f line column]}]
                          [:li {:key idx} (name hook)])
                        hooks)]
          [:details
           [:summary "Raw request..."]
           [:pre (with-out-str (pprint req))]]]])]
     #_
     [:pre (with-out-str (pprint @db))]]))

(defmethod on-event :init [{:keys [state]}]
  (swap! db merge
         {:request/uuid {}
          :ui/loading? false
          :ui/selected-req nil}
         state))

(defmethod on-event :ui/select-req [{:request/keys [uuid]}]
  (swap! db assoc :ui/selected-req uuid))

(defmethod on-event :ui/loading! [_]
  (swap! db assoc :ui/loading? true))

(defmethod on-event :ui/done! [_]
  (swap! db assoc :ui/loading? false))

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
                         (.send ws (prn-str {:event/type :ui/init}))))
    (.addEventListener ws "message" on-message))
  (on-event {:event/type :ui/loading!})
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "Reloading..."))
