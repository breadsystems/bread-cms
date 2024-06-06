(ns systems.bread.alpha.marx.field.bar
  (:require
    ["react" :as react]

    ["/Bar" :refer [Bar]]
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

(defmethod bar-section :spacer [_ section]
  "spacer")

(defmethod bar-section :site-name [ed _]
  (:site/name ed))

(defmethod bar-section :settings [ed {:keys [label]}]
  (or label (t :settings)))

(defmethod bar-section :media-library [ed {:keys [label]}]
  (or label (t :media-library)))

(defmethod bar-section :save-button [ed {:keys [label]}]
  (or label (t :save)))

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
