(ns systems.bread.alpha.tools.impl
  (:require
    [systems.bread.alpha.tools.impl.util :refer [conjv]]))

(defonce db (atom {:request/uuid {}}))

(defmulti on-event :event/type)
(defmethod on-event :default [e]
  (js/console.log "Unknown event type:" (:event/type e)))
(defmethod on-event :bread/hook [e]
  (let [{:keys [uuid]} e]
    (swap! db update-in [:request/uuid uuid :request/hooks] conjv e)))
