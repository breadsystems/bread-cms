(ns systems.bread.alpha.marx.core
  (:require
    ["@tiptap/core" :refer [Editor] :rename {Editor TiptapEditor}] ;; TODO
    ["react-dom/client" :as rdom]
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

(defprotocol MarxBackend
  (persist! [this ed-state]))

(defprotocol StatefulBackend
  (init-backend! [this config])
  (retry! [this config]))

(defmulti backend :type)

;; TODO multimethod
(defn- field-content [field]
  (let [tiptap (:tiptap (:state field))]
    (assoc (select-keys field [:name :type :db/id])
           :html (if tiptap (.getHTML ^TiptapEditor tiptap) ""))))

(defn persist-to-backend! [{:marx/keys [fields backend]}]
  (->> fields
       vals
       (filter (complement (comp false? :persist?)))
       (map field-content)
       (persist! backend)))

(defn attach-backend! [ed backend-inst]
  (swap! ed assoc :marx/backend backend-inst))

(defn init-field [ed field]
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
