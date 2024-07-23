(ns systems.bread.alpha.marx.field.rich-text
  (:require
    #_
    ["react-dom/client" :as rdom]
    ["@tiptap/core" :refer [Editor] :rename {Editor TiptapEditor}]

    #_
    ["/EditorMenu" :refer [EditorMenu]]
    [systems.bread.alpha.marx.core :as core]
    [systems.bread.alpha.marx.tiptap :as tiptap]))

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
          #_#_ ;; TODO custom elem
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
         #_
         (.render menu-root (EditorMenu (clj->js menu-props))))
       nil)}))
