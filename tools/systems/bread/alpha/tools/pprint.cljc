(ns systems.bread.alpha.tools.pprint
  (:require
    [clojure.string :as string :refer [split starts-with?]]
    [systems.bread.alpha.core :as core]
    [systems.bread.alpha.cache]
    [systems.bread.alpha.database :as store])
  (:import
    [clojure.lang Keyword]
    [java.io Writer]))

(def ^{:dynamic true
       :doc "Whether to abbreviate schema migration data when printing.
            Reduces noise when printing app config data. Defaults to true."}
  *summarize-migrations* true)

(defmethod print-method Keyword [^Keyword k ^Writer w]
  (.write w (let [kns (.getNamespace k)]
              (cond
                (= kns "systems.bread.alpha.core")
                (str "::bread/" (name k))
                (and kns (starts-with? kns "systems.bread.alpha"))
                (str "::" (last (split kns #"\.")) "/" (name k))
                :else (str k)))))

(defmethod print-method :bread/migration [migration writer]
  (.write writer (if *summarize-migrations*
                   (let [nm (or (store/migration-key migration)
                                (hash migration))
                         summary {:tx-count (count migration)}]
                     (str "#migration[" summary " " nm "]"))
                   (str (seq migration)))))

(comment
  (alter-var-root (var *summarize-migrations*) not)

  (and
    (= ":systems.bread.alpha.core/x" (str ::core/x))
    (= "[:a/b :xyz :clojure.string/hi :clojure.string/hi ::bread/x]\n"
       (with-out-str
         (prn [:a/b :xyz ::string/hi :clojure.string/hi ::core/x])))))
