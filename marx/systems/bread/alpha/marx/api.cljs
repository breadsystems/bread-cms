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
                :initialized? true
                kvs)))

(defmethod core/init-field! :rich-text
  [ed field]
  (let [elem (:elem field)
        tools (or (:tools field)
                  (:tools ed)
                  tiptap/default-rich-text-tools)
        menu-elem (doto
                    (js/document.createElement "DIV")
                    (.setAttribute "id" "editor-menu"))
        extensions (tiptap/extensions ed tools
                                      {:menu-element menu-elem})
        ;; TODO RENDER
        tiptap (TiptapEditor. (clj->js {:element (.-parentNode elem)
                                        :extensions extensions
                                        :content (.-outerHTML elem)}))
        ;; TODO INIT
        root (or (:menu-react-root field)
                 (doto (rdom/createRoot menu-elem) (prn 'createRoot)))]
    ;; TODO RENDER
    (.render root (EditorBar))
    ;; TODO INIT
    (persist-field! ed field elem :tiptap tiptap :menu-elem menu-elem :menu-react-root root)
    (when (nil? (:tiptap field)) (prn 'TiptapEditor! tiptap))
    ;; TODO INIT?
    (.removeChild (.-parentNode elem) elem)))

(defmethod core/render-field! :rich-text
  [ed field]
  (prn 'TODO 'render :rich-text))

(defmethod core/init-field! :editor-bar
  [ed field]
  (let [elem (:elem field)
        field-name (:name field)
        !root (or
                (get-in @ed [:marx/fields field-name :root])
                (get-in (persist-field! ed field elem
                                        :root (rdom/createRoot elem))
                        [:marx/fields field-name :root]))]
    (prn 'init! field elem)
    (.render !root (EditorBar #js {:children (js/Array.from (.-children elem))}))))

(defmethod core/render-field! :editor-bar
  [ed field]
  (prn 'TODO 'render :editor-bar))

(defn- fields-from-editor [ed]
  (vals (:marx/fields @ed)))

(defn- fields-from-dom [config]
  (let [attr (:attr config "data-marx")
        selector (str "[" attr "]")
        elems (vec (js/document.querySelectorAll selector))]
    (map (fn [elem]
           (assoc (read-attr elem attr)
                  :elem elem))
         elems)))

(defn init! [ed {:keys [attr]
                 :or {attr "data-marx"}
                 :as config}]
  (let [fields (or
                 (fields-from-editor ed)
                 (fields-from-dom config))]
    (doseq [field fields]
      (core/init-field* ed field))))
