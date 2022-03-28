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

(defn render-static! [path file contents]
  (let [path (string/replace path leading-slash "")]
    (mkdir path)
    (spit (str path sep file) contents)))
