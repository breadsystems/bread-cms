;; Utilities for reading content from the filesystem
;; rather than from the datastore.
(ns systems.bread.alpha.plugin.static-backend
  (:require
    [reitit.core :as reitit] ;; TODO
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

(defn request-creator [{:keys [dir ext path->req]}]
  (if (fn? path->req)
    path->req
    (fn [path]
      (let [sub-path (subs path (count dir) (- (count path) (count ext)))]
        {:uri sub-path}))))

(defn- watch-handler [f config]
  (with-meta
    (fn [{:keys [action file]}]
      (when (= :modify action)
        (when-let [req ((request-creator config) (.getCanonicalPath file))]
          (f req))))
    {:handler f
     :config config}))

(defn- path->uri [path]
  (let [[lang slug] (filter (complement empty?) (string/split path #"/"))]
    (str "/" lang "/static/" slug)))

(defn- *bread-routes [rtr] (reitit/compiled-routes rtr))
(defn- *bread-route-watch-confg [[_ {watch-config :bread/watch-static}]]
  (when watch-config
    (merge {:ext (str (:ext watch-config ".md"))
            :path->req (fn [p]
                         (let [file (io/file p)
                               dir (.getCanonicalPath (io/file (:dir watch-config)))
                               ;; TODO move this default to watch-config fn
                               ext (or (:ext watch-config) ".md")
                               creator (request-creator {:dir dir :ext ext})
                               {md-path :uri} (creator (.getCanonicalPath file))]
                           {:uri (path->uri md-path)}))}
           watch-config)))

(defn- watch-route [handler route]
  (when-let [config (*bread-route-watch-confg route)]
    (watch/watch-dir
      (watch-handler handler config)
      (io/file (:dir config)))))

(defn watch-routes [handler router]
  (let [watchers (doall
                   (filter some? (map (fn [route]
                                        (watch-route handler route))
                                      (*bread-routes router))))]
    (fn []
      (doall (for [watcher watchers]
               (watch/close-watcher watcher))))))

(comment

  (defn $handler [req]
    (prn 'MOCK req))
  (def $router breadbox.app/$router)

  (defonce stop (atom nil))

  (do
    (when (fn? @stop)
      (@stop))
    (reset! stop (watch* $handler $router)))

  (.getCanonicalPath (io/file "dev/content"))
  (.getCanonicalPath (io/file "dev/content/en/one.md"))

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
