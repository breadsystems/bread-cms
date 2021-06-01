(ns systems.bread.alpha.static-frontend
  (:require
    [clojure.string :as string]
    [systems.bread.alpha.core :as bread]
    #?(:cljs
       ["fs" :as fs]))
  #?(:clj
     (:import
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

(defn plugin
  "Returns a plugin that renders a static file with the fully rendered
  HTML body of each response."
  ([]
   (plugin {}))
  ([{:keys [root index-file]}]
   (let [index-file (or index-file "index.html")
         root (or root "resources/public")]
     (fn [app]
       (bread/add-hook
         app :hook/response
         (fn [{:keys [body uri] :as res}]
           (render-static! (str root uri) index-file body)
           res))))))

(comment

  (string/replace "/1/2" (re-pattern (str "^" File/separator)) "")
  (string/replace "/leading/slash" #"^/" "")

  (render-static! "/one/two/three//" "index.html" "<h1>Hello, World!</h1>")

  (slurp "one/two/three/index.html")

  )
