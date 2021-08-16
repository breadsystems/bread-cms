(ns systems.bread.alpha.field
  (:require
    [clojure.edn :as edn]
    [systems.bread.alpha.core :as bread]))

(defn compact [fields]
  (if (map? fields)
    fields
    (into {} (map (fn [row]
                    (let [{k :field/key content :field/content} (first row)]
                      (when k [k (edn/read-string content)])))
                  fields))))
