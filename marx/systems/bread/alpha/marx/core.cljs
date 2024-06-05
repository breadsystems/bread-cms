(ns systems.bread.alpha.marx.core
  (:require
    ["react-dom/client" :as rdom]
    [clojure.edn :as edn]))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(defmulti init-field! (fn [_ed field] (:type field)))
(defmulti field-lifecycle (fn [_ed field-config] (:type field-config)))

(defonce render-count (atom {}))

(defn- persist-field-state! [ed field state]
  (prn 'PERSIST (:name field) field state)
  (swap! ed assoc-in [:marx/fields (:name field)]
         (assoc field
                :initialized? true
                :state state)))

(defn fields-from-editor [ed]
  (vals (:marx/fields @ed)))

(defn init-field* [ed field]
  (swap! render-count update (:name field) inc)
  (let [{:keys [init-state
                did-mount
                render]
         :or {state {}
              init-state (constantly {})}}
        (field-lifecycle ed field)]
    (prn (get @render-count (:name field)) (:name field) (:initialized? field) (:state field))
    (assert (fn? render))
    (if (:initialized? field)
      (let [{{root :marx/react-root :as state} :state} field
            react-element (render state)]
        (.render root react-element))
      (do
        (assert (fn? init-state))
        (prn ())
        (let [root (rdom/createRoot (:elem field))
              initial (assoc (init-state)
                             :marx/react-root root)]
          (persist-field-state! ed field initial)
          (.render root (render initial))
          (when (fn? did-mount)
            (did-mount initial)))))
    #_#_#_
    (assert (fn? render))
    (let [react-element (render (:state field))]
      (.render))
    (render (:state field)))
  #_
  (when-not (:initialized? field)
    (init-field! ed field)))
