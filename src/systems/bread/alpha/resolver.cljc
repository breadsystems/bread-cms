(ns systems.bread.alpha.resolver
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [systems.bread.alpha.component :as comp :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))

(defn empty-query []
  [{:find []
    :in ['$]
    :where []}])

(defn query-key [resolver]
  "Get from the component layer the key at which to store the resolved query
  within the ::bread/queries map"
  (comp/get-key (:resolver/component resolver)))

(defn pull
  "Get the (pull ...) form for the given resolver."
  [resolver]
  (let [schema (comp/get-query (:resolver/component resolver))]
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
  [{:resolver/keys [pull]}]
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

(defmulti resolve-query (fn [req]
                          (get-in req [::bread/resolver :resolver/type])))

(defn resolve-queries [req]
  {:pre [(s/valid? ::bread/app req)
         (s/valid? ::bread/resolver (::bread/resolver req))]
   :post [(s/valid? ::bread/queries (::bread/queries %))]}
  (update req ::bread/queries concat (resolve-query req)))

(defn plugin []
  (fn [app]
    (bread/add-hook app :hook/resolve resolve-queries)))
