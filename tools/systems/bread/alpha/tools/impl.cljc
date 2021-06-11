;; TODO extract channel/websocket stuff into a lib and dogfood it!
(ns systems.bread.alpha.tools.impl
  (:require
    [clojure.core.async :as async :refer [<! chan go-loop mult put! tap untap]]
    [systems.bread.alpha.tools.impl.util :refer [conjv]]))

(defonce db (atom {}))

;; PUBLISH to events>
(def ^:private events> (chan 1))
;; SUBSCRIBE to <events
(def ^:private <events (mult events>))

(defn publish!
  "Publishes e, broadcasting all subscribers (attached via subscribe!)"
  [e]
  (put! events> e))

(defn subscribe!
  "Subscribes (taps) to a mult of the <events channel, attaching f as a handler.
  Returns an unsubscribe callback that closes around the mult (calls untap)."
  [f]
  (let [listener (chan 1)]
    (tap <events listener)
    (go-loop []
             (let [e (<! listener)]
               (f e)
               (recur)))
    (fn []
      (untap <events listener))))

(defmulti on-event :event/type)

(defmethod on-event :default [e]
  (println "Unknown event type:" (:event/type e))
  (prn e))

;; NOTE: This gets overridden in CLJS!!!
(defmethod on-event :init [_]
  (reset! db {:request/uuid {}
              :request/uuids []}))

(defmethod on-event :bread/request [{req :event/request}]
  (swap! db
         (fn [state]
           (-> state
               (assoc-in
                 [:request/uuid (:request/uuid req)]
                 {:request/uuid (:request/uuid req)
                  :request/initial req})
               (update :request/uuids conjv (:request/uuid req))))))

(comment
  (deref db)
  (def on-event nil)
  (do
    (publish! {:event/type :init})
    (publish! {:event/type :bread/request
               :event/request {:uri "/"
                               :request/uuid "123-asdf"}})
    (publish! {:event/type :bread/request
               :event/request {:uri "/"
                               :request/uuid "456-qwerty"}})
    (publish! {:event/type :bread/request
               :event/request {:uri "/"
                               :request/uuid "789-foobar"}})))

(defn- update-req [state {:request/keys [uuid] :as e}]
  (-> state
      (assoc-in  [:request/uuid uuid :request/uuid] uuid)
      (update-in [:request/uuid uuid :request/hooks] conjv e)))

(defmethod on-event :bread/hook [hook-event]
  (swap! db update-req hook-event))

(defn subscribe-db
  "Returns at instance of the db (atom) and an unsubscribe callback. Note:
  This is the canonical way to get the db, since the db atom itself is stored
  internally in a private var."
  []
  [db (subscribe! on-event)])
