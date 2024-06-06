(ns systems.bread.alpha.marx.field.bar
  (:require
    ["react" :as react]

    ["/Bar" :refer [Spacer
                    SiteNameSection
                    SettingsSection
                    MediaLibrarySection
                    SaveButtonSection
                    Bar]]
    [systems.bread.alpha.marx.core :as core]))

;; TODO
(def t {:media-library "Media Library"
        :save "Save"
        :settings "Settings"})

(defmulti bar-section (fn [_ section]
                        (prn 'section section)
                        (cond
                          (keyword? section) section
                          :default (:type section))))

(defmethod bar-section :spacer [_ _]
  (Spacer))

(defmethod bar-section :site-name [ed _]
  (SiteNameSection (clj->js {:siteName (:site/name ed)})))

(defmethod bar-section :settings [ed {:keys [label]}]
  (SettingsSection (clj->js {:label (or label (t :settings))
                             :onClick #(prn 'SETTINGS!)})))

(defmethod bar-section :media-library [ed {:keys [label]}]
  (MediaLibrarySection (clj->js {:label (or label (t :media-library))
                                 :onClick #(prn 'MEDIA!)})))

(defmethod bar-section :save-button [ed {:keys [label]}]
  (SaveButtonSection (clj->js {:label (or label (t :save))
                               :onClick #(prn 'SAVE!)})))

#_#_#_#_#_
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

(defmethod core/field-lifecycle :bar
  [ed field]
  {:render
   (fn [_]
     (let [ed-state @ed]
       (Bar (clj->js {:children (map (partial bar-section ed-state)
                                     (:bar/sections ed-state))}))))})
