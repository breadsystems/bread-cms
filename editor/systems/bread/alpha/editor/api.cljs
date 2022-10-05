(ns systems.bread.alpha.editor.api
  (:require
    [rum.core :as rum]
    [systems.bread.alpha.editor.core :as core]
    [systems.bread.alpha.editor.ui :as ui]
    [systems.bread.alpha.editor.fields]
    [systems.bread.alpha.editor.events]))

(def init-field! core/init-field!)
(def get-field core/get-field)
(def listen! core/listen!)
(def event! core/event!)
(def read-attr core/read-attr)

(defn from-meta-element []
  (core/read-attr
    (js/document.querySelector "meta[name=bread-editor]")
    "content"))

(defn editor [config]
  (assoc config
         :bread/fields {}
         :bread/listeners {}))

(defn init! [ed {:keys [attr]
                 :or {attr "data-bread"}}]
  (let [elements (set (or
                        (seq (map (comp :element val)
                                  (:bread/fields @ed)))
                        (js/document.querySelectorAll
                          (str "[" attr "]"))))]
    (doseq [elem elements]
      (let [config (core/read-attr elem attr)]
        (swap! ed assoc-in [:bread/fields (:name config)]
               {:config config
                :element elem})
        (core/init-field! ed elem config)))
    (when-let [mount-point (js/document.querySelector (:bar/mount-into @ed))]
      (rum/mount (ui/editor-bar @ed) mount-point))))
