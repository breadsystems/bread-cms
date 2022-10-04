(ns systems.bread.alpha.editor.tiptap
  (:require
    ["@tiptap/core" :refer [Editor]]
    ["@tiptap/extension-blockquote" :refer [Blockquote]]
    ["@tiptap/extension-bold" :refer [Bold]]
    ["@tiptap/extension-bullet-list" :refer [BulletList]]
    ["@tiptap/extension-code" :refer [Code]]
    ["@tiptap/extension-code-block" :refer [CodeBlock]]
    ["@tiptap/extension-document" :refer [Document]]
    ["@tiptap/extension-dropcursor" :refer [Dropcursor]]
    ["@tiptap/extension-gapcursor" :refer [Gapcursor]]
    ["@tiptap/extension-hard-break" :refer [HardBreak]]
    ["@tiptap/extension-heading" :refer [Heading]]
    ["@tiptap/extension-history" :refer [History]]
    ["@tiptap/extension-image" :refer [Image]]
    ["@tiptap/extension-italic" :refer [Italic]]
    ["@tiptap/extension-list-item" :refer [ListItem]]
    ["@tiptap/extension-ordered-list" :refer [OrderedList]]
    ["@tiptap/extension-paragraph" :refer [Paragraph]]
    ["@tiptap/extension-strike" :refer [Strike]]
    ["@tiptap/extension-text" :refer [Text]]))

(defmulti extension (fn [ed tool]
                      (cond
                        (map? tool) (:type tool)
                        :default tool)))

(defmethod extension :heading
  [_ed {:keys [levels]
        ;; Levels 2+ are supported by default, to avoid content authors
        ;; accidentally introducing multiple h1's on the page.
        :or {levels [2 3 4 5 6]}}]
  [(.configure Heading (clj->js {:levels levels}))])

(defmethod extension :bold [_ _] [Bold])
(defmethod extension :italic [_ _] [Italic])
(defmethod extension :blockquote [_ _] [Blockquote])
(defmethod extension :ul [_ _] [BulletList ListItem])
(defmethod extension :ol [_ _] [OrderedList ListItem])
(defmethod extension :strike [_ _] [Strike])

(def default-extensions
  [Document
   Dropcursor
   History
   Paragraph
   Text])

(def default-rich-text-tools
  [{:type :heading :levels [2 3 4 5 6]}
   :bold :italic :blockquote :ul :ol :strike])

(defn extensions [ed tools]
  (mapcat #(extension ed %) tools))

(defn mount-tiptap-editor! [{:keys [element extensions]}]
  (Editor. (clj->js {:element (.-parentNode element)
                     :extensions extensions
                     :content (.-outerHTML element)}))
  (.removeChild (.-parentNode element) element))
