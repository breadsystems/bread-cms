(ns systems.bread.alpha.editor.fields
  (:require
    [systems.bread.alpha.editor.core :as core]))

(defmethod core/init-field! :default [_ config]
  (when-not (:synthetic? config)
    (js/console.error (str "No event! multimethod defined for: "
                           (prn-str (:type config))))))

(defmethod core/init-field! :repeater
  [element {:keys [each] :as config}]
  (when each
    (let [{:keys [on-click]} each
          ;; TODO more event handlers
          ]
      (doseq [child (.querySelectorAll element (:selector each))]
        (core/listen! child "click" (core/handler child on-click))))))
