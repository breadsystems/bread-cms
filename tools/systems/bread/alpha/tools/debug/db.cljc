(ns systems.bread.alpha.tools.debug.db
  (:require
    [asami.core :as d]))

(defn- pull-clause [attrs]
  (let [;; TODO deal with maps
        symbols (mapv (comp symbol #(str "?" %) name) attrs)
        eid (gensym "?e")
        where (mapv (fn [[attr sym]]
                      ;; [?e... :some/attr ?sym]
                      [eid attr sym])
                    (partition 2 (interleave attrs symbols)))]
    [symbols where]))

(defn- find-pull [shape]
  (let [[find-clause where-clause] (pull-clause shape)]
    {:find find-clause
     :where where-clause}))

(defn- build-entity [query res]
  (into {} (map vec (partition 2 (interleave query res)))))

(comment
  (def $query [:request/uuid :request/uri])
  $res
  (build-entity $query (first $res))
  )

(defn pull [shape conn]
  (let [rows (d/q (find-pull shape) conn)]
    (map (partial build-entity shape) rows)))
