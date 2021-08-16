;; Utilities for reading content from the filesystem
;; rather than from the datastore.
(ns systems.bread.alpha.plugin.static-backend
  (:require
    [clojure.instant :as instant]
    [clojure.set :refer [rename-keys]]
    [clojure.string :as string]
    [clojure.java.io :as io]
    [juxt.dirwatch :as watch]
    [markdown.core :as md]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.resolver :as resolver]))

(comment
  (instant/read-instant-date "2020-10-25T20:59:36+00:00")
  (inst-ms (java.util.Date.))

  (slurp "dev/pages/one.md")
  (slurp (io/resource "pages/one.md"))

  (md/md-to-html-string (slurp (io/resource "pages/one.md")))

  (clojure.string/join java.io.File/separator (map {:lang "en" :slug "one"} [:lang :slug]))

  ;;
  )

(defn query-fs
  ([data params]
   (query-fs data params {}))
  ([data params opts]
   (let [{:keys [root ext lang-param slug-param parse parse-meta?]
          :or {root "content" ext ".md" lang-param :lang slug-param :slug}}
         opts
         sep java.io.File/separator
         path (string/join sep (map params [lang-param slug-param]))
         path (str root sep path ext)
         parse (cond
                 (ifn? parse) parse
                 (false? parse-meta?) md/md-to-html-string
                 :else md/md-to-html-string-with-meta)
         parsed (some-> path io/resource slurp parse)]
     (if (false? parse-meta?)
       {:html parsed}
       (let [{:keys [html metadata]} parsed]
         (when html
           (assoc metadata :html html)))))))

(defn- rename-ns-keys [key-ns m names]
  (let [keymap (into {} (map (fn [k]
                               [(keyword (str (name key-ns) "/" (name k))) k])
                             names))]
    (rename-keys (select-keys m (keys keymap)) keymap)))

(comment
  (rename-ns-keys :my {:my/a "A" :my/b "B" :my/c "C"} [:a :b]))

(defmethod resolver/resolve-query :resolver.type/static
  [{::bread/keys [resolver config] :as req}]
  (let [params (:route/params resolver)
        opts (rename-ns-keys :static-backend config
                             [:root :ext :lang-param :slug-param])]
    [[:post query-fs params opts]]))

(defn- debounce [f ms]
  (let [timeout (atom nil)]
    (fn [& args]
      (when (future? @timeout)
        (future-cancel @timeout))
      (reset! timeout (future
                        (Thread/sleep ms)
                        (apply f args))))))

(comment
  (def $file (io/file "dev/content/en/one.md"))
  (.getName $file)
  (.getPath $file)
  (string/starts-with? (.getPath $file) "dev/content")
  $dir
  $file
  (handler-request $dir $file))

(defn- handler-request [dir file ext]
  (let [dir-path (.getCanonicalPath dir)
        file-path (.getCanonicalPath file)]
    (when (string/starts-with? file-path dir-path)
      (let [path (subs file-path
                       (count dir-path)
                       (- (count file-path) (count ext)))]
        {:uri path}))))

(defn- watch-handler [f dir ext]
  (fn [{:keys [action file]}]
    (when (= :modify action)
      (when-let [req (handler-request dir file ext)]
        (f req)))))

(defn watch! [dirs f ext]
  (println (format "Watching %s for changes..." (string/join ", " dirs)))
  (let [watchers (doall (for [dir dirs]
                          (let [dir (io/file dir)]
                            (watch/watch-dir
                              (watch-handler f dir ext)
                              dir))))]
    (fn []
      (doall (for [w watchers]
               (watch/close-watcher w))))))

(defn request-creator [{:keys [dir ext path->req]}]
  (if (fn? path->req)
    path->req
    (fn [path _]
      (let [sub-path (subs path (count dir) (- (count path) (count ext)))]
        {:uri sub-path}))))

(defn- watch-handler* [f {:keys [path->req] :as config}]
  (with-meta
    (fn [{:keys [action file]}]
      (prn action file)
      (when (= :modify action)
        (prn 'file (.getCanonicalPath file))
        (when-let [req (path->req (.getCanonicalPath file) config)]
          (f req))))
    {:handler f
     :config config}))

(defn- watch-configs [routes]
  (filter (fn [[_ data]]
            (:bread.static/watch data))
          routes))

(defn- path->uri [path]
  (let [[lang slug] (string/split path #"/")]
    (str "/" lang "/static/" slug)))

(defn- *bread-routes [rtr] (reitit/compiled-routes rtr))
(defn- *bread-route-watch-confg [[_ {watch-config :bread/watch-static}]]
  (merge {:path->req (fn [p config]
                       (prn p 'changed config)
                       {:uri (path->uri p)})} watch-config))

(defn watch* [handler {:keys [router]}]
  (reduce concat [] (map
                      (fn [route]
                        (let [config (*bread-route-watch-confg route)]
                          (map (fn [dir]
                                 (watch-handler* handler
                                                 (assoc config :dir dir)))
                               (:dirs config))))
                      (watch-configs (*bread-routes $router)))))

(comment
  (require '[reitit.core :as reitit])

  (defn $handler [req]
    (prn 'MOCK req))
  (def $router breadbox.app/$router)

  (def $handlers (watch* $handler {:router $router}))
  (map meta (watch* $handler {:router $router}))

  (.getCanonicalPath (io/file "dev/content"))
  (.getCanonicalPath (io/file "dev/content/en/one.md"))

  $handlers
  (first $handlers)
  (def $watcher (watch/watch-dir (first $handlers) (io/file "dev/content")))

  ;;
  )

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [root ext lang-param slug-param]}]
   (fn [app]
     (bread/set-config-cond-> app
       root :static-backend/root
       ext :static-backend/ext
       lang-param :static-backend/lang-param
       slug-param :static-backend/slug-param))))
