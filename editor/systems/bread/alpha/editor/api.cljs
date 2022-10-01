(ns systems.bread.alpha.editor.api
  (:require
    [systems.bread.alpha.editor.core :as core]
    [systems.bread.alpha.editor.fields]
    [systems.bread.alpha.editor.events]))

(defn editor [config]
  (assoc config
         :fields {}
         :listeners {}))

(defn init! [ed {:keys [attr]
                 :or {attr "data-bread"}}]
  (let [selector (str "[" attr "]")]
    (doseq [elem (js/document.querySelectorAll selector)]
      (let [config (core/read-attr elem attr)]
        (core/declare-field! ed elem config)
        (core/init-field! ed elem config)))))
