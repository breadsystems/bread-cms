(ns systems.bread.alpha.tools.debug
  (:require
    [clojure.edn :as edn]
    [editscript.core :as ed]
    [rum.core :as rum]
    [systems.bread.alpha.tools.debug.client :as client]
    [systems.bread.alpha.tools.debug.db :as db :refer [db]]
    [systems.bread.alpha.tools.debug.event :as e]
    [systems.bread.alpha.tools.debug.request :as r]
    [systems.bread.alpha.tools.util :refer [date-fmt
                                            #_
                                            pp]]
    [systems.bread.alpha.tools.debug.diff :as diff]))

(defn- clear-debug-log! []
  (when (js/confirm "Clear all debug data? This cannot be undone.")
    (client/send! [:clear-debug-log])
    (reset! db db/initial)))

(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))

(defn- idx->req [idx]
  (get @db/requests (get @db/req-uuids idx)))

(defn- replay-selected! []
  (client/send!
    [:replay-requests
     (map (comp :request/initial idx->req) @db/selected)
     {:replay/as-of? @db/replay-as-of?}]))

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
           (map-indexed
             (fn [idx {uuid :request/uuid
                       req :request/initial}]
               [:li.req-item {:key uuid
                              :class (when (= uuid current-uuid)
                                       "current")
                              :on-click #(e/on-event [:ui/view-req uuid])}
                [:div
                 [:input {:type :checkbox
                          :checked (contains? selected idx)
                          :on-change #(e/on-event [:ui/select-req idx])}]]
                [:label.req-label
                 [:div [:code (:uri req)]]
                 [:div (:request/id req)]
                 [:div (some-> (:request/timestamp req) date-fmt)]]])
             reqs)]
          loading?
          [:p "Loading..."])
        [:div.rows
         [:div
          [:button {:on-click replay-selected!
                    :disabled (not (seq selected))}
           "Replay selected"]]
         [:div
          [:button {:on-click #(clear-debug-log!)
                    :disabled (not (seq reqs))}
           "Clear debug log"]]
         [:div
          [:input {:type :checkbox
                   :id "pref-replay-as-of"
                   :checked (boolean as-of?)
                   :value 1
                   :on-change #(swap! db update :ui/replay-as-of? not)}]
          [:label {:for "pref-replay-as-of"} "Replay with " [:code "as-of"]]]]]
       (cond
         diff (diff/diff-ui)
         current-uuid (r/request-details)
         (seq reqs) [:p "Click a request to view details"]
         :else [:p.info "No requests yet."])]]
     ;; TODO maybe optimize event log/db for printing?
     #_
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
    (client/send! [:replay-event-log])))

(defn- on-message [message]
  (when-let [event (try
                     ;; TODO transit
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

(defn- ws-url []
  (if-let [elem (js/document.querySelector "meta[name=ws-url]")]
    (.getAttribute elem "content")
    (str "ws://" js/location.host "/ws")))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  (js/console.log "Initializing debugger...")
  (let [url (ws-url)
        ws (js/WebSocket. url)]
    (reset! client/ws ws)
    (.addEventListener ws "open" (fn [_] (on-open ws url)))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close" attempt-reconnect))
  (start))
