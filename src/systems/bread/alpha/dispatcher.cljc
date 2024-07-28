(ns systems.bread.alpha.dispatcher
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]))

(defn query-key [dispatcher]
  "Get from the component layer the key at which to db the dispatched query
  within the ::bread/expansions map"
  (component/query-key (:dispatcher/component dispatcher)))

(defn pull
  "Get the (pull ...) form for the given dispatcher."
  [dispatcher]
  (let [schema (component/query (:dispatcher/component dispatcher))]
    (list 'pull '?e schema)))

(defn pull-spec
  "Gets the pull spec from the given dispatcher. Adds :db/id to the list
  of fields to return if it is not already included."
  [{:dispatcher/keys [pull]}]
  (vec (if (some #{:db/id} pull) pull (cons :db/id pull))))

;; TODO mv to core
(defmulti dispatch
  (fn [req]
    (get-in req [::bread/dispatcher :dispatcher/type])))

(defn- merge-with-concat [& maps]
  (apply merge-with concat maps))

(defmethod bread/action ::dispatch
  [{::bread/keys [dispatcher expansions] :as req} _ _]
  (if (fn? dispatcher)
    ;; We have a vanilla fn handler:
    ;; Short-circuit the rest of the lifecycle.
    (dispatcher req)
    (let [{:keys [expansions data effects hooks]} (dispatch req)
          ;; TODO short-circuit here if we got a response...?
          hooks (filter (comp seq val) hooks)]
      (-> req
          (update ::bread/data merge data)
          (update ::bread/expansions concat expansions)
          (update ::bread/effects concat effects)
          (update ::bread/hooks merge-with-concat hooks)))))

(defn plugin []
  {:hooks
   {::bread/dispatch
    [{:action/name ::dispatch
      :action/description
      "Translate high-level dispatcher into expansions, data, and effects"}]}})
