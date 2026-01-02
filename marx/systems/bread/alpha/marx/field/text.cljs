(ns systems.bread.alpha.marx.field.text
  (:require
    [systems.bread.alpha.marx.core :as marx]))

(defmethod marx/content :text [field]
  (.-innerText (:elem field)))

(defmethod marx/field-lifecycle :text
  [ed {:as field :keys [state elem]}]
  {:render
   (fn [_state]
     (.setAttribute elem "contenteditable" true))})
