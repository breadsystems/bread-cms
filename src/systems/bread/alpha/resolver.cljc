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

(defmulti resolve-query :resolver/type)

(defmethod resolve-query :resolver.type/post [resolver]
  (let [query (empty-query)
        pull-schema (comp/get-query (:resolver/component resolver))
        pull (list 'pull '?e pull-schema)]
    {:post (update-in query [:query :find] conj pull)}))

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
  (as-> req $
    (resolver $)
    (resolve-query $)
    (replace-query-args req $)
    (assoc req ::bread/queries $)))
