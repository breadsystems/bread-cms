(ns systems.bread.alpha.marx.api
  (:require
    ["react" :as react]
    ["react-dom/client" :as rdom]
    ["@tiptap/core" :refer [Editor] :rename {Editor TiptapEditor}]

    ["/MarxEditor$default" :as MarxEditor]
    ["/EditorBar$default" :as EditorBar]
    [systems.bread.alpha.marx.core :as core]
    [systems.bread.alpha.marx.tiptap :as tiptap]))

(defn from-meta-element
    ([editor-name]
   (let [selector (str "meta[name=\"" (name editor-name) "\"]")]
     (core/read-attr (js/document.querySelector selector) "content")))
  ([]
   (from-meta-element "marx-editor")))

(defn editor [config]
  (assoc config :marx/fields {}))

(defn elements [ed]
  (map :elem (vals (:marx/fields ed))))

(defmethod core/field-lifecycle :rich-text
  [ed {:keys [state elem] :as field}]
  {:init-state
   (fn []
     (let [tools (or (:tools field) (:tools ed) tiptap/default-rich-text-tools)
           menu-elem (js/document.createElement "DIV")
           extensions (tiptap/extensions ed tools {:menu-element menu-elem})]
       {:menu-elem menu-elem
        :menu-root (rdom/createRoot menu-elem)
        :extensions extensions
        :tiptap (TiptapEditor. (clj->js {:element (.-parentNode elem)
                                         :extensions extensions
                                         :content (.-outerHTML elem)}))}))
   :did-mount
   (fn [state]
     (.removeChild (.-parentNode elem) elem))
   :render
   (fn [state]
     (.render (:menu-root state) (EditorBar))
     nil)})

(defmethod core/field-lifecycle :editor-bar
  [ed field]
  {:render
   (fn [state]
     (EditorBar))})

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (core/fields-from-editor ed)
                 (core/fields-from-dom config))]
    (doseq [field fields]
      (core/init-field* ed field))))
