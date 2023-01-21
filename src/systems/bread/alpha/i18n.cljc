(ns systems.bread.alpha.i18n
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.util.datalog :refer [empty-query where pull-query]]))

(defn supported-langs
  "Checks all supported languages in the database. Returns supported langs
  as a set of keywords."
  [req]
  (bread/hook req ::supported-langs
              (bread/config req :i18n/supported-langs)))

(defn lang
  "High-level fn for getting the language for the current request."
  [req]
  (let [params (route/params req (route/match req))
        lang-param (bread/config req :i18n/lang-param)
        supported ((supported-langs req) (keyword (lang-param params)))
        fallback (bread/config req :i18n/fallback-lang)
        lang (or supported fallback)]
    (bread/hook req :hook/lang lang)))

(defn lang-supported?
  "Whether lang has any translatable strings available. Does not necessarily
  indicate that all translatable strings have translations for lang."
  [app lang]
  (contains? (supported-langs app) lang))

(defn strings
  "Load the strings from the database for the current language, i.e.
  (lang req)."
  ([req]
   (strings req (lang req)))
  ([req lang]
   (->> (store/q (store/datastore req)
                 '{:find [?key ?string]
                   :in [$ ?lang]
                   :where [[?e :i18n/key ?key]
                           [?e :i18n/string ?string]
                           [?e :i18n/lang ?lang]]}
                 lang)
        (into {})
        (bread/hook req ::strings))))

(defn t
  "Query the database for the translatable string represented by keyword k."
  [app k]
  {:pre [(keyword? k)]}
  (k (strings app)))

(defn- map-with-keys [ks m]
  (when
    (and (map? m) (some ks (keys m)))
    m))

(defn internationalize-query
  "Takes a Bread query and separates out any translatable fields into their
  own subsequent queries, returning the entire sequence of one or more queries.
  The underlying Datalog query must be a map (i.e. with :find as a key) and
  must contain a pull as the first item in its :find clause."
  [query lang]
  (let [{:query/keys [args db] k :query/key} query
        ;; {:find [(pull ?e _____)]}
        pull (->> (get-in args [0 :find])
                  first rest second)
        ;; Find any appearances of :post/fields in the query. If it appears as
        ;; a map key, use the corresponding value as our pull expr. If it's a
        ;; a keyword, query for a sensible default. Always include :db/id in
        ;; the queried attrs.
        ;; TODO generalize this to only look for :field/content
        fields-args
        (when-let [fields-binding
                   (first (keep
                            (some-fn
                              #{:post/fields}
                              (partial map-with-keys #{:post/fields}))
                            pull))]
          (let [field-keys (or (:post/fields fields-binding)
                               [:field/key :field/content])]
            (-> (empty-query)
                (assoc-in [0 :find]
                          [(list 'pull '?e (cons :db/id field-keys))])
                (where [['?p :post/fields '?e [::bread/data k :db/id]]
                        ['?lang :field/lang lang]]))))
        ;; TODO update original args to omit i18n fields.
        args
        (if fields-args
          (update-in args [0 :find] identity)
          args)]
    (if fields-args
      [(assoc query :query/args args)
       {:query/name ::store/query
        :query/key [k :post/fields]
        :query/db db
        :query/args fields-args}]
      [query])))

(defmethod bread/action ::path-params
  [req _ [params]]
  (assoc params (bread/config req :i18n/lang-param) (lang req)))

(defmethod bread/action ::add-queries
  [req _ _]
  (-> req
      (query/add {:query/name ::store/query
                  :query/key :i18n
                  :query/into {}
                  :query/db (store/datastore req)
                  :query/args
                  ['{:find [?key ?string]
                     :in [$ ?lang]
                     :where [[?e :i18n/key ?key]
                             [?e :i18n/string ?string]
                             [?e :i18n/lang ?lang]]}
                   (lang req)]})
      (query/add {:query/name ::bread/value
                  :query/key :lang
                  :value (lang req)})))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [lang-param fallback-lang supported-langs]
     :or {lang-param :lang fallback-lang :en supported-langs #{:en}}}]
   {:config
    {:i18n/lang-param lang-param
     :i18n/fallback-lang fallback-lang
     :i18n/supported-langs supported-langs}
    :hooks
    {:hook/path-params
     [{:action/name ::path-params
       :action/description "Get internationalized path params from route"}]
     ::bread/dispatch
     [{:action/name ::add-queries
       :action/description "Add I18n queries"}]}}))

(comment
  (require '[kaocha.repl :as k])
  (k/run 'systems.bread.alpha.i18n-test))
