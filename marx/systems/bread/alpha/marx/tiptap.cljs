(ns systems.bread.alpha.marx.tiptap
  (:require
    ["@tiptap/core" :refer [Editor] :rename {Editor TiptapEditor}]
    ["@tiptap/extension-blockquote" :refer [Blockquote]]
    ["@tiptap/extension-bold" :refer [Bold]]
    ["@tiptap/extension-bubble-menu" :refer [BubbleMenu]]
    ["@tiptap/extension-bullet-list" :refer [BulletList]]
    ["@tiptap/extension-code" :refer [Code]]
    ["@tiptap/extension-collaboration" :refer [Collaboration]]
    ["@tiptap/extension-collaboration-cursor" :refer [CollaborationCursor]]
    ["@tiptap/extension-code-block" :refer [CodeBlock]]
    ["@tiptap/extension-document" :refer [Document]]
    ["@tiptap/extension-dropcursor" :refer [Dropcursor]]
    ["@tiptap/extension-gapcursor" :refer [Gapcursor]]
    ["@tiptap/extension-floating-menu" :refer [FloatingMenu]]
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
                      (if (map? tool) (:type tool) tool)))

(defmethod extension :heading
  [_ed {:keys [levels]
        ;; Levels 2+ are supported by default, to avoid content authors
        ;; accidentally introducing multiple h1's on the page.
        :or {levels [2 3 4 5 6]}}]
  [(.configure Heading (clj->js {:levels levels}))])

(defmethod extension :bold       [_ _] [Bold])
(defmethod extension :italic     [_ _] [Italic])
(defmethod extension :blockquote [_ _] [Blockquote])
(defmethod extension :ul         [_ _] [BulletList ListItem])
(defmethod extension :ol         [_ _] [OrderedList ListItem])
(defmethod extension :strike     [_ _] [Strike])
(defmethod extension :highlight  [_ _] [Highlight])
(defmethod extension :sub        [_ _] [Subscript])
(defmethod extension :sup        [_ _] [Superscript])
(defmethod extension :code       [_ _] [Code])
(defmethod extension :codeblock  [_ _] [CodeBlock])
(defmethod extension :br         [_ _] [HardBreak])
(defmethod extension :hr         [_ _] [HorizontalRule])

(defmulti command (fn [_tiptap tool] (if (map? tool) (:type tool) tool)))

(defmethod command :heading    [tiptap {:keys [level]}] (-> tiptap .chain .focus (.toggleHeading #js {:level level}) .run))
(defmethod command :bold       [tiptap _] (-> tiptap .chain .focus .toggleBold .run))
(defmethod command :italic     [tiptap _] (-> tiptap .chain .focus .toggleItalic .run))
(defmethod command :blockquote [tiptap _] (-> tiptap .chain .focus .toggleBlockquote .run))
(defmethod command :ul         [tiptap _] (-> tiptap .chain .focus .toggleBulletList .run))
(defmethod command :ol         [tiptap _] (-> tiptap .chain .focus .toggleOrderedList .run))
(defmethod command :strike     [tiptap _] (-> tiptap .chain .focus .toggleStrike .run))
(defmethod command :highlight  [tiptap _] (-> tiptap .chain .focus .toggleHighlight .run))
(defmethod command :sub        [tiptap _] (-> tiptap .chain .focus .toggleSubscript .run))
(defmethod command :sup        [tiptap _] (-> tiptap .chain .focus .toggleSuperscript .run))
(defmethod command :code       [tiptap _] (-> tiptap .chain .focus .toggleCode .run))
(defmethod command :codeblock  [tiptap _] (-> tiptap .chain .focus .toggleCodeBlock .run))
(defmethod command :br         [tiptap _] (-> tiptap .chain .focus .setHardBreak .run))
(defmethod command :hr         [tiptap _] (-> tiptap .chain .focus .setHorizontalRule .run))

;; DEPRECATED. Decalre tooltip etc. on tool-specific multimethod instead.
(def default-rich-text-tools
  [{:type :heading :label :h2 :level 2}
   {:type :heading :label :h3 :level 3}
   {:type :heading :label :h4 :level 4}
   {:type :heading :label :h5 :level 5}
   {:type :heading :label :h6 :level 6}
   :bold
   :italic
   {:type :ul     :tooltip "Numbered list"}
   {:type :ol     :tooltip "Bullet list"}
   {:type :strike :tooltip "Strikethrough"}
   :blockquote
   :highlight
   {:type :sup    :tooltip "Superscript"}
   {:type :sub    :tooltip "Subscript"}
   :code
   :codeblock
   {:type :hr     :tooltip "Horizontal line"}
   {:type :br     :tooltip "Line break"}])

(defn extensions [ed tools {:keys [menu-element] :as _opts}]
  (let [{:keys [collab menu tiptap]
         :or {menu {:style :floating}}} @ed
        {:keys [ydoc provider user]} collab
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
         (when menu
           (if (= :floating (:style menu))
             (.configure FloatingMenu
                         (clj->js {:element menu-element
                                   :shouldShow (constantly true)
                                   :tippyOptions {:placement "top-start"
                                                  :maxWidth "100%"}}))
             (.configure BubbleMenu
                         (clj->js {:element menu-element}))))
         Document
         Dropcursor
         Paragraph
         (.configure Placeholder placeholder-opts)
         Text
         Typography]
        (mapcat #(extension ed %) tools)))))
