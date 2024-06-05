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

(defn field [ed field-name]
  (get-in ed [:marx/fields field-name]))

(def read-attr (memoize core/read-attr))

(defn- persist-field! [ed field elem & kvs]
  (swap! ed assoc-in [:marx/fields (:name field)]
         (apply assoc field
                :elem elem
                :persisted? true
                kvs)))

(defn- recreate-tiptap [])

(defmethod core/init-field! :rich-text
  [ed field elem]
  (let [tools (or (:tools field)
                  (:tools ed)
                  tiptap/default-rich-text-tools)
        menu-elem (doto
                    (js/document.createElement "DIV")
                    (.setAttribute "id" "editor-menu"))
        extensions (tiptap/extensions ed tools
                                      {:menu-element menu-elem})
        ;; TODO conditionally instantiate TiptapEditor
        tiptap (TiptapEditor. (clj->js {:element (.-parentNode elem)
                                        :extensions extensions
                                        :content (.-outerHTML elem)}))]
    (persist-field! ed field elem :tiptap tiptap :menu-elem menu-elem)
    ;; TODO createRoot
    (.render (rdom/createRoot menu-elem) (EditorBar))
    (prn 'tiptap (:tiptap field))
    (.removeChild (.-parentNode elem) elem)))

(defmethod core/init-field! :editor-bar
  [ed field elem]
  (let [field-name (:name field)
        !root (or
                (get-in @ed [:marx/fields field-name :root])
                (get-in (persist-field! ed field elem
                                        :root (rdom/createRoot elem))
                        [:marx/fields field-name :root]))]
    (prn 'render field elem)
    (.render !root (EditorBar #js {:children (js/Array.from (.-children elem))}))))

(defn init-field! [ed config {:keys [elem persisted?] :as field}]
  (let [field (if persisted?
                field
                (read-attr elem (:attr config "data-marx")))]
    (prn 'init field)
    (core/init-field! ed field elem)))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (vals (:marx/fields @ed))
                 (map
                   (fn [elem] {:elem elem})
                   (vec (js/document.querySelectorAll (str "[" attr "]")))))]
    (prn 'fields (map (juxt :name :persisted?) fields))
    (doseq [field fields]
      (init-field! ed config field))))
