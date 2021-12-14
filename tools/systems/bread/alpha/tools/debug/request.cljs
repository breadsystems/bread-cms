(ns systems.bread.alpha.tools.debug.request
  (:require
    [systems.bread.alpha.tools.debug.event :as e]
    [systems.bread.alpha.tools.debug.db :as db :refer [db]]
    [systems.bread.alpha.tools.impl.util :refer [conjv]]))

(defn- record-replay [state {replayed :profiler/replay-uuid
                             uuid :request/uuid}]
  (if replayed
    (update-in state [:request/uuid replayed :request/replays] conjv uuid)
    state))

(defmethod e/on-event :profile.type/request
  [[_ {uuid :request/uuid :as req}]]
  (swap! db (fn [state]
              (-> state
                  (assoc-in
                    [:request/uuid (str uuid)]
                    {:request/uuid (str uuid)
                     ;; Record the raw request on its own.
                     :request/initial req
                     ;; TODO make this a sorted-set?
                     :request/replays []})
                  (update :request/uuids conjv (str uuid))
                  (record-replay req)))))

(defmethod e/on-event :profile.type/response
  [[_ {uuid :response/uuid :as res}]]
  (swap! db assoc-in [:request/uuid uuid :request/response] res))

(defmethod e/on-event :ui/view-req [[_ uuid]]
  (swap! db assoc :ui/selected-req (str uuid)))

(defmethod e/on-event :ui/select-req [[_ idx]]
  (swap! db update :ui/selected-reqs
         (fn [selected]
           (if (selected idx)
             (disj selected idx)
             (conj selected idx)))))
