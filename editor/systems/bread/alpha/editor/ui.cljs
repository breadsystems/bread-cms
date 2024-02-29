(ns systems.bread.alpha.editor.ui
  (:require
    [rum.core :as rum]))

;; TODO
(def t {:media-library "Media Library"
        :save "Save"
        :settings "Settings"})

(rum/defc EditorMenu < rum/reactive [toolbar config]
  [:div {:data-bread-menu true
         :data-field-name (:name config)
         :data-field-type (:type config)
         #_#_:on-click (fn [] (prn 'CLICK config toolbar))}
   (map (fn [{:keys [tool effect]}]
          (let [label (if (map? tool)
                        (or (:label tool) (:type tool))
                        tool)]
            [:button {:on-click effect} (str label)])) (:tools toolbar))])

(defn menu-element [{:keys [id]}]
  (let [elem (js/document.createElement "DIV")]
    (when id (.setAttribute elem "id" id))
    elem))

(defmulti bar-section (fn [_ section]
                         (cond
                           (keyword? section) section
                           :default (:type section))))

(defmethod bar-section :default [_ section]
  [:span
   (cond
     (keyword? section) (name section)
     :default (:name section))])

(defmethod bar-section :spacer [_ _]
  [:span {:data-bread-spacer true}])

(defmethod bar-section :site-name [ed _]
  [:span (:site/name ed)])

(defmethod bar-section :settings [ed {:keys [label]}]
  [:a {:href "#"
       :on-click (fn []
                   (prn 'SETTINGS!))}
   (or label (t :settings))])

(defmethod bar-section :media-library [ed {:keys [label]}]
  [:a {:href "#"
       :on-click (fn []
                   (prn 'MEDIA!))}
   (or label (t :media-library))])

(defmethod bar-section :save-button [ed _]
  [:button {:data-bread-button true
            :on-click #(prn 'SAVE)}
   (t :save)])

(rum/defc editor-bar < rum/reactive [{:bar/keys [sections] :as ed}]
  [:div {:data-bread-bar true}
   (map-indexed (fn [i section]
                  [:<> {:key i} (bar-section ed section)])
                sections)])
