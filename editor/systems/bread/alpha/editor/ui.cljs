(ns systems.bread.alpha.editor.ui
  (:require
    [rum.core :as rum]))

;; TODO
(def t {:media-library "Media Library"
        :save "Save"
        :settings "Settings"})

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
