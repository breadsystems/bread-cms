(ns systems.bread.alpha.dispatcher
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

(defn empty-query []
  [{:find []
    :in ['$]
    :where []}])

(defn query-key [dispatcher]
  "Get from the component layer the key at which to store the dispatchd query
  within the ::bread/queries map"
  (component/query-key (:dispatcher/component dispatcher)))

(defn pull
  "Get the (pull ...) form for the given dispatcher."
  [dispatcher]
  (let [schema (component/query (:dispatcher/component dispatcher))]
    (list 'pull '?e schema)))

(defn- apply-where
  ([query sym k v]
   (-> query
       (update-in [0 :where] conj ['?e k sym])
       (update-in [0 :in] conj sym)
       (conj v)))
  ([query sym k input-sym v]
   (-> query
       (update-in [0 :where] conj [sym k input-sym])
       (update-in [0 :in] conj sym)
       (conj v))))

(defn pull-query
  "Get a basic query with a (pull ...) form in the :find clause"
  [{:dispatcher/keys [pull]}]
  (let [pulling-eid? (some #{:db/id} pull)
        pull-expr (if pulling-eid? pull (cons :db/id pull))]
    (update-in
      (empty-query)
      [0 :find]
      conj
      (list 'pull '?e pull-expr))))

;; TODO provide a slightly higher-level query helper API with simple maps
(defn where [query constraints]
  (reduce
    (fn [query params]
      (apply apply-where query params))
    query
    constraints))

(defmulti dispatch
  (fn [req]
    (get-in req [::bread/dispatcher :dispatcher/type])))

(defn- merge-with-concat [& maps]
  (apply merge-with concat maps))

(defmethod bread/action ::dispatch
  [{::bread/keys [dispatcher queries] :as req} _ _]
  (if (fn? dispatcher)
    ;; We have a vanilla fn handler:
    ;; Short-circuit the rest of the lifecycle.
    (dispatcher req)
    (let [{:keys [queries data effects hooks]} (dispatch req)
          hooks (filter (comp seq val) hooks)]
      (-> req
          (update ::bread/data merge data)
          (update ::bread/queries concat queries)
          (update ::bread/effects concat effects)
          (update ::bread/hooks merge-with-concat hooks)))))

(defn plugin []
  {:hooks
   {::bread/dispatch
    [{:action/name ::dispatch
      :action/description
      "Translate high-level dispatcher into queries, data, and effects"}]}})
