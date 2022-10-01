(ns systems.bread.alpha.editor.core
  (:require
    [clojure.edn :as edn]))

(defn get-field [ed field-name]
  (get-in @ed [:fields field-name]))

(defn listen! [ed elem event-name f]
  (let [listener-path [:listeners elem event-name]]
    (when-let [prev (get-in ed listener-path)]
      (.removeEventListener elem event-name prev))
    (swap! ed assoc-in [:listeners elem event-name] f))
  (.addEventListener elem event-name f))

(defmulti event! (fn [_ _ _ config]
                   (:event config)))

(defn handler [ed elem event-config]
  (fn [e]
    (event! ed e elem event-config)))

(defmulti init-field! (fn [_ _ config] (:type config)))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(comment
  (sequential? {})
  (sequential? ())
  (sequential? [])
  (edn/read-string "{:hello :there}"))
