(ns systems.bread.alpha.tools.debug.request
  (:require
    [systems.bread.alpha.tools.debug.event :as e]
    [systems.bread.alpha.tools.debug.db :as db :refer [db]]
    [systems.bread.alpha.tools.impl.util :refer [conjv]]
    [systems.bread.alpha.tools.util :refer [shorten-uuid]]))

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
                    [:request/uuid uuid]
                    {:request/uuid uuid
                     :request/id (shorten-uuid uuid)
                     ;; Record the raw request on its own.
                     :request/initial req
                     ;; TODO make this a sorted-set?
                     :request/replays []})
                  (update :request/uuids conjv uuid)
                  (record-replay req)))))

(defmethod e/on-event :profile.type/response
  [[_ {uuid :response/uuid :as res}]]
  (swap! db assoc-in [:request/uuid uuid :request/response] res))

(defmethod e/on-event :profile.type/hook
  [[_ {{rid :request/uuid} :hook/request :as invocation}]]
  (swap! db
         update-in [:request/uuid (str rid) :request/hooks]
         conjv invocation))


(defmethod e/on-event :ui/view-req [[_ uuid]]
  (swap! db assoc :ui/selected-req (str uuid)))

(defmethod e/on-event :ui/select-req [[_ idx]]
  (swap! db update :ui/selected-reqs
         (fn [selected]
           (if (selected idx)
             (disj selected idx)
             (conj selected idx)))))
