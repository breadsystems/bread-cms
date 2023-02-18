(ns build
  (:require
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]))

(def minor-version "0.6")

(def lib 'systems.bread/bread-core)
(def patch-version (format "%s.%s" minor-version (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) patch-version))

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
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact jar-file
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))
