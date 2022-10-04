(ns systems.bread.alpha.editor.core
  (:require
    [clojure.edn :as edn]))

(defmulti init-field! (fn [_ _ config] (:type config)))

(defn get-field [ed field-name]
  (get-in @ed [:bread/fields field-name]))

(defn get-target [ed {:keys [selector field]} _elem]
  ;; TODO :within
  (cond
    selector (js/document.querySelector selector)
    field (:element (get-field ed field))))

(defn listen! [ed elem event-name f]
  (let [listener-path [:bread/listeners elem event-name]]
    (when-let [prev (get-in ed listener-path)]
      (.removeEventListener elem event-name prev))
    (swap! ed assoc-in [:bread/listeners elem event-name] f))
  (.addEventListener elem event-name f))

(defmulti event! (fn [_ _ _ config]
                   (:event config)))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(comment
  (sequential? {})
  (sequential? ())
  (sequential? [])
  (edn/read-string "{:hello :there}"))
