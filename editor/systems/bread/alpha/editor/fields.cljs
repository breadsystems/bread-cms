(ns systems.bread.alpha.editor.fields
  (:require
    ["@tiptap/core" :refer [Editor]]
    ["@tiptap/extension-document" :refer [Document]]
    ["@tiptap/extension-dropcursor" :refer [Dropcursor]]
    ["@tiptap/extension-history" :refer [History]]
    ["@tiptap/extension-image" :refer [Image]]
    ["@tiptap/extension-paragraph" :refer [Paragraph]]
    ["@tiptap/extension-text" :refer [Text]]
    [systems.bread.alpha.editor.core :as core]))

(defmethod core/init-field! :default [_ _ config]
  (when-not (:derived? config)
    (js/console.error (str "No init-field! multimethod defined for: "
                           (prn-str (:type config))))))

(defn- ->dom-event [event-key]
  ;; TODO get this to work for camelCased events like mouseDown.
  ;; See React's impl:
  ;; https://github.com/facebook/react/blob/17806594cc28284fe195f918e8d77de3516848ec/packages/react-dom/src/events/DOMEventProperties.js#L120-125
  (subs (name event-key) 3))

(comment
  (->dom-event :on-click))

(defmethod core/init-field! :repeater
  [ed element {:keys [each] :as config}]
  (when each
    (doseq [child (.querySelectorAll element (:selector each))]
      (doseq [[event-key event-spec] (:events each)]
        (let [handler (if (sequential? event-spec)
                        (fn [e] (doseq [event event-spec]
                                  (core/event! ed e child event)))
                        (fn [e] (core/event! ed e child event-spec)))]
          (core/listen! ed child (->dom-event event-key) handler))))))

(defmethod core/init-field! :rich-text
  [ed element config]
  (let [clone (.cloneNode element true)]
    (Editor. (clj->js {:element (.-parentNode element)
                       :extensions [Document Dropcursor History Image Paragraph Text]
                       :content (.-outerHTML element)}))
    (.removeChild (.-parentNode element) element)))
