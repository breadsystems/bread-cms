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
   :args [::bread/store]})

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

(defmulti resolve-query :resolver/type)

(defmethod resolve-query :resolver.type/page [resolver]
  (let [query (pull-query resolver)])
    {:post (pull-query resolver)})

(defmulti replace-arg (fn [_ arg]
                        arg))

(defmethod replace-arg ::bread/store [req _]
  (store/datastore req))

(defn- replace-args [req args]
  (map (partial replace-arg req) args))

(defn- replace-query-args [req queries]
  (into {} (map (fn [[k query]]
                  [k (update query :args #(replace-args req %))])
                queries)))

(defn resolver [req]
  (bread/hook-> req :hook/resolver (::bread/resolver req)))

(defn resolve-queries [req]
  (->> req
       resolver
       resolve-query
       (replace-query-args req)
       (assoc req ::bread/queries)))
