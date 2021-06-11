(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [rum.core :as rum]
    [systems.bread.alpha.tools.impl :as impl :refer [publish!
                                                     subscribe-db
                                                     on-event]]
    [systems.bread.alpha.tools.util :refer [ago date-fmt date-fmt-ms join-some req->url]]))

(let [[db' _] (subscribe-db)]
  (def db db'))

(defonce !ws (atom nil))

(defn send! [event]
  (when-let [ws @!ws]
    (.send ws (prn-str event))))

(def requests (rum/cursor-in db [:request/uuid]))
(def loading? (rum/cursor-in db [:ui/loading?]))
(def print-db? (rum/cursor-in db [:ui/print-db?]))
(def websocket (rum/cursor-in db [:ui/websocket]))

(def req-uuids (rum/cursor-in db [:request/uuids]))
(def req-uuid (rum/cursor-in db [:ui/selected-req]))
(def selected (rum/cursor-in db [:ui/selected-reqs]))
(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))
(defn- idx->req [idx]
  (get @requests (get @req-uuids idx)))

(defn- toggle-print-db! []
  (swap! db update :ui/print-db? not))

(defn clear-requests! []
  (when (js/confirm "Clear all request data? This cannot be undone.")
    (publish! {:event/type :clear-requests})
    (send! {:event/type :clear-requests})))

(comment
  (toggle-print-db!)

  (uuid->req (first @req-uuids))

  (deref req-uuids)
  (deref selected)
  (deref loading?)

  ;;
  )

(rum/defc request-details < rum/reactive []
  (let [{uuid :request/uuid req :request/initial :as req-data}
        (uuid->req (rum/react req-uuid))]
    [:div.rows
     ;; TODO use Citrus here?
     [:button {:on-click #(swap! db assoc :ui/selected-req nil)}
      "Close"]
     [:h2 [:code (req->url req)]]
     [:h3 uuid]
     [:div.info (date-fmt-ms (:request/timestamp req))]
     [:div
      [:h3 "Hooks"]
      [:ul
       (map-indexed (fn [idx {:keys [hook args f file line column]}]
                      [:li {:key idx}
                       [:strong (name hook)]
                       " "
                       [:code
                        (join-some ":" [file line column])]])
                    (:request/hooks req-data))]]
     [:details
      [:summary "Raw request EDN..."]
      [:pre (with-out-str (pprint req))]]]))

(rum/defc ui < rum/reactive []
  (let [reqs (map uuid->req (rum/react req-uuids))
        selected (rum/react selected)
        loading? (rum/react loading?)
        current-uuid (rum/react req-uuid)
        print? (rum/react print-db?)
        ws (rum/react websocket)]
    [:main
     (if (and (not loading?) (false? ws))
       [:p.error "WebSocket connection lost!"]
       [:p.info "connected to " ws])
     [:div.with-sidebar
      [:div
       [:div.rows
        (cond
          (seq reqs)
          [:ul
           (map-indexed (fn [idx {uuid :request/uuid
                                  req :request/initial}]
                          [:li.req-item {:key uuid}
                           [:div
                            [:input {:type :checkbox
                                     :checked (contains? selected idx)
                                     :on-change #(publish!
                                                   {:event/type :ui/select-req
                                                    :uuids/index idx})}]]
                           [:label.req-label
                            ;; TODO decouple publish! from UI events...?
                            {:on-click #(publish! {:event/type :ui/view-req
                                                   :request/uuid uuid})}
                            [:div [:code (:uri req)]]
                            [:div (:request/id req)]
                            [:div (some-> (:request/timestamp req) date-fmt)]]])
                        reqs)]
          loading?
          [:p "Loading..."])
        [:div.rows
         [:div
          [:button {:on-click #(publish! {:event/type :replay!})
                    :disabled (not (seq selected))}
           "Replay selected"]]
         [:div
          [:button {:on-click #(clear-requests!)
                    :disabled (not (seq reqs))}
           "Clear requests"]]]]
       (cond
         current-uuid
         (request-details)
         (seq reqs)
         [:p "Click a request to view details"]
         :else
         [:p.info "No requests yet."])]]
     (when print?
       [:div
        [:h3 "Debug DB"]
        [:pre (with-out-str (pprint (rum/react db)))]])]))

(defmethod on-event :init [{:ui/keys [state]}]
  (reset! db (merge {:request/uuid {}
                     :request/uuids []
                     :ui/selected-req nil
                     :ui/selected-reqs (sorted-set)
                     :ui/loading? false
                     :ui/websocket "ws://localhost:1314"}
                    state)))

(defmethod on-event :ui/view-req [{:request/keys [uuid]}]
  (swap! db assoc :ui/selected-req uuid))

(defmethod on-event :ui/loading! [_]
  (swap! db assoc :ui/loading? true))

(defmethod on-event :ui/done! [_]
  (swap! db assoc :ui/loading? false))

(defmethod on-event :ui/websocket-closed! [_]
  (swap! db assoc :ui/websocket false))

(defmethod on-event :ui/select-req [{:uuids/keys [index]}]
  (swap! db update :ui/selected-reqs
         (fn [selected]
           (if (selected index)
             (disj selected index)
             (conj selected index)))))

(defmethod on-event :replay! []
  (doseq [idx @selected]
    (let [{req :request/initial} (idx->req idx)]
      (prn 'replay! idx (:uri req)))))

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
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
    (reset! !ws ws)
    (.addEventListener ws "open"
                       (fn [_]
                         (.send ws (prn-str {:event/type :ui/init}))))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close"
                       #(do
                          (publish! {:event/type :ui/websocket-closed!})
                          (js/console.error "WebSocket connection closed!"))))
  (on-event {:event/type :ui/loading!})
  (start))
