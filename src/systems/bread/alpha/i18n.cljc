(ns systems.bread.alpha.i18n
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.query :as query]))

;; TODO make this fn pluggable
(defn supported-langs
  "Checks all supported languages in the database. Returns supported langs
   as a set of keywords."
  [app]
  (set (map first
            (let [db (store/datastore app)]
              (store/q db
                     '{:find [?lang] :in [$]
                       :where [[?e :i18n/lang ?lang]]})))))

(defn lang
  "High-level fn for getting the language for the current request."
  [req]
  (let [params (route/params req (route/match req))
        ;; TODO configurable param key
        supported ((supported-langs req) (keyword (:lang params)))
        fallback (bread/config req :i18n/fallback-lang)
        lang (or supported fallback)]
    (bread/hook->> req :hook/lang lang)))

(defn lang-supported?
  "Whether lang has any translatable strings available. Does not necessarily
  indicate that all translatable strings have translations for lang."
  [app lang]
  (contains? (supported-langs app) lang))

(defn strings-for
  "Load the strings from the database for the given language."
  [req lang]
  ;; TODO support locales
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

(defn t
  "Query the database for the translatable string represented by keyword k."
  [app k]
  {:pre [(keyword? k)]}
  (k (strings app)))

(defmethod bread/action ::path-params
  [req _ [_ params]]
  (assoc params (bread/config req :i18n/lang-param) (lang req)))

(defmethod bread/action ::add-queries
  [req _ _]
  (-> req
      (query/add [:i18n (fn [_] (strings req))])
      (query/add [:lang (fn [_] (lang req))])))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [lang-param fallback-lang]
     :or {lang-param :lang fallback-lang :en}}]
   {:config
    {:i18n/lang-param lang-param
     :i18n/fallback-lang fallback-lang}
    :hooks
    {:hook/path-params
     [{:action/name ::path-params
       :action/description "Get internationalized path params from route"}]
     ::bread/resolve
     [{:action/name ::add-queries
       :action/description "Add I18n queries"}]}}))
