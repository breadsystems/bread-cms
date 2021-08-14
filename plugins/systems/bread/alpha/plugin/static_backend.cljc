;; Utilities for reading content from the filesystem
;; rather than from the datastore.
(ns systems.bread.alpha.plugin.static-backend
  (:require
    [clojure.instant :as instant]
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

;; TODO option[s] for metadata
(defn query-fs [data params {:keys [root ext lang-param slug-param]}]
  (let [sep java.io.File/separator
        path (string/join sep (map params [lang-param slug-param]))
        path (str root sep path ext)
        {:keys [metadata html]}
        (some-> path io/resource slurp md/md-to-html-string-with-meta)]
    (prn path '-> (-> path io/resource slurp))
    (when html
      (assoc metadata :html html))))

(defmethod resolver/resolve-query :resolver.type/static
  [{::bread/keys [resolver] :as req}]
  (let [params (:route/params resolver)
        opts {:root (bread/config req :static-backend/root)
              :ext (bread/config req :static-backend/ext)
              :lang-param (bread/config req :static-backend/lang-param)
              :slug-param (bread/config req :static-backend/slug-param)}]
    [[:post query-fs params opts]]))

;; TODO watch files
(defn plugin
  ([]
   (plugin {}))
  ([{:keys [root ext lang-param slug-param]
     :or {root "content"
          ext ".md"
          lang-param :lang
          slug-param :slug}}]
   (fn [app]
     (bread/set-config
       app
       :static-backend/root root
       :static-backend/ext ext
       :static-backend/lang-param lang-param
       :static-backend/slug-param slug-param))))
