(ns systems.bread.alpha.editor.ui
  (:require
    [rum.core :as rum]
    [systems.bread.alpha.editor.internal.macros
     :include-macros true :refer [i]]))

;; TODO
(def t {:media-library "Media Library"
        :save "Save"
        :settings "Settings"})

(defmulti tool-label identity)

(defmethod tool-label :default [kw]
  (clojure.string/upper-case (str (first (name kw)))))

(defmethod tool-label :bold       [_] (i :Editor :bold))
(defmethod tool-label :italic     [_] (i :Editor :italic))
(defmethod tool-label :blockquote [_] (i :Editor :double-quotes-l))
(defmethod tool-label :ul         [_] (i :Editor :list-unordered))
(defmethod tool-label :ol         [_] (i :Editor :list-ordered))
(defmethod tool-label :strike     [_] (i :Editor :strikethrough))
(defmethod tool-label :highlight  [_] (i :Design :mark-pen-line))
(defmethod tool-label :sup        [_] (i :Editor :superscript))
(defmethod tool-label :sub        [_] (i :Editor :subscript))
(defmethod tool-label :code       [_] (i :Development :code-line))
(defmethod tool-label :codeblock  [_] (i :Editor :code-block))
(defmethod tool-label :br         [_] (i :Arrows :corner-down-left-line))
(defmethod tool-label :hr         [_] (i :Editor :separator))

(rum/defc EditorMenu < rum/reactive [toolbar config]
  [:div {:data-bread-menu true
         :data-field-name (:name config)
         :data-field-type (:type config)}
   (map (fn [{:keys [tool effect]}]
          (let [tk (if (map? tool) (:type tool) tool)]
            [:button {:key tool
                      :on-click effect
                      :title (:tooltip tool (name tk))}
             (tool-label tk)])) (:tools toolbar))])

(defn menu-element [{:keys [id]}]
  (let [elem (js/document.createElement "DIV")]
    (when id (.setAttribute elem "id" id))
    elem))

(defmulti BarSection (fn [_ section]
                         (cond
                           (keyword? section) section
                           :default (:type section))))

(defmethod BarSection :default [_ section]
  [:span
   (cond
     (keyword? section) (name section)
     :default (:name section))])

(defmethod BarSection :spacer [_ _]
  [:span {:data-bread-spacer true}])

(defmethod BarSection :site-name [ed _]
  [:span (:site/name ed)])

(defmethod BarSection :settings [ed {:keys [label]}]
  [:a {:href "#"
       :on-click (fn []
                   (prn 'SETTINGS!))}
   (or label (t :settings))])

(defmethod BarSection :media-library [ed {:keys [label]}]
  [:a {:href "#"
       :on-click (fn []
                   (prn 'MEDIA!))}
   (or label (t :media-library))])

(defmethod BarSection :save-button [ed _]
  [:button {:data-bread-button true
            :on-click #(prn 'SAVE)}
   (t :save)])

(rum/defc EditorBar < rum/reactive [{:bar/keys [sections] :as ed}]
  [:div {:data-bread-bar true}
   (map-indexed (fn [i section]
                  [:<> {:key i} (BarSection ed section)])
                sections)])
