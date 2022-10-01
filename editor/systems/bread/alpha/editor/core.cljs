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

(defn- handler [elem event-config]
  (fn [e]
    (event! e elem event-config)))

(defmulti init-field! (fn [_ config] (:type config)))

(defmethod init-field! :default [_ config]
  (when-not (:synthetic? config)
    (js/console.error (str "No event! multimethod defined for: "
                           (prn-str (:type config))))))

(defmethod init-field! :repeater
  [element {:keys [each] :as config}]
  (when each
    (let [{:keys [on-click]} each
          ;; TODO more event handlers
          ]
      (doseq [child (.querySelectorAll element (:selector each))]
        (listen! child "click" (handler child on-click))))))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(defmethod event! :replace-attrs
  [_ elem {:keys [target target-field with]}]
  (when-let [target-elem (or (js/document.querySelector target)
                             (:element (get-field target-field)))]
    (doseq [[k v] (read-attr elem (name with))]
      (.setAttribute target-elem (name k) v))))

(comment
  (sequential? {})
  (sequential? ())
  (sequential? [])
  (edn/read-string "{:hello :there}"))
