(ns build
  (:require
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]))

(def PLUGIN-DIRS
  ["plugins/auth"
   "plugins/bidi" ;; TODO fix reitit routes
   "plugins/datahike"
   "plugins/markdown"
   "plugins/reitit"
   "plugins/rum"])

(def minor-version "0.6")

(def lib 'systems.bread/bread-core)
(def patch-version (format "%s.%s" minor-version (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) patch-version))
(def uber-file (format "target/bread-%s-standalone.jar" patch-version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn tag [_]
  (print (str "v" patch-version)))

;; TODO parameterize to build CMS & plugins.
;; This only builds core for now.
(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version patch-version
                :basis (b/create-basis {:project "deps.edn"})
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn uber [_]
  (println "Cleaning target directory...")
  (clean nil)
  (println "Copying resources...")
  (b/copy-dir {:src-dirs ["resources"]
               :target-dir class-dir})
  (let [basis (b/create-basis {:project "deps.edn"
                               :aliases [:cms]})]
    (println "Compiling namespaces...")
    (b/compile-clj {:basis basis
                    :src-dirs (concat ["src" "cms"] PLUGIN-DIRS)
                    :class-dir class-dir
                    :ns-compile '[systems.bread.alpha.cms.main]})
    (println "Writing uberjar...")
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main 'systems.bread.alpha.cms.main}))
  (println "Uberjar written to" uber-file))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
