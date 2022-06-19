(ns systems.bread.alpha.tools.pprint
  (:require
    [clojure.string :as string :refer [split starts-with?]]
    [systems.bread.alpha.core :as core]
    [systems.bread.alpha.cache]
    )
  (:import
    [clojure.lang Keyword]
    [java.io Writer]))

(defmethod print-method Keyword [^Keyword k ^Writer w]
  (.write w (let [kns (.getNamespace k)]
              (cond
                (= kns "systems.bread.alpha.core")
                (str "::bread/" (name k))
                (and kns (starts-with? kns "systems.bread.alpha"))
                (str "::" (last (split kns #"\.")) "/" (name k))
                :else (str k)))))

(defmethod print-method :bread/schema-migration [migration writer]
  (.write writer (let [nm (or (:bread.migration/name (meta migration))
                              (hash migration))
                       summary {:attr-count (count migration)}]
                   (str "#migration[" summary " " nm "]"))))

(comment
  (and
    (= ":systems.bread.alpha.core/x" (str ::core/x))
    (= "[:a/b :xyz :clojure.string/hi :clojure.string/hi ::bread/x]\n"
       (with-out-str
         (prn [:a/b :xyz ::string/hi :clojure.string/hi ::core/x])))))
