(ns systems.bread.alpha.marx.field.bar
  (:require
    ["react" :as react]

    ["/Button" :refer [Button]]
    ["/Spacer" :refer [Spacer]]
    ["/BreadBar" :refer [HeadingSection BarSection PopoverSection]]
    ["/Popover" :refer [Popover]]
    ["/BreadContainer" :refer [BreadContainer]]
    ["/SettingsBox" :refer [SettingsBox]]
    ["/MediaLibrary" :refer [MediaLibrary]]
    [systems.bread.alpha.marx.core :as core :refer [->js]]))

;; TODO
(def t {:media "Media"
        :publish "Publish"
        :settings "Settings"})

(defmulti bar-section (fn [_ section]
                        (cond
                          (keyword? section) section
                          :default (:type section))))

(defmethod bar-section :spacer [_ _]
  (Spacer))

(defmethod bar-section :site-name [{ed :editor-state} _]
  (HeadingSection #js {:children (:site/name ed)}))

(defmethod bar-section :settings
  [{{:site/keys [settings]} :editor-state} {:keys [label]}]
  (Popover #js {:buttonProps #js {:children (or label (t :settings))}
                :content (SettingsBox
                           #js {:settings (->js settings)})}))

(defmethod bar-section :media
  [{{:site/keys [settings]} :editor-state} {:keys [label]}]
  (Popover #js {:buttonProps #js {:children (or label (t :media))}
                :content (MediaLibrary
                           #js {:settings (->js settings)})}))

(defmethod bar-section :publish-button
  [state {:keys [label]}]
  (BarSection #js {:children
                   (Button #js {:children (or label (t :publish))
                                :onClick #(core/persist-edit!
                                            ;; TODO :revise-fields
                                            {:edit/action :publish-fields}
                                            state)})}))

(defmethod core/field-lifecycle :bar
  [ed {:keys [sections] :marx/keys [document]}]
  {:render
   (fn [_]
     (let [ed-state @ed
           settings (:site/settings ed-state)
           sections (or sections (:bar/sections ed-state))
           child-props {:settings (->js settings)
                        :editor-state ed-state
                        :document document}]
       (BreadContainer #js {:children (map (partial bar-section child-props)
                                           sections)
                            :settings (->js settings)
                            :themeVariants (:theme/variants ed-state)})))})
