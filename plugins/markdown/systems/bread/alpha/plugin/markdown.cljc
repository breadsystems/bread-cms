;; Utilities for reading content from the filesystem
;; rather than from the database.
(ns systems.bread.alpha.plugin.markdown
  (:require
    [clojure.instant :as instant]
    [clojure.set :refer [rename-keys]]
    [clojure.string :as string]
    [clojure.java.io :as io]
    [juxt.dirwatch :as watch]
    [markdown.core :as md]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.dispatcher :as dispatcher]))

(comment

  (slurp "dev/content/en/one.md")
  (slurp (io/resource "content/en/one.md"))

  (md/md-to-html-string (slurp (io/resource "content/en/one.md")))

  (clojure.string/join java.io.File/separator (map {:lang "en" :slug "one"} [:lang :slug]))

  ;;
  )

(defn query-fs [data params opts]
  (let [{:keys [root ext lang-param slug-param parse]} opts
        sep java.io.File/separator
        path (string/join sep (map params [lang-param slug-param]))
        path (str root sep path ext)
        parsed (some-> path io/resource slurp parse)]
    (if (string? parsed)
      {:html parsed}
      (let [{:keys [html metadata]} parsed]
        (when html
          (assoc metadata :html html))))))

(defmethod bread/dispatch :dispatcher.type/static
  [{::bread/keys [dispatcher config] :as req}]
  (let [params (:route/params dispatcher)
        opts (-> config
                 (rename-keys
                   {:static/root :root
                    :static/ext :ext
                    :static/lang-param :lang-param
                    :static/slug-param :slug-param
                    :static/parse :parse})
                 (select-keys [:root :ext :lang-param :slug-param :parse]))]
    {:expansions [[:post query-fs params opts]]}))

(defprotocol ^:private RequestCreator
  (create-request [this path config]))

(defn- get-path-segment [segments i]
  (if (integer? i)
    (get segments i)
    i))

(defn- extrapolate-uri [v path]
  (let [segments (vec (filter (complement empty?) (string/split path #"/")))]
    (str "/" (string/join "/" (map (partial get-path-segment segments) v)))))

(defn abs-path->uri [abs-path dir ext]
  (subs abs-path (count dir) (- (count abs-path) (count ext))))

(comment
  (extrapolate-uri ["a"] "whatever")
  (extrapolate-uri [0 1 2] "/a/b/c")
  (extrapolate-uri [2 1 0] "/a/b/c")
  (extrapolate-uri [0 "then" 1 "then" 2] "/a/b/c")

  (abs-path->uri "/var/www/a/b/c.md" "/var/www" ".md")

  (query-fs {}
            {:slug "one" :lang "en"}
            {:root "content" :ext "md" :lang-param :lang :slug-param :slug
             :parse md/md-to-html-string-with-meta})
  )

(extend-protocol RequestCreator
  clojure.lang.Fn
  (create-request [f path config]
    (f path config))

  clojure.lang.PersistentArrayMap
  (create-request [m path config]
    (let [v (:uri m)
          path (abs-path->uri path (:dir config) (:ext config))]
      (when-not (vector? v)
        (throw (IllegalArgumentException.
                 "(:uri path->req) must be a vector")))
      (assoc m :uri (extrapolate-uri v path))))

  clojure.lang.PersistentVector
  (create-request [v path config]
    (let [path (abs-path->uri path (:dir config) (:ext config))]
      {:uri (extrapolate-uri v path)})))

(defn request-creator [{:keys [dir ext path->req]}]
  (or path->req
      (fn [path _]
        {:uri (abs-path->uri path dir ext)})))

(defn- watch-handler [f config]
  (with-meta
    (fn [{:keys [action file]}]
      (when (= :modify action)
        (when-let [req (create-request
                         (request-creator config)
                         (.getCanonicalPath file)
                         config)]
          (f req))))
    {:handler f
     :config config}))

(defn- watch-route [handler route]
  (when-let [config (bread/watch-config route)]
    (let [;; We need to get the absolute path of dir to correctly handle
          ;; absolute Markdown/content file paths later on.
          dir (io/file (:dir config))
          config (assoc config :dir (.getCanonicalPath dir))]
      (watch/watch-dir (watch-handler handler config) dir))))

(defn watch-routes [handler router]
  (let [watchers (doall
                   (filter some? (map (fn [route]
                                        (watch-route handler route))
                                      (bread/routes router))))]
    (fn []
      (doall (for [watcher watchers]
               (watch/close-watcher watcher))))))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [root ext lang-param slug-param parse-meta? parse]
     :or {root "content"
          ext ".md"
          lang-param :lang
          slug-param :slug
          parse-meta? true}}]
   (let [parse (cond
                 parse parse
                 parse-meta? md/md-to-html-string-with-meta
                 :else md/md-to-html-string)]
     {:config
      {:static/root root
       :static/ext ext
       :static/lang-param lang-param
       :static/slug-param slug-param
       :static/parse parse
       :static/parse-meta? parse-meta?}})))
