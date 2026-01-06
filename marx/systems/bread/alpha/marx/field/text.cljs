(ns systems.bread.alpha.marx.field.text
  (:require
    [systems.bread.alpha.marx.core :as marx]))

(defmethod marx/content :text [field]
  (.-innerText (:elem field)))

(defmethod marx/field-lifecycle :text
  [ed {:as field :keys [state elem]}]
  {:render
   (fn [_state]
     (.setAttribute elem "contenteditable" true)
     ;; Disallow newline characters, since this would set up an expectation
     ;; they will be re-rendered.
     (.addEventListener elem "keypress" (fn [e]
                                          (when (= "Enter" (.-key e))
                                            (.preventDefault e)))))})
