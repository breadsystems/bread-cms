(ns systems.bread.alpha.editor.fields
  (:require
    [systems.bread.alpha.editor.core :as core]))

(defmethod core/init-field! :default [_ _ config]
  (when-not (:synthetic? config)
    (js/console.error (str "No init-field! multimethod defined for: "
                           (prn-str (:type config))))))

(defmethod core/init-field! :repeater
  [ed element {:keys [each] :as config}]
  (when each
    (let [{:keys [on-click]} each
          ;; TODO more event handlers
          ]
      (doseq [child (.querySelectorAll element (:selector each))]
        (core/listen! ed child "click" (fn [e] (core/event! ed e child on-click)))))))
