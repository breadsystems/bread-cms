;; TODO extract channel/websocket stuff into a lib and dogfood it!
(ns systems.bread.alpha.tools.impl
  (:require
    [clojure.core.async :as async :refer [<! chan go-loop mult put! tap untap]]
    [systems.bread.alpha.tools.impl.util :refer [conjv]]))

(defonce db (atom {:request/uuid {}}))

;; PUBLISH to events>
(defonce ^:private events> (chan 1))
;; SUBSCRIBE to <events
(defonce ^:private <events (mult events>))

(defn publish!
  "Publishes e, broadcasting all subscribers (attached via subscribe!)"
  [e]
  (put! events> e))

(defn subscribe!
  "Subscribes (taps) to a mult of the <events channel, attaching f as a handler.
  Returns an unsubscribe callback that closes around the mult (calls untap)."
  [f]
  (let [listener (chan)]
    (tap <events listener)
    (go-loop []
             (let [e (<! listener)]
               (prn 'calling (list f e))
               (f e)
               (recur)))
    (fn []
      (untap <events listener))))

(defmulti on-event :event/type)

(defmethod on-event :default [e]
  (js/console.log "Unknown event type:" (:event/type e)))

(defmethod on-event :bread/hook [e]
  (let [{:keys [uuid]} e]
    (swap! db update-in [:request/uuid uuid :request/hooks] conjv e)))

(defn subscribe-db
  "Returns at instance of the db (atom) and an unsubscribe callback. Note:
  This is the canonical way to get the db, since the db atom itself is stored
  internally in a private var."
  []
  [db (subscribe! on-event)])

(comment
  (slurp "http://localhost:1312"))
