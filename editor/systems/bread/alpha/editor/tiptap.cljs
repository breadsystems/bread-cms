(ns systems.bread.alpha.editor.tiptap
  (:require
    ["@tiptap/core" :refer [Editor]]
    ["@tiptap/extension-document" :refer [Document]]
    ["@tiptap/extension-dropcursor" :refer [Dropcursor]]
    ["@tiptap/extension-history" :refer [History]]
    ["@tiptap/extension-image" :refer [Image]]
    ["@tiptap/extension-paragraph" :refer [Paragraph]]
    ["@tiptap/extension-text" :refer [Text]]))

(def default-extensions
  [Document
   Dropcursor
   History
   Image
   Paragraph
   Text])

(defn mount-tiptap-editor! [{:keys [element extensions]}]
  (Editor. (clj->js {:element (.-parentNode element)
                     :extensions extensions
                     :content (.-outerHTML element)}))
  (.removeChild (.-parentNode element) element))
