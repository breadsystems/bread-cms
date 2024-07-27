(ns systems.bread.alpha.marx.core
  (:require
    [clojure.edn :as edn]))

(defn read-attr [elem attr]
  (edn/read-string (.getAttribute elem attr)))

(defmulti field-lifecycle (fn [_ed field-config] (:type field-config)))

(defmulti tool-props (fn [_ed tool] (:type tool)))

(defmethod tool-props :default [_ed tool]
  {})

(defonce render-count (atom {}))

(defn- persist-field-state! [ed field state]
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

(defn ->js [m]
  (clj->js (reduce (fn [m [k v]]
                     (if (namespace k)
                       (assoc-in m [(namespace k) (name k)] v)
                       (assoc m k v)))
                   {} m)))

(defn <-js [obj]
  (reduce (fn [outer [nsk nested]]
            (reduce (fn [m [nk v]]
                      (assoc m (keyword nsk nk) v))
                    outer nested))
          {} (js->clj obj)))

(comment
  (->js {:a :b
         :bar/position "absolute"
         :theme/variant "light"
         :hello/there {:some :data}})
  (<-js #js {:hi #js {:there "data"
                      :more "dataaaa"}
             :stuff #js {:things "thingy"}}))

(defn init-field* [ed field]
  (swap! render-count update (:name field) inc)
  (let [{:keys [init-state
                did-mount
                render]
         :or {state {}
              init-state (constantly {})}}
        (field-lifecycle ed field)]
    ;(prn (get @render-count (:name field)) (:name field) (:initialized? field) (:state field))
    (assert (fn? render)
            (str "field-lifecycle method for " (:type field)
                 " returned something other than a function!"))
    (if (:initialized? field)
      (let [{{component :marx/component :as state} :state} field
            parent (.-parentNode component)]
        (js/console.log "parent" parent)
        (js/customElements.upgrade parent)
        (.render component (->js state)))
      #_
      (let [{{root :marx/react-root :as state} :state} field
            react-element (render state)]
        (.render root react-element))
      (do
        (assert (fn? init-state))
        (js/console.log (:marx/react-root field) (name (:name field)) (:elem field))
        (let [component (doto (js/document.createElement "bread-bar")
                          (.setAttribute "color" "red"))
              initial (assoc (init-state)
                             :marx/component component)]
          (prn 'field field)
          (prn 'component component)
          (persist-field-state! ed field initial)
          (.appendChild (:elem field) component)
          (when (fn? did-mount)
            (did-mount initial)))
        #_
        (let [root (rdom/createRoot (:elem field))
              initial (assoc (init-state)
                             :marx/react-root root)]
          (persist-field-state! ed field initial)
          (.render root (render initial))
          (when (fn? did-mount)
            (did-mount initial)))))))
