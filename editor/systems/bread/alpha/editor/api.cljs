(ns systems.bread.alpha.editor.api
  (:require
    [rum.core :as rum]
    [systems.bread.alpha.editor.core :as core]
    [systems.bread.alpha.editor.ui :as ui]
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
        (swap! ed
               assoc-in [:fields (:name config)] (assoc config :element elem))
        (core/init-field! ed elem config))))
  (when-let [mount-point (js/document.querySelector (:bar/mount-into @ed))]
    (rum/mount (ui/editor-bar @ed) mount-point)))
