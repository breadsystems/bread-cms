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

(def requests (rum/cursor-in db [:request/uuid]))
(def loading? (rum/cursor-in db [:ui/loading?]))
(def print-db? (rum/cursor-in db [:ui/print-db?]))

(def req-uuids (rum/cursor-in db [:request/uuids]))
(def req-uuid (rum/cursor-in db [:ui/selected-req]))
(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(defn- toggle-print-db! []
  (swap! db update :ui/print-db? not))

(comment
  (toggle-print-db!)

  (uuid->req (first @req-uuids))

  (deref req-uuids)
  (deref loading?)
  (select-keys
    (uuid->req (deref req-uuid))
    [:request/uuid :request/id :request/timestamp :uri :headers :params])
  (:request/timestamp (:request/initial (uuid->req (deref req-uuid))))
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
     [:div.req-timestamp (date-fmt-ms (:request/timestamp req))]
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
        loading? (rum/react loading?)
        current-uuid (rum/react req-uuid)
        print? (rum/react print-db?)]
    [:main
     [:div.with-sidebar
      [:div
       [:div
        (cond
          (seq reqs)
          [:ul
           (map-indexed (fn [idx {uuid :request/uuid
                                  req :request/initial}]
                          [:li.req-item {:key uuid}
                           [:div
                            [:input {:type :checkbox
                                     :checked true
                                     :on-change #(prn 'click! idx)}]]
                           [:label.req-label
                            ;; TODO decouple publish! from UI events...?
                            {:on-click #(publish! {:event/type :ui/select-req
                                                   :request/uuid uuid})}
                            [:div [:code (:uri req)]]
                            [:div (:request/id req)]
                            [:div (some-> (:request/timestamp req) date-fmt)]]])
                        reqs)]
          loading?
          [:p "Loading..."]
          :else
          [:p "No requests yet!"])]
       (if current-uuid
         (request-details)
         [:p "Click a request ID to view details"])]]
     (when print?
       [:div
        [:h3 "Debug DB"]
        [:pre (with-out-str (pprint (rum/react db)))]])]))

(defmethod on-event :init [_]
  (reset! db {:request/uuid {}
              :request/uuids []
              :ui/selected-requests (sorted-set)
              :ui/loading? false
              :ui/selected-req nil}))

(defmethod on-event :ui/select-req [{:request/keys [uuid]}]
  (swap! db assoc :ui/selected-req uuid))

(defmethod on-event :ui/loading! [_]
  (swap! db assoc :ui/loading? true))

(defmethod on-event :ui/done! [_]
  (swap! db assoc :ui/loading? false))

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
    (.addEventListener ws "open"
                       (fn [_]
                         (.send ws (prn-str {:event/type :ui/init}))))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close"
                       #(js/console.error
                          "WebSocket connection closed!")))
  (on-event {:event/type :ui/loading!})
  (start))
