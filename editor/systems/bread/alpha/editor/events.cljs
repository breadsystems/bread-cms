(ns systems.bread.alpha.editor.events
  (:require
    [systems.bread.alpha.editor.core :as core]))

(defmethod core/event! :replace-attrs
  [_ elem {:keys [target target-field with]}]
  (when-let [target-elem (or (js/document.querySelector target)
                             (:element (core/get-field target-field)))]
    (doseq [[k v] (core/read-attr elem (name with))]
      (.setAttribute target-elem (name k) v))))
