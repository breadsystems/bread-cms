(ns systems.bread.alpha.marx.api
  (:require
    [systems.bread.alpha.marx.core :as core]
    ["/MarxEditor$default" :as MarxEditor]
    ["react" :as react]
    ["react-dom/client" :as rdom]))

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

(def ^:private read-attr (memoize core/read-attr))

(defn mount! [ed elem]
  (let [field (read-attr elem "data-marx")
        field-name (:name field)
        !root (or
                (get-in @ed [:marx/fields field-name :root])
                (get-in (swap! ed assoc-in [:marx/fields field-name]
                               (assoc field
                                      :elem elem
                                      :root (rdom/createRoot elem)))
                        [:marx/fields field-name :root]))]
    (.render !root (MarxEditor #js {:children (js/Array.from (.-children elem))}))))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}}]
  (let [elements (vec (js/document.querySelectorAll (str "[" attr "]")))]
    (doseq [elem elements]
      (mount! ed elem))))
