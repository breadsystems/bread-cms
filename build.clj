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

(def minor-version "0.7")

(def core-lib 'systems.bread/bread-core)
(def patch-version (format "%s.%s" minor-version (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/bread-%s-standalone.jar" patch-version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn tag [_]
  (print (str "v" patch-version)))

(def libs
  {:core
   {:lib 'systems.bread/bread-core}

   :datahike
   {:lib 'systems.bread/bread-plugin-datahike
    :aliases [:datahike]}

   :garden
   {:lib 'systems.bread/bread-plugin-garden
    :aliases [:garden]}

   :markdown
   {:lib 'systems.bread/bread-plugin-markdown
    :aliases [:markdown]}

   :reitit
   {:lib 'systems.bread/bread-plugin-reitit
    :aliases [:reitit]}

   :rum
   {:lib 'systems.bread/bread-plugin-rum
    :aliases [:rum]}

   :selmer
   {:lib 'systems.bread/bread-plugin-selmer
    :aliases [:selmer]}

   ;;
   })

(defn- jar-path [lib version]
  (format "target/%s-%s.jar" (name lib) version))

;; TODO parameterize to build CMS & plugins.
;; This only builds core for now.
(defn jar [opts]
  (let [{:keys [aliases lib]} (get libs (:lib opts :core))
        jar-file (jar-path lib patch-version)]
    (clean nil)
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version patch-version
                  :basis (b/create-basis {:project "deps.edn"
                                          :aliases aliases})
                  :src-dirs ["src"]
                  :pom-data
                  [:licenses
                   [:license
                    [:name "Anti-Capitalist Software License v1.4"]
                    [:url "https://anticapitalist.software/"]]]})
    (b/copy-dir {:src-dirs ["src"]
                 :target-dir class-dir})
    (println "Writing jar:" jar-file)
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

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

(defn deploy [opts]
  (let [{:keys [lib]} (get libs (:lib opts :core))
        jar-file (jar-path lib patch-version)]
    (println "Deploying jar:" jar-file)
    (dd/deploy {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})})))
