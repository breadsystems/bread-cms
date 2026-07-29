(ns build
  (:require
    [clojure.java.shell :as shell]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]))

(def PLUGIN-DIRS
  ["plugins/auth"
   "plugins/datahike"
   "plugins/markdown"
   "plugins/reitit"
   "plugins/rum"])

(def minor-version "0.11")

(def core-lib 'systems.bread/bread-core)
(def patch-version (format "%s.%s" minor-version (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/bread-%s-standalone.jar" patch-version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn tag [_]
  (doto (str "v" patch-version) println))

(defn tag-release! [_]
  (b/git-process {:git-args (str "tag v" patch-version)}))

(def libs
  {:core
   {:lib 'systems.bread/bread-core
    :src-dirs ["src"]}

   :auth
   {:lib 'systems.bread/bread-plugin-auth
    :aliases [:auth]
    :src-dirs ["plugins/auth"]}

   :datahike
   {:lib 'systems.bread/bread-plugin-datahike
    :aliases [:datahike]
    :src-dirs ["plugins/datahike"]}

   :email
   {:lib 'systems.bread/bread-plugin-email
    :aliases [:email]
    :src-dirs ["plugins/email"]}

   :garden
   {:lib 'systems.bread/bread-plugin-garden
    :aliases [:garden]}

   :markdown
   {:lib 'systems.bread/bread-plugin-markdown
    :aliases [:markdown]
    :src-dirs ["plugins/markdown"]}

   :reitit
   {:lib 'systems.bread/bread-plugin-reitit
    :aliases [:reitit]
    :src-dirs ["plugins/reitit"]}

   :rum
   {:lib 'systems.bread/bread-plugin-rum
    :aliases [:rum]
    :src-dirs ["plugins/rum"]}

   :selmer
   {:lib 'systems.bread/bread-plugin-selmer
    :aliases [:selmer]
    :src-dirs ["plugins/selmer"]}

   ;; Base theme API.
   :theme
   {:lib 'systems.bread/bread-plugin-theme
    :aliases [:theme]
    :src-dirs ["plugins/theme"]}

   ;; RISE theme.
   :theme-rise
   {:lib 'systems.bread/bread-theme-rise
    :aliases [:theme-rise]
    :src-dirs ["cms/themes/rise/src" "cms/themes/rise/resources"]}

   ;; CRUST theme.
   :theme-crust
   {:lib 'systems.bread/bread-theme-crust
    :aliases [:theme-rise]
    :src-dirs ["cms/themes/crust/src" "cms/themes/crust/resources"]}

   ;;
   })

(defn- jar-path [lib version]
  (format "target/%s-%s.jar" (name lib) version))

(defn jar [opts]
  (let [{:keys [aliases lib src-dirs]} (get libs (:lib opts :core))
        jar-file (jar-path lib patch-version)]
    ;; Prevent pollution from previous builds.
    (clean nil)
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version patch-version
                  :basis (b/create-basis {:project "deps.edn"
                                          :aliases aliases})
                  :src-dirs src-dirs})
    (b/copy-dir {:src-dirs src-dirs
                 :target-dir class-dir})
    (println "Writing jar:" jar-file)
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(defn deploy [opts]
  (let [{:keys [lib]} (get libs (:lib opts :core))
        jar-file (jar-path lib patch-version)]
    (println "Deploying jar:" jar-file)
    (dd/deploy {:installer :remote
                :artifact jar-file
                :pom-file (b/pom-path {:lib lib :class-dir class-dir})})))

(defn- interpret-libs [lib]
  (cond
    (= :all lib) [:core :auth :datahike :email :reitit :rum
                  :theme-rise :theme-crust]
    (= :plugins lib) [:auth :datahike :email :reitit :rum]
    (= :themes lib) [:theme-rise :theme-crust]
    :else [lib]))

(defn release [opts]
  (doseq [lib (interpret-libs (:lib opts :all))]
    (let [opts (assoc opts :lib lib)]
      (jar opts)
      (deploy opts))))

(defn cljdoc-analyze [opts]
  (doseq [k (interpret-libs (:lib opts :all))]
    (let [{lib-name :lib} (get libs k)
          cmd ["clojure" "-Tcljdoc" "analyze"
               ":project" (pr-str (str lib-name))
               ":version" (pr-str patch-version)
               ":jarpath" (pr-str (jar-path lib-name patch-version))
               ":pompath" (pr-str (b/pom-path {:lib lib-name :class-dir class-dir}))]]
      (jar (assoc opts :lib k))
      (println cmd)
      (let [{:keys [exit out]} (apply shell/sh cmd)]
        (print out)
        (flush)
        (System/exit exit)))))

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
                    :src-dirs (concat ["src" "cms" "resources"] PLUGIN-DIRS)
                    :class-dir class-dir
                    :ns-compile '[systems.bread.alpha.cms.main]})
    (println "Writing uberjar...")
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main 'systems.bread.alpha.cms.main}))
  (println "Uberjar written to" uber-file))
