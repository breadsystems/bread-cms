(ns systems.bread.alpha.internal.html-cache
  (:require
    [clojure.string :as string]
    #?(:cljs ["fs" :as fs]))
  #?(:clj (:import
            [java.io File])))

(defn- mkdir [path]
  #?(:clj
     (.mkdirs (File. path))
     :cljs
     ;; TODO test this
     (fs/mkdir path {:recursive true})))

(defonce ^:private sep
  #?(:clj
     File/separator))

(defonce ^:private leading-slash
  #?(:clj
     (re-pattern (str "^" sep))))

(defonce ^:private trailing-slash
  #?(:clj
     (re-pattern (str sep "$"))))

(defn- trim-slashes [s]
  (-> s
      (string/replace leading-slash "")
      (string/replace trailing-slash "")))

(defn render-static! [path file contents]
  (let [path (trim-slashes path)]
    (mkdir path)
    (spit (str path sep file) contents)))
