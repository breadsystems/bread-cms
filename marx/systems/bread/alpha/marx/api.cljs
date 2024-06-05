(ns systems.bread.alpha.marx.api
  (:require
    [systems.bread.alpha.marx.core :as core]))

(defn from-meta-element
  ([editor-name]
   (let [selector (str "meta[name=\"" (name editor-name) "\"]")]
     (core/read-attr (js/document.querySelector selector) "content")))
  ([]
   (from-meta-element "marx-editor")))

(defn editor [config]
  (assoc config
         :marx/fields {}
         :marx/listeners))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}}]
  (let [elements (vec (js/document.querySelectorAll (str "[" attr "]")))]
    (doseq [elem elements]
      (prn 'found elem))))
