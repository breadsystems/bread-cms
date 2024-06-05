(ns systems.bread.alpha.marx.core
  (:require
    ["react-dom/client" :as rdom]
    [clojure.edn :as edn]))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

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

(defn fields-from-dom [config]
  (let [attr (:attr config "data-marx")
        selector (str "[" attr "]")
        elems (vec (js/document.querySelectorAll selector))]
    (map (fn [elem]
           (assoc (read-attr elem attr)
                  :elem elem))
         elems)))

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
        (let [root (rdom/createRoot (:elem field))
              initial (assoc (init-state)
                             :marx/react-root root)]
          (persist-field-state! ed field initial)
          (.render root (render initial))
          (when (fn? did-mount)
            (did-mount initial)))))))
