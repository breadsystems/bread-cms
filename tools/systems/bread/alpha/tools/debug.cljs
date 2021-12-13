(ns systems.bread.alpha.tools.debug
  (:require
    [citrus.core :as citrus]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [datascript.core :as d]
    [editscript.core :as ed]
    [rum.core :as rum]
    #_
    [systems.bread.alpha.tools.debugger.diff :as diff]))

(comment
  (def schema {:person/aka {:db/cardinality :db.cardinality/many}})
  (def conn (d/create-conn schema))

  (d/transact! conn [{:person/name "Coby"
                      :person/age 33
                      :person/aka ["Cobster" "Cobmeister"]}
                     {:person/name "Rowan"
                      :person/age 20
                      :person/aka ["Old Man Carrick" "Carrick"]}])

  ;; Pull query
  (->>
    (d/q '{:find [(pull ?e [:db/id :person/name :person/age :person/aka])]
           :in [$]
           :where [[?e :person/name ]]}
         @conn)
    (map first)
    vec)

  ;; Map query
  (d/q '{:find [?e ?name ?age]
         :in [$]
         :where [[?e :person/name ?name]
                 [?e :person/age ?age]]}
       @conn)

  ;; Basic query
  (d/q '[:find ?e ?name ?age
         :in $
         :where
         [?e :person/name ?name]
         [?e :person/age ?age]]
       @conn)
  )

(defonce event-log (atom []))

(defn log-entries [e]
  (prn 'log-entries e)
  @event-log)

(defonce state (atom {:stuff {:a :A :b :B}}))

(defonce reconciler
  (citrus/reconciler {:state state
                      :controllers {:log-entries log-entries}
                      :effect-handlers {}}))

(defn stuff [reconciler]
  (citrus/subscription reconciler [:stuff]))

(rum/defc ui
  < rum/reactive
  []
  [:main
   [:a {:href (str "data:text/plain;charset=utf-8,"
                   (js/encodeURIComponent (prn-str (rum/react event-log))))
        :download "debug-log.edn"}
    "DOWNLOAD DEBUG EVENT LOG"]
   [:pre (str (rum/react (stuff reconciler)))]
   (map-indexed
     (fn [idx e]
       [:div {:key idx}
        (prn-str e)])
     (rum/react event-log))])

(defn ^:dev/after-load start []
  (rum/mount (ui) (js/document.getElementById "app")))

(defmulti on-event first)

(defn on-message [message]
  (when-let [event (try
                     (edn/read-string (.-data message))
                     (catch js/Error ^js/Error err
                       (js/console.error (.-message err))
                       (prn (.-data message))
                       nil))]
    (swap! event-log conj event)
    (on-event event)))

;; init is called ONCE when the page loads
;; this is called in the index.html and must be exported
;; so it is available even in :advanced release builds
(defn init []
  (js/console.log "Initializing debugger...")
  (let [ws (js/WebSocket. (str "ws://" js/location.host "/ws"))]
    (.addEventListener ws "open"
                       (fn [_]
                         (js/console.log "Connected to WebSocket.")
                         (when (empty? @event-log)
                           (.send ws (prn-str [:replay-event-log])))))
    (.addEventListener ws "message" on-message)
    (.addEventListener ws "close"
                       #(do
                          (js/setTimeout
                            (fn []
                              (js/console.log "Attempting to re-initialize...")
                              (init))
                            1000)
                          (js/console.error "WebSocket connection closed!"))))
  (start))
