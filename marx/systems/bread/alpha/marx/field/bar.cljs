(ns systems.bread.alpha.marx.field.bar
  (:require
    ["react" :as react]

    ["/Button" :refer [Button]]
    ["/Spacer" :refer [Spacer]]
    ["/BreadBar" :refer [BarSection PopoverSection BreadBar]]
    ["/SettingsBox" :refer [SettingsBox]]
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
  (BarSection (:site/name ed)))

(defmethod bar-section :settings [ed {:keys [label]}]
  (let [button-props (clj->js {:label (or label (t :settings))
                               :onClick #(prn 'SETTINGS)})]
    (PopoverSection (clj->js {:buttonProps button-props
                              :content (SettingsBox)}))))

(defmethod bar-section :media-library [ed {:keys [label]}]
  (let [button-props (clj->js {:label (or label (t :media-library))
                               :onClick #(prn 'MEDIA)})]
    (PopoverSection (clj->js {:buttonProps button-props
                              :content nil}))))

(defmethod bar-section :save-button [ed {:keys [label]}]
  (BarSection (Button (clj->js {:label (or label (t :save))
                                :onClick #(prn 'SAVE!)}))))

(defmethod core/field-lifecycle :bar
  [ed field]
  {:render
   (fn [_]
     (let [ed-state @ed]
       (BreadBar (clj->js {:children (map (partial bar-section ed-state)
                                     (:bar/sections ed-state))}))))})
