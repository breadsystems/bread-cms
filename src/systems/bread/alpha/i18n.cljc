(ns systems.bread.alpha.i18n
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]))

(defn lang
  "High-level fn for getting the language for the current request."
  [req]
  (or (bread/hook req :hook/lang) (bread/config req :i18n/fallback-lang)))

(defn key?
  "Checks whether the given keyword is an i18n key for a translatable string."
  [x]
  (and (keyword? x) (= "i18n" (namespace x))))

(defn supported-langs
  "Checks all supported languages in the database. Returns supported langs
   as a set of keywords."
  [app]
  ;; TODO query db for these
  #{:en :fr})

(defn- route-segments [{:keys [uri] :as req}]
  (filter (complement empty?) (str/split (or uri "") #"/")))

(defn- req->lang [req]
  ((supported-langs req) (keyword (first (route-segments req)))))

(defn lang-supported?
  "Whether lang has any translatable strings available. Does not necessarily
  indicate that all translatable strings have translations for lang."
  [app lang]
  (contains? (supported-langs app) lang))

(defn lang-route?
  "Whether the first segment of the current route looks like a ISO-639
  language code (two-char code with optional hyphen-and-two-char suffix.
  Does not guarantee a valid language code or that the language is supported."
  [req]
  (boolean (re-matches #"[a-z]{2}(-[a-z]{2})?"
                       (str/lower-case (first (route-segments req))))))

(defn t*
  "Query the database for the translatable string represented by keyword k.
  Returns the original keyword k if it is not recognized as a translatable key."
  [app k]
  {:pre [(keyword? k)]}
  (if (= "i18n" (namespace k))
    (let [query (vec (conj '[:find ?str
                             :where
                             [?e :i18n/string ?str]]
                           ['?e :i18n/lang (lang app)]
                           ['?e :i18n/key k]))]
      (ffirst (store/q (store/datastore app) query)))
    k))

(defprotocol Translatable
  (-translate [x app]))

(extend-protocol Translatable
  java.lang.Object
  (-translate [s _] s)

  clojure.lang.PersistentArrayMap
  (-translate [m app]
    (into {} (doall (map (juxt key (comp #(-translate % app) val)) m))))

  clojure.lang.LazySeq
  (-translate [sq app]
    (map #(-translate % app) sq))

  clojure.lang.PersistentVector
  (-translate [v app]
    (vec (map #(-translate % app) v)))

  clojure.lang.Keyword
  (-translate [k app]
    (t* app k)))

(defn strings-for
  "Load the strings from the database for the given language."
  [req lang]
  (->> (store/q (store/datastore req)
                (conj '[:find ?key ?str
                        :where
                        [?e :i18n/key ?key]
                        [?e :i18n/string ?str]]
                      ['?e :i18n/lang lang]))
       (into {})
       (bread/hook-> req :hook/strings-for)))

(defn strings
  "Load the strings from the database for the current language, i.e.
  (lang req)."
  [req]
  (bread/hook-> req :hook/strings (strings-for req (lang req))))

(defn translate
  "Translate an arbitrary object recursively, expanding i18n/* keywords into
  their respective translated strings in the current language."
  [app k]
  ;; TODO run a hook instead?
  (-translate k app))

(defn inject-strings [data req]
  (assoc data :i18n (strings req)))

(defn plugin
  ([]
   (plugin {}))
  ([opts]
   (let [->lang (:i18n/req->lang opts req->lang)
         fallback (:i18n/fallback opts :en)]
     (fn [app]
       (bread/with-hooks (bread/set-config app :i18n/fallback-lang fallback)
         (:hook/post translate)
         (:hook/view-data inject-strings)
         (:hook/lang ->lang))))))
