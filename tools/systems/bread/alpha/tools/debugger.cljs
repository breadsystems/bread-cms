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

(def req-uuid (rum/cursor-in db [:ui/selected-req]))
(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(comment
  (keys (deref requests))
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
  (let [reqs (rum/react requests)
        loading? (rum/react loading?)
        current-uuid (rum/react req-uuid)]
    [:main
     [:div.with-sidebar
      [:div
       [:div
        (cond
          (seq reqs)
          [:ul
           (map-indexed (fn [idx [uuid {req :request/initial}]]
                          [:li.req-item {:key uuid}
                           [:div
                            [:input {:type :checkbox
                                     :checked true
                                     :on-change #(prn 'click! idx)}]]
                           [:label.req-label
                            ;; TODO decouple publish! from UI events...
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
