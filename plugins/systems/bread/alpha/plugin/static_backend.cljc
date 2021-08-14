;; Utilities for reading content from the filesystem
;; rather than from the datastore.
(ns systems.bread.alpha.plugin.static-backend
  (:require
    [clojure.instant :as instant]
    [clojure.set :refer [rename-keys]]
    [clojure.string :as string]
    [clojure.java.io :as io]
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

;; TODO watch files
(defn plugin
  ([]
   (plugin {}))
  ([{:keys [root ext lang-param slug-param]}]
   (fn [app]
     (cond-> app
       root (bread/set-config :static-backend/root root)
       ext (bread/set-config :static-backend/ext ext)
       lang-param (bread/set-config :static-backend/lang-param lang-param)
       slug-param (bread/set-config :static-backend/slug-param slug-param)))))
