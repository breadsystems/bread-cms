(ns systems.bread.alpha.tools.debug
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [datascript.core :as d]
    [editscript.core :as ed]
    [rum.core :as rum]
    [systems.bread.alpha.tools.debug.db :as db :refer [db]]
    [systems.bread.alpha.tools.debug.event :as e]
    [systems.bread.alpha.tools.debug.request :as r]
    [systems.bread.alpha.tools.util :refer [date-fmt
                                            date-fmt-ms
                                            join-some
                                            pp
                                            req->url
                                            shorten-uuid]]
    ;; TODO move this ns up into tools
    [systems.bread.alpha.tools.debugger.diff :as diff]))

;; TODO delete this
(defmulti publish! :event/type)
(defmethod publish! :default [e] (prn 'TODO (:event/type e)))

(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(defn- uuid->max-tx [uuid]
  (-> uuid uuid->req (get-in [:request/response :response/datastore :max-tx])))

(defn- idx->req [idx]
  (get @db/requests (get @db/req-uuids idx)))

(defn clear-requests! []
  (when (js/confirm "Clear all request data? This cannot be undone.")
    (publish! {:event/type :clear-requests})
    #_
    (send! {:event/type :clear-requests})))

(defn prefer! [pref-key pref]
  (swap! db assoc-in [:ui/preferences pref-key] pref))

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
         [req-uuid (shorten-uuid db/req-uuid)])
       (filter #(not= uuid %) @db/req-uuids)))

(rum/defc request-details < rum/reactive []
  (let [{uuid :request/uuid
         id :request/id
         req :request/initial
         res :request/response
         :as req-data}
        (uuid->req (rum/react db/req-uuid))
        diff-opts (map shorten-uuid @db/req-uuids)
        viewing-hooks? (rum/react db/viewing-hooks?)
        viewing-raw-request? (rum/react db/viewing-raw-request?)
        viewing-raw-response? (rum/react db/viewing-raw-response?)]
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
       [:button {:on-click #(prn 'TODO 'replay-request!)}
        "Replay this request"]]
      [:div
       [:select
        {:on-change (fn [e]
                      (let [target (.. e -target -value)]
                        (swap! db assoc :ui/diff [uuid target])))}
        [:option "Diff against..."]
        (map (fn [opt]
               [:option {:key opt :value opt :disabled (= id opt)}
                opt (when (= id opt) " (this request)")])
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

(rum/defc diff-ui < rum/reactive []
  (let [current-uuid (rum/react db/req-uuid)
        ;; What kind of diff is the user viewing?
        diff-type (rum/react db/diff-type)
        ;; Get each UUID in the diff.
        [ua ub] (rum/react db/diff-uuids)
        ;; Get the timestamp for each response being diffed.
        ;; We use this info to detect if a diff is oriented
        ;; reverse-chronologically, and, if so, to indicate that to the user.
        [ta tb] (mapv uuid->max-tx [ua ub])
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
        "← Back to " (shorten-uuid current-uuid)]
       [:button {:on-click #(swap! db update :ui/diff reverse)}
        "↺ Reverse diff"]
       [:select
        {:value (name diff-type)
         :on-change #(swap! db assoc :ui/diff-type
                            (keyword (.. % -target -value)))}
        [:option {:value "response-pre-render"} "Response (pre-render)"]
        [:option {:value "database"} "Database"]]]
      (when (> ta tb)
        [:p.info
         "This diff is in reverse-chronological order."
         " The data on the left is older than the data on the right."])]
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
  (let [reqs (map uuid->req (rum/react db/req-uuids))
        selected (rum/react db/selected)
        loading? (rum/react db/loading?)
        diff (rum/react db/diff-uuids)
        current-uuid (rum/react db/req-uuid)
        print? (rum/react db/print-db?)
        ws (rum/react db/websocket)
        as-of? (rum/react db/replay-as-of?)]
    [:main
     (if (and (not loading?) (false? ws))
       [:p.error "Attempting to reconnect to WebSocket..."]
       [:p.info "connected to " ws])
     [:div.with-sidebar
      [:div
       [:div.rows.sidebar
        [:h2 "Requests"]
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
                                     :on-change #(e/on-event
                                                   [:ui/select-req idx])}]]
                           [:label.req-label
                            {:on-click #(e/on-event [:ui/view-req uuid])}
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
        [:pre (pp (rum/react db))]
        [:h3 "Events"]
        (when-let [entries (seq @e/event-log)]
          [:ul
           (map-indexed (fn [idx entry]
                          ^{:key idx}
                          [:li
                           [:code (prn-str entry)]])
                        entries)])])]))

(comment
  (swap! db update :ui/print-db? not))

(defn ^:dev/after-load start []
  (rum/mount (ui) (js/document.getElementById "app")))

(declare init)

(defn- on-open [ws url]
  (js/console.log "Connected to WebSocket.")
  (swap! db assoc :ui/websocket url)
  (when (empty? @e/event-log)
    (.send ws (prn-str [:replay-event-log]))))

(defn- on-message [message]
  (when-let [event (try
                     (edn/read-string (.-data message))
                     (catch js/Error ^js/Error err
                       (js/console.error (.-message err))
                       (prn (.-data message))
                       nil))]
    (swap! e/event-log conj event)
    (e/on-event event)))

(defn- attempt-reconnect []
  (js/setTimeout
    (fn []
      (js/console.log "Attempting to re-initialize...")
      (swap! db assoc :ui/websocket false)
      (init))
    1000)
  (js/console.error "WebSocket connection closed!"))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  (js/console.log "Initializing debugger...")
  (let [url (str "ws://" js/location.host "/ws")
        ws (js/WebSocket. url)]
    (.addEventListener ws "open" (fn [_] (on-open ws url)))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close" attempt-reconnect))
  (start))
