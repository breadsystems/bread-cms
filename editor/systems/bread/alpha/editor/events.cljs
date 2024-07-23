(ns systems.bread.alpha.editor.events
  (:require
    [systems.bread.alpha.editor.core :as core]))

(defmethod core/event! :replace-attrs
  [ed _ elem event-spec]
  (when-let [target-elem (core/get-target ed event-spec elem)]
    (doseq [[k v] (core/read-attr elem (name (:with event-spec)))]
      (.setAttribute target-elem (name k) v))))

(defmethod core/event! :update-state
  [ed _ elem event-spec]
  (when-let [target-elem (core/get-target ed event-spec elem)]
    (let [state (.getAttribute elem "data-bread-state")]
      (.setAttribute target-elem "data-bread-state" state))))
