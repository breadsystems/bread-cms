;; Utilities for reading content from the filesystem
;; rather than from the database.
(ns systems.bread.alpha.plugin.markdown
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]
    [markdown.core :as md]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.interop :refer [separator]])
  (:import
    #?(:clj [java.io File])))

(defmethod bread/action ::singularize-metadata
  [{:as req :keys [::bread/dispatcher]} _action [content]]
  (let [configured? (bread/config req :markdown/singularize-metadata?)
        singularize? (bread/hook req ::singularize-metadata?
                                 (:singularize-metadata? dispatcher configured?))]
    (if singularize?
      (update content :metadata #(into {} (map (juxt key (comp first val)) %)))
      content)))

(defmethod bread/expand ::markdown
  [{:keys [filepaths hook]} _]
  (let [file (loop [[filepath & filepaths] filepaths]
               (let [file (io/resource filepath)]
                 (cond
                   file file
                   (seq filepaths) (recur filepaths))))]
    (when file
      (hook ::html (md/md-to-html-string-with-meta (slurp file)) file))))

(defmethod bread/dispatch ::markdown=>
  [{:as req dispatcher ::bread/dispatcher}]
  (let [params (:route/params dispatcher)
        slug (get params (bread/config req :markdown/slug-param))
        extensions (bread/config req :markdown/extensions)
        paths (bread/config req :markdown/paths)
        index-filename (bread/config req :markdown/index-filename)
        ->path (fn [& path-components]
                 (let [path-components (filter identity path-components)]
                   (string/join separator (map name path-components))))
        filepaths (if slug
                    (for [path paths ext extensions]
                      (->path path (i18n/lang req) (str slug ext)))
                    (for [path paths]
                      (->path path (i18n/lang req) index-filename)))]
    {:expansions [{:expansion/name ::markdown
                   :expansion/key (:dispatcher/key dispatcher :markdown)
                   :filepaths (bread/hook req ::filepaths filepaths params)
                   :hook (partial bread/hook req)}]}))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [paths extensions index-filename singularize-metadata? slug-param]
     :or {paths ["public"]
          extensions [".md"]
          index-filename "index.md"
          singularize-metadata? true
          slug-param :slug}}]
   {:config {:markdown/paths paths
             :markdown/extensions extensions
             :markdown/index-filename index-filename
             :markdown/singularize-metadata? singularize-metadata?
             :markdown/slug-param slug-param}
    :hooks
    {::html
     [{:action/name ::singularize-metadata
       :action/description "Singularize metadata from a markdown file."}]}}))
