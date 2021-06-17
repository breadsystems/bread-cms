;; TODO extract channel/websocket stuff into a lib and dogfood it!
(ns systems.bread.alpha.tools.impl
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.edn :as edn]
    [clojure.walk :as walk]
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
  nil)

;; NOTE: This gets overridden in CLJS!!!
(defmethod on-event :init [_]
  (reset! db {:request/uuid {}
              :request/uuids []
              :ui/preferences {:replay-as-of? true}}))

(defmethod on-event :clear-requests [_]
  (swap! db assoc
         :request/uuid {}
         :request/uuids []
         :ui/selected-reqs (sorted-set)
         :ui/selected-req nil))

(defn- record-replay [state {replayed :profiler/replay-uuid
                             uuid :request/uuid}]
  (if replayed
    (update-in state [:request/uuid replayed :request/replays] conjv uuid)
    state))

(defmethod on-event :bread/request [{req :event/request}]
  (swap! db
         (fn [state]
           (-> state
               (assoc-in
                 [:request/uuid (:request/uuid req)]
                 {:request/uuid (:request/uuid req)
                  :request/initial req
                  :request/replays []})
               (update :request/uuids conjv (:request/uuid req))
               (record-replay req)))))

(defmethod on-event :bread/response [{res :event/response}]
  (let [uuid (str (:request/uuid res))]
    (swap! db
           (fn [state]
             (assoc-in state [:request/uuid uuid :request/response] res)))))

(comment
  (deref db)
  (def on-event nil)
  (publish! {:event/type :init})

  ;;
  )

(defn- update-req [state {:request/keys [uuid] :as e}]
  (-> state
      (assoc-in  [:request/uuid uuid :request/uuid] uuid)
      (update-in [:request/uuid uuid :request/hooks] conjv e)))

(defmethod on-event [:bread/hook :hook/render] [{:request/keys [uuid] :as e}]
  (swap! db assoc-in [:request/uuid uuid :response/pre-render]
         (some-> e :args first :body)))

(defmethod on-event :bread/hook [{:keys [hook] :as e}]
  (on-event (merge e {:event/type [:bread/hook hook]}))
  (swap! db update-req e))

(defn subscribe-db
  "Returns at instance of the db (atom) and an unsubscribe callback. Note:
  This is the canonical way to get the db, since the db atom itself is stored
  internally in a private var."
  []
  [db (subscribe! on-event)])
