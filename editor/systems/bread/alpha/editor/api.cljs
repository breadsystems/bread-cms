(ns systems.bread.alpha.editor.api
  (:require
    [systems.bread.alpha.editor.core :as core]))

(defn init! [{:keys [attr]
                     :or {attr "data-bread"}}]
  (let [selector (str "[" attr "]")]
    (doseq [elem (js/document.querySelectorAll selector)]
      (let [config (core/read-attr elem attr)]
        (core/declare-field! elem config)
        (core/init-field! elem config)))))
