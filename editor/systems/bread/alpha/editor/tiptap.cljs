(ns systems.bread.alpha.editor.tiptap
  (:require
    ["@tiptap/core" :refer [Editor] :rename {Editor TiptapEditor}]
    ["@tiptap/extension-blockquote" :refer [Blockquote]]
    ["@tiptap/extension-bold" :refer [Bold]]
    ["@tiptap/extension-bullet-list" :refer [BulletList]]
    ["@tiptap/extension-code" :refer [Code]]
    ["@tiptap/extension-collaboration" :refer [Collaboration]]
    ["@tiptap/extension-collaboration-cursor" :refer [CollaborationCursor]]
    ["@tiptap/extension-code-block" :refer [CodeBlock]]
    ["@tiptap/extension-document" :refer [Document]]
    ["@tiptap/extension-dropcursor" :refer [Dropcursor]]
    ["@tiptap/extension-gapcursor" :refer [Gapcursor]]
    ["@tiptap/extension-hard-break" :refer [HardBreak]]
    ["@tiptap/extension-heading" :refer [Heading]]
    ["@tiptap/extension-highlight" :refer [Highlight]]
    ["@tiptap/extension-history" :refer [History]]
    ["@tiptap/extension-horizontal-rule" :refer [HorizontalRule]]
    ["@tiptap/extension-image" :refer [Image]]
    ["@tiptap/extension-italic" :refer [Italic]]
    ["@tiptap/extension-list-item" :refer [ListItem]]
    ["@tiptap/extension-ordered-list" :refer [OrderedList]]
    ["@tiptap/extension-paragraph" :refer [Paragraph]]
    ["@tiptap/extension-placeholder" :refer [Placeholder]]
    ["@tiptap/extension-strike" :refer [Strike]]
    ["@tiptap/extension-subscript" :refer [Subscript]]
    ["@tiptap/extension-superscript" :refer [Superscript]]
    ["@tiptap/extension-text" :refer [Text]]
    ["@tiptap/extension-typography" :refer [Typography]]
    ["yjs" :as Y]
    ["y-webrtc" :refer [WebrtcProvider]]))

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
(defmethod extension :highlight [_ _] [Highlight])
(defmethod extension :sub [_ _] [Subscript])
(defmethod extension :sup [_ _] [Superscript])
(defmethod extension :code [_ _] [Code])
(defmethod extension :codeblock [_ _] [CodeBlock])
(defmethod extension :br [_ _] [HardBreak])
(defmethod extension :hr [_ _] [HorizontalRule])

(def default-rich-text-tools
  [{:type :heading :levels [2 3 4 5 6]}
   :bold :italic :blockquote :ul :ol :strike :highlight :sup :sub
   :code :codeblock :hr :br])

(defn extensions [ed tools]
  (let [{:keys [ydoc provider user] :as collab} (:collab @ed)
        placeholder-opts (clj->js {;; TODO parameterize this
                                   :placeholder "Start writing..."
                                   :emptyEditorClass "bread-editor--empty"})]
    (filter
      identity
      (concat
        [;; The Collaboration extension manages its own history, so these two
         ;; are mutually exclusive.
         (if collab
           (.configure Collaboration #js {:document ydoc})
           History)
         (when collab
           (.configure CollaborationCursor (clj->js {:provider provider
                                                     :user user})))
         Document
         Dropcursor
         Paragraph
         (.configure Placeholder placeholder-opts)
         Text
         Typography]
        (mapcat #(extension ed %) tools)))))

(comment
  (def a (atom {:a {:b {}}}))
  (deref a)
  (swap! a update-in [:a :b] assoc :c (js/Date.))
  (get-in @a [:a :b :c]))

(defn mount! [{:keys [editor element extensions]
               {field-name :name} :config}]
  ;; TODO figure out how to re-mount the entire editor
  (when-let [tiptap-inst (get-in @editor [:bread/fields field-name :tiptap])]
    (prn 'TIPTAP? tiptap-inst)
    (.destroy tiptap-inst))
  (let [tiptap-inst
        (TiptapEditor. (clj->js {:element (.-parentNode element)
                                 :extensions extensions
                                 :content (.-outerHTML element)}))]
    (prn 'SET tiptap-inst)
    (swap! editor update-in [:bread/fields field-name] assoc
           :tiptap tiptap-inst))
  (.removeChild (.-parentNode element) element))
