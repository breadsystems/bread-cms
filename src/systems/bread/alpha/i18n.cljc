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

(defn req->lang [req]
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
  (-t [x app]))

(extend-protocol Translatable
  java.lang.Object
  (-t [s _] s)

  clojure.lang.PersistentArrayMap
  (-t [m app]
    (into {} (doall (map (juxt key (comp #(-t % app) val)) m))))

  clojure.lang.LazySeq
  (-t [sq app]
    (map #(-t % app) sq))

  clojure.lang.PersistentVector
  (-t [v app]
    (vec (map #(-t % app) v)))

  clojure.lang.Keyword
  (-t [k app]
    (t* app k)))

(defn t [app k]
  (-t k app))

(defn plugin
  ([]
   (plugin {}))
  ([opts]
   (let [->lang (:i18n/req->lang opts req->lang)
         fallback (:i18n/fallback opts :en)]
     (fn [app]
       (-> app
           (bread/set-config :i18n/fallback-lang fallback)
           (bread/add-hook :hook/lang ->lang))))))
