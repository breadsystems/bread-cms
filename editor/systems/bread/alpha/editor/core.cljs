(ns systems.bread.alpha.editor.core
  (:require
    [clojure.edn :as edn]))

(defonce EDITOR (atom {:fields {}
                       :listeners {}}))

(defn declare-field! [elem config]
  (swap! EDITOR assoc-in [:fields (:name config)]
         (assoc config :element elem)))

(defn get-field [field-name]
  (get-in @EDITOR [:fields field-name]))

(defn listen! [elem event-name f]
  (let [listener-path [:listeners elem event-name]]
    (when-let [prev (get-in @EDITOR listener-path)]
      (.removeEventListener elem event-name prev))
    (swap! EDITOR assoc-in [:listeners elem event-name] f))
  (.addEventListener elem event-name f))

(defmulti event! (fn [_ _ config] (:event config)))

(defn handler [elem event-config]
  (fn [e]
    (event! e elem event-config)))

(defmulti init-field! (fn [_ config] (:type config)))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(comment
  (sequential? {})
  (sequential? ())
  (sequential? [])
  (edn/read-string "{:hello :there}"))
