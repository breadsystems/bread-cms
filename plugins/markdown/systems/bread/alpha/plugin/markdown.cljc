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

(defmethod bread/action ::join-metadata
  [{:as req :keys [::bread/dispatcher]} _action [content]]
  (let [ks (->> (bread/config req :markdown/join-metadata-keys)
                   (:join-metadata-keys dispatcher)
                   (bread/hook req ::join-metadata-keys))]
    (cond
      (true? ks)
      (update content :metadata #(into {} (map (juxt key (comp (partial string/join "\n") val)) %)))
      ks
      (let [ks (set ks)]
        (update content :metadata #(into {} (map (fn [[k v]]
                                                   (if (contains? (set ks) k)
                                                     [k (string/join "\n" v)]
                                                     [k v])) %))))
      :else content)))

(comment
  (md/md-to-html-string-with-meta (slurp (io/resource "pages/en/markdown-example.md")))
  ,)

(defmethod bread/expand ::page
  [{:keys [filepaths hook expansion/key]} _]
  (let [file (loop [[filepath & filepaths] filepaths]
               (let [file (io/resource filepath)]
                 (cond
                   file file
                   (seq filepaths) (recur filepaths))))]
    (when file
      (hook ::parsed (md/md-to-html-string-with-meta (slurp file)) file))))

(defmethod bread/dispatch ::page=>
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
    {:expansions [{:expansion/name ::page
                   :expansion/key (or (:dispatcher/key dispatcher) :markdown)
                   :filepaths (bread/hook req ::filepaths filepaths params)
                   :hook (partial bread/hook req)}]}))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [paths extensions index-filename join-metadata-keys slug-param]
     :or {paths ["pages"]
          extensions [".md"]
          index-filename "index.md"
          join-metadata-keys true
          slug-param :slug}}]
   {:config {:markdown/paths paths
             :markdown/extensions extensions
             :markdown/index-filename index-filename
             :markdown/join-metadata-keys join-metadata-keys
             :markdown/slug-param slug-param}
    :hooks
    {::parsed
     [{:action/name ::join-metadata
       :action/description "join metadata from a markdown file."}]}}))
