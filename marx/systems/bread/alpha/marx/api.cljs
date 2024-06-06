(ns systems.bread.alpha.marx.api
  (:require
    ["react" :as react]
    ["react-dom/client" :as rdom]
    ["@tiptap/core" :refer [Editor] :rename {Editor TiptapEditor}]

    [systems.bread.alpha.marx.field.bar :as bar]
    ["/MarxEditor$default" :as MarxEditor]
    ["/EditorMenu$default" :as EditorMenu]
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

(defmethod core/tool-props :bold [ed tool]
  {:tooltip "Bold"
   :icon "bold"})

(defmethod core/field-lifecycle :rich-text
  [ed {:keys [state elem] :as field}]
  (let [tools (or (:tools field) (:tools ed) tiptap/default-rich-text-tools)]
    {:init-state
     (fn []
       (let [menu-elem (js/document.createElement "DIV")
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
     (fn [{:keys [menu-root tiptap]}]
       (let [menu-tool (fn [tool]
                         (let [tk (if (map? tool) (:type tool) tool)
                               props {:type tk
                                      :content (name tk)
                                      :effect #(tiptap/command tiptap tool)}]
                           (merge props (core/tool-props @ed props))))
             menu-props {:tools (map menu-tool tools)}]
         (.render menu-root (EditorMenu (clj->js menu-props))))
       nil)}))

(def bar-section bar/bar-section)

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (core/fields-from-editor ed)
                 (core/fields-from-dom config))]
    (doseq [field fields]
      (core/init-field* ed field))))
