(ns systems.bread.alpha.resolver
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.component :as comp :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.datastore :as store]))


(defn empty-query []
  {:query {:find [] :in ['$] :where []}
   :args [::bread/store]
   ::bread/expand []})

(defn pull
  "Get the (pull ...) form for the given resolver."
  [resolver]
  (let [schema (comp/get-query (:resolver/component resolver))]
    (list 'pull '?e schema)))

(defn pull-query
  "Get a basic query with a (pull ...) form in the :find clause"
  [resolver]
  (update-in (empty-query)
             [:query :find]
             conj
             (pull resolver)))

(defn- apply-where
  ([query sym k v]
   (-> query
       (update-in [:query :where] conj ['?e k sym])
       (update-in [:query :in] conj sym)
       (update-in [:args] conj v)))
  ([query sym k input-sym v]
   (-> query
       (update-in [:query :where] conj [sym k input-sym])
       (update-in [:query :in] conj sym)
       (update-in [:args] conj v))))

;; TODO provide a slightly higher-level query helper API with simple maps
(defn where [query constraints]
  (reduce
    (fn [query params]
      (apply apply-where query params))
    query
    constraints))

(defmulti resolve-query :resolver/type)

;; TODO refactor how queries are invoked so we don't need this
(defmulti replace-arg (fn [_ arg]
                        arg))

(defmethod replace-arg :default [_ x] x)

(defmethod replace-arg ::bread/store [req _]
  (store/datastore req))

(defn- replace-args [req args]
  (map (partial replace-arg req) args))

(defn- replace-query-args [req queries]
  (into [] (map (fn [[k query f]]
                  (filter some? [k (update query :args #(replace-args req %)) f]))
                queries)))

(defn resolve-queries [req]
  (->> req
       ::bread/resolver
       resolve-query
       (assoc req ::bread/queries)))
