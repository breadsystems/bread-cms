(ns systems.bread.alpha.tools.debugger
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [editscript.core :as ed]
    [rum.core :as rum]
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

(declare db)

;; Initialize the and subscribe to updates exactly once.
(when-not db
  (let [[db' _] (subscribe-db)]
    (def db db')))

(defonce !ws (atom nil))

(defn- websocket-url []
  (str "ws://" js/location.host "/ws"))

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
(def viewing-raw-request? (rum/cursor-in db [:ui/viewing-raw-request?]))
(def viewing-raw-response? (rum/cursor-in db [:ui/viewing-raw-response?]))
(def diff-uuids (rum/cursor-in db [:ui/diff]))
(def diff-type (rum/cursor-in db [:ui/diff-type]))

(def replay-as-of? (rum/cursor-in db [:ui/preferences :replay-as-of?]))

(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(defn- idx->req [idx]
  (get @requests (get @req-uuids idx)))

(defn- diff-entities [[a b] diff-type]
  (when (and a b)
    (condp = diff-type
      :response-pre-render
      [(get-in (uuid->req a) [:response/pre-render])
       (get-in (uuid->req b) [:response/pre-render])]
      :database
      [(get-in (uuid->req a) [:request/response :response/datastore])
       (get-in (uuid->req b) [:request/response :response/datastore])])))

(defn- diff-uuid-options [uuid]
  (map (fn [req-uuid]
         [req-uuid (shorten-uuid req-uuid)])
       (filter #(not= uuid %) @req-uuids)))

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
        diff-opts (diff-uuid-options uuid)
        viewing-hooks? (rum/react viewing-hooks?)
        viewing-raw-request? (rum/react viewing-raw-request?)
        viewing-raw-response? (rum/react viewing-raw-response?)]
    [:article.rows
     [:header.with-sidebar.reverse
      [:div
       [:div
        [:h2.emphasized (req->url req)]]
       [:div
        [:button.close-btn {:on-click #(swap! db assoc
                                              :ui/selected-req nil
                                              :ui/diff nil)}
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
     [:div.flex
      [:div
       [:button {:on-click #(replay-request! req)} "Replay this request"]]
      [:div
       [:select
        {:on-change (fn [e]
                      (let [target (.. e -target -value)]
                        (swap! db assoc :ui/diff [uuid target])))}
        [:option "Diff against..."]
        (map (fn [[value label]]
               [:option {:key value :value value} label])
             diff-opts)]]]
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
     [:h3 "Response (HTML)"]
     [:div.response
      (:body res)]
     [:h3 "Response (pre-render)"]
     [:div.response
      (pp (:response/pre-render req-data))]
     [:h3 "Raw request"]
     [:div
      [:button.lowkey {:on-click #(swap! db update :ui/viewing-raw-request? not)}
       (if viewing-raw-request? "Hide" "Show")]]
     (when viewing-raw-request?
       [:pre (pp req)])
     [:h3 "Raw response"]
     [:div
      [:button.lowkey {:on-click #(swap! db update :ui/viewing-raw-response? not)}
       (if viewing-raw-response? "Hide" "Show")]]
     (when viewing-raw-response?
       [:pre (pp res)])]))

(rum/defc diff-line [n line]
  (let [attrs {:key n :data-line (inc n)}]
    (if (string? line)
      [:pre.str attrs line]
      (let [[op line] line]
        (condp = op
          :- [:pre.del attrs line]
          :+ [:pre.add attrs line]
          :gap [:pre (assoc attrs :style {:margin-top "1em"}) line])))))

(def a'
  [:html
   [:head [:title "The Page Title"]]
   [:p "this got deleted"]
   [:main
    [:p "Libero esse excepteur enim facilis odio."]
    [:p "Occaecat eiusmod libero omnis qui omnis laborum."]
    [:p "Omnis molestias eligendi quis veniam similique deserunt."]]])
(def b'
  [:html
   [:head [:title "The Page Title"]]
   [:main
    [:p "Libero esse excepteur enim facilis odio."]
    [:p "Proident tempore voluptate libero lorem tempore soluta."]
    [:p "Omnis molestias eligendi quis veniam similique deserunt."]]
   [:div
    [:p "Nulla optio et exercitation similique."]]])

(rum/defc diff-ui < rum/reactive []
  (let [diff-type (rum/react diff-type)
        [ua ub] (rum/react diff-uuids)
        [source target] (diff-entities [ua ub] diff-type)
        [ra rb script] (diff/diff-struct-lines source target)
        #_#_
        [ra rb script] (diff/diff-struct-lines a' b')
        ]
    [:article.rows
     [:header.rows
      [:h2 "Diff: " [:code (shorten-uuid ua)] " → " [:code (shorten-uuid ub)]]
      [:div.flex
       [:button {:on-click #(swap! db assoc :ui/diff nil)}
        "← Back to " (shorten-uuid ua)]
       [:select
        {:default-value diff-type
         :on-change #(swap! db assoc :ui/diff-type
                            (keyword (.. % -target -value)))}
        [:option {:value :response-pre-render} "Response (pre-render)"]
        [:option {:value :database} "Database"]
        ]]]
     #_
     (map-indexed (fn [idx [path op value]]
            [:pre {:key idx} (str path) " " (name op) " " (pp value)])
          (ed/get-edits script))
     [:div.diff
      [:div.response.diff-source
       (map-indexed (fn [idx line] (diff-line idx line)) ra)]
      [:div.response.diff-target
       (map-indexed (fn [idx line] (diff-line idx line)) rb)]]]))

(rum/defc ui < rum/reactive []
  (let [reqs (map uuid->req (rum/react req-uuids))
        selected (rum/react selected)
        loading? (rum/react loading?)
        diff (rum/react diff-uuids)
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
                          [:li.req-item {:key uuid
                                         :class (when (= uuid current-uuid)
                                                  "current")}
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
         diff (diff-ui)
         current-uuid (request-details)
         (seq reqs) [:p "Click a request to view details"]
         :else [:p.info "No requests yet."])]]
     (when print?
       [:div
        [:h3 "Debug DB"]
        [:pre (pp (rum/react db))]])]))

(defmethod on-event :init [{:ui/keys [state]}]
  (reset! db (merge {:request/uuid {}
                     :request/uuids []
                     :ui/websocket (websocket-url)
                     :ui/diff nil
                     :ui/diff-type :response-pre-render
                     :ui/selected-req nil
                     :ui/selected-reqs (sorted-set)
                     :ui/loading? false}
                    state)))

(defmethod on-event :ui/view-req [{:request/keys [uuid]}]
  (swap! db assoc :ui/selected-req uuid :ui/diff nil))

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
  (when-let [event (try
                     (edn/read-string (.-data message))
                     (catch js/Error ^js/Error err
                       (prn err)
                       (prn (.-data message))
                       nil))]
    (publish! event)))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  ;; TODO get WS host/port dynamically
  (let [ws (js/WebSocket. (websocket-url))]
    (reset! !ws ws)
    (.addEventListener ws "open"
                       (fn [_]
                         (.send ws (prn-str {:event/type :ui/init}))))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close"
                       #(do
                          (publish! {:event/type :ui/websocket-closed!})
                          (js/setTimeout
                            (fn []
                              (set! js/window.location js/window.location))
                            1000)
                          (js/console.error "WebSocket connection closed!"))))
  (on-event {:event/type :ui/loading!})
  (start))
