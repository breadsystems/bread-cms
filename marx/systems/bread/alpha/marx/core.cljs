(ns systems.bread.alpha.marx.core
  (:require
    ["react-dom/client" :as rdom]
    [clojure.edn :as edn]))

(defn read-attr [elem attr]
  (when elem (edn/read-string (.getAttribute elem attr))))

(defmulti field-lifecycle (fn [_ed field-config] (:marx/field-type field-config)))

(defmulti tool-props (fn [_ed tool] (:marx/field-type tool)))

(defmethod tool-props :default [_ _] {})

(defn- persist-field-state! [ed field state]
  (swap! ed assoc-in [:marx/fields (:field/key field)]
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

(defmulti content :marx/field-type)

(defmulti edit (fn [e _ed]
                  (:edit/action e)))

(defmethod edit :publish-fields [_ {:marx/keys [document fields]}]
  (let [field-data (->> fields
                        vals
                        (filter (complement (comp false? :persist?)))
                        (map (fn [field]
                               (-> field
                                   (assoc :field/content (content field))
                                   (select-keys [:db/id
                                                 :field/key
                                                 :field/format
                                                 :field/content])))))]
    {:edit/action :publish-fields
     :edit/key :edit/instant
     :fields field-data
     :marx/document document}))

(defn save! [e {:keys [marx/backends] :as ed-state}]
  (let [data (edit e ed-state)]
    (doseq [be backends]
      (persist! be data))))

(defn attach-backend! [ed backend-inst]
  (swap! ed update :marx/backends conj backend-inst))

(defn- assert-valid-lifecycle-method [t f mn]
  (assert (fn? f) (str "field-lifecycle method for " t
                       " returned a `" (name mn)
                       "` value of a type other than function!")))

(defn init-field [ed field]
  (let [{:keys [init-state
                did-mount
                render]
         :or {init-state (constantly {})}}
        (field-lifecycle ed field)]
    (assert-valid-lifecycle-method (:marx/field-type field) render :render)
    (if (:initialized? field)
      (render (:state field))
      (do
        (assert-valid-lifecycle-method (:marx/field-type field) init-state :init-state)
        (let [initial (init-state)]
          (render initial)
          (persist-field-state! ed field initial)
          (when (fn? did-mount)
            (did-mount initial)))))))
