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
         :data-field-type (:type config)}
   (map (fn [{:keys [tool effect]}]
          (let [label (if (map? tool)
                        (or (:label tool) (:type tool))
                        tool)]
            [:button {:key tool :on-click effect} (str label)])) (:tools toolbar))])

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
