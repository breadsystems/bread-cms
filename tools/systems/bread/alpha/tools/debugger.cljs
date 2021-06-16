(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [rum.core :as rum]
    [systems.bread.alpha.tools.impl :as impl :refer [publish!
                                                     subscribe-db
                                                     on-event]]
    [systems.bread.alpha.tools.util :refer [ago
                                            date-fmt
                                            date-fmt-ms
                                            join-some
                                            req->url
                                            shorten-uuid]]))

(declare db)

;; Initialize the and subscribe to updates exactly once.
(when-not db
  (let [[db' _] (subscribe-db)]
    (def db db')))

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
(def viewing-hooks? (rum/cursor-in db [:ui/viewing-hooks?]))

(def replay-as-of? (rum/cursor-in db [:ui/preferences :replay-as-of?]))

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

(defn- replay-as-of [{:profiler/keys [as-of-param]
                      :request/keys [as-of]
                      :as req}]
  (-> req
      ;; TODO formatting for dates
      (assoc-in [:params as-of-param] (str as-of))
      (assoc :profiler/as-of-param as-of-param)))

(defn req->replay [req]
  (-> req
    ;; Always add replay indicators first.
    (assoc :profiler/replay? true
           :profiler/replay-uuid (:request/uuid req))
    ;; Remove data we always want to overwrite for each request,
    ;; to avoid confusion.
    (dissoc :request/timestamp :request/uuid)))

(defn replay-request! [req]
  (send! {:event/type :request/replay
          :event/request (req->replay req)
          :replay/as-of? @replay-as-of?}))

(defn prefer! [pref-key pref]
  (swap! db assoc-in [:ui/preferences pref-key] pref))

(comment
  (toggle-print-db!)

  (uuid->req (first @req-uuids))

  (deref req-uuids)
  (deref selected)
  (deref loading?)

  (deref replay-as-of?)

  (publish! {:event/type :replay-selected!})

  ;;
  )

(rum/defc request-details < rum/reactive []
  (let [{uuid :request/uuid
         req :request/initial
         res :request/response
         :as req-data}
        (uuid->req (rum/react req-uuid))
        viewing-hooks? (rum/react viewing-hooks?)]
    [:article.rows
     [:header.with-sidebar
      [:div
       [:div
        [:h2.emphasized (req->url req)]]
       [:div
        [:button.close-btn {:on-click #(swap! db assoc :ui/selected-req nil)}
         "Close"]]]]
     [:div.info (date-fmt-ms (:request/timestamp req))]
     [:div.with-sidebar
      [:div
       [:div "UUID"]
       [:div [:code.uuid uuid]]]]
     (when-let [replayed (:profiler/replay-uuid req)]
       [:div.with-sidebar
        [:div
         [:div "Replay of:"]
         [:div
          [:button.replay-uuid
           {:on-click #(swap! db assoc :ui/selected-req replayed)}
           (shorten-uuid replayed)]]]])
     (when-let [replays (seq (:request/replays req-data))]
       [:div.with-sidebar
        [:div
         [:div "Replays:"]
         [:div
          (map (fn [uuid]
                 [:button.replay-uuid.replay-btn
                  {:key uuid
                   :on-click #(swap! db assoc :ui/selected-req uuid)}
                  (shorten-uuid uuid)])
               replays)]]])
     [:div
      [:button {:on-click #(replay-request! req)} "Replay this request"]]
     [:h3 "Request hooks"]
     [:p.info
      [:span (str (count (:request/hooks req-data)) " hooks")]
      [:button.lowkey {:on-click #(swap! db update :ui/viewing-hooks? not)}
       (if viewing-hooks? "Hide" "Show")]]
     (when viewing-hooks?
       [:ul
        (map-indexed (fn [idx {:keys [hook args f file line column]}]
                       [:li {:key idx}
                        [:strong (name hook)]
                        " "
                        [:code
                         (join-some ":" [file line column])]])
                     (:request/hooks req-data))])
     [:h3 "Response"]
     [:div.response
      (:body res)]
     [:h3 "Response (pre-render)"]
     [:div.response
      (with-out-str (pprint (:response/pre-render req-data)))]
     [:h3 "Raw request"]
     [:pre (with-out-str (pprint req))]
     [:h3 "Raw response"]
     [:pre (with-out-str (pprint res))]]))

(rum/defc ui < rum/reactive []
  (let [reqs (map uuid->req (rum/react req-uuids))
        selected (rum/react selected)
        loading? (rum/react loading?)
        current-uuid (rum/react req-uuid)
        print? (rum/react print-db?)
        ws (rum/react websocket)
        as-of? (rum/react replay-as-of?)]
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
          [:button {:on-click #(publish! {:event/type :replay-selected!})
                    :disabled (not (seq selected))}
           "Replay selected"]]
         [:div
          [:button {:on-click #(clear-requests!)
                    :disabled (not (seq reqs))}
           "Clear requests"]]
         [:div
          [:input {:type :checkbox
                   :id "pref-replay-as-of"
                   :checked (boolean as-of?)
                   :value 1
                   :on-change #(prefer! :replay-as-of? (not as-of?))}]
          [:label {:for "pref-replay-as-of"} "Replay with " [:code "as-of"]]]]]
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
                     :ui/loading? false}
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

(defmethod on-event :replay-selected! []
  (doseq [idx @selected]
    (let [{req :request/initial} (idx->req idx)]
      (replay-request! req))))

;; TODO figure out why replay is send!ing once more each time code
;; is reloaded...
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
  (let [ws (js/WebSocket. (str "ws://" js/location.host "/ws"))]
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
