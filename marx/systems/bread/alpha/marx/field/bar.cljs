(ns systems.bread.alpha.marx.field.bar
  (:require
    ["react" :as react]

    ["/Button" :refer [Button]]
    ["/Spacer" :refer [Spacer]]
    ["/BreadBar" :refer [HeadingSection BarSection PopoverSection]]
    ["/BreadContainer" :refer [BreadContainer]]
    ["/SettingsBox" :refer [SettingsBox]]
    [systems.bread.alpha.marx.core :as core]))

;; TODO
(def t {:media "Media"
        :publish "Publish"
        :settings "Settings"})

(defmulti bar-section (fn [_ section]
                        (prn 'section section)
                        (cond
                          (keyword? section) section
                          :default (:type section))))

(defmethod bar-section :spacer [_ _]
  (Spacer))

(defmethod bar-section :site-name [ed _]
  (HeadingSection #js {:children (:site/name ed)}))

(defmethod bar-section :settings [ed {:keys [label]}]
  (let [button-props #js {:children (or label (t :settings))}]
    (PopoverSection #js {:buttonProps button-props
                         :content (SettingsBox)})))

(defmethod bar-section :media [ed {:keys [label]}]
  (let [button-props #js {:children (or label (t :media))}]
    (PopoverSection #js {:buttonProps button-props
                         :content (SettingsBox)})))

(defmethod bar-section :publish-button [ed {:keys [label]}]
  (BarSection #js {:children
                   (Button #js {:children (or label (t :publish))
                                :onClick #(prn 'SAVE!)})}))

(defmethod core/field-lifecycle :bar
  [ed field]
  {:render
   (fn [_]
     (let [ed-state @ed]
       (BreadContainer #js {:children (map (partial bar-section ed-state)
                                           (:bar/sections ed-state))})))})
