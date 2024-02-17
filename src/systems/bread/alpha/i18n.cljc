(ns systems.bread.alpha.i18n
  (:require
    [clojure.edn :as edn]
    [com.rpl.specter :as s]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.internal.query-inference :as qi]
    [systems.bread.alpha.util.datalog :as d]))

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
        supported (get (supported-langs req) (keyword (lang-param params)))
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
   (->> (db/q (db/database req)
              '{:find [?key ?content]
                :in [$ ?lang]
                :where [[?e :field/key ?key]
                        [?e :field/content ?content]
                        [?e :field/lang ?lang]
                        (not-join [?e] [_ :translatable/fields ?e])]}
              lang)
        (into {})
        (bread/hook req ::strings))))

(defn t
  "Query the database for the translatable string represented by keyword k."
  [app k]
  {:pre [(keyword? k)]}
  (k (strings app)))

(defn translatable-binding?
  "Takes a query binding vector and returns the binding itself if it is
  translatable, otherwise nil."
  [qb]
  (when-let [qb (seq qb)] (some #{'* :field/content} qb)))

(comment
  (translatable-binding? [:field/content])
  (translatable-binding? ['*])
  (translatable-binding? [])
  (translatable-binding? [:post/slug :post/authors :post/fields]))

(defn- compact* [fields]
  (into {} (map (juxt :field/key :field/content)) fields))

(defmethod bread/query ::compact
  [{k :query/key spath :spath} data]
  (let [e (get data k)]
    (if e (s/transform spath compact* (get data k)) e)))

(defmethod bread/query ::filter-fields
  [{k :query/key lang :field/lang spath :spath} data]
  (let [e (get data k)]
    (if e
      (s/transform spath (fn [fields]
                           (filter #(= lang (:field/lang %)) fields))
                   e)
      e)))

(defmethod bread/action ::queries
  i18n-queries
  [req _ [{:query/keys [db] :as query}]]
  "Internationalizes the given query, returning a vector of queries for
  translated content (i.e. :field/content in the appropriate lang).
  If no translation is needed, returns a length-1 vector containing only the
  original query."
  (let [dbq (first (:query/args query))
        {:keys [bindings]} (qi/infer-query-bindings
                             :translatable/fields
                             translatable-binding?
                             dbq)
        attrs-map (bread/hook req ::bread/attrs-map)
        field-lang (lang req)]
    (if (seq bindings)
      (let [{qn :query/name k :query/key :query/keys [db args]} query
            attrs-map (bread/hook req ::bread/attrs-map)
            db-and-filter-qs
            (reduce
              (fn [qs {:keys [binding-path relation entity-index] :as _b}]
                (conj
                  (s/transform
                    (concat [0 :query/args 0 ;; db query
                             :find           ;; find clause
                             entity-index    ;; position within find
                             s/LAST]         ;; pull expr
                            binding-path)
                    (partial d/ensure-attrs [:field/lang :field/key :db/id])
                    qs)
                  {:query/name ::filter-fields
                   :query/key k
                   :field/lang field-lang
                   :spath (qi/relation->spath attrs-map relation)}))
              [query]
              bindings)
            format-qs (when (bread/config req :i18n/format-fields?)
                        (map (fn [{:keys [relation]}]
                               {:query/name ::format
                                :query/key k
                                :spath (qi/relation->spath attrs-map relation)})
                             bindings))
            compact-qs (if (bread/config req :i18n/compact-fields?)
                         (map (fn [{:keys [relation]}]
                                {:query/name ::compact
                                 :query/key k
                                 :spath (qi/relation->spath attrs-map relation)})
                              bindings)
                         [])]
        (concat db-and-filter-qs format-qs compact-qs))
      [query])))

(defmethod bread/action ::path-params
  [req _ [params]]
  (assoc params (bread/config req :i18n/lang-param) (lang req)))

(defmethod bread/action ::add-strings-query
  [req _ _]
  (query/add req {:query/name ::db/query
                  :query/key :i18n
                  :query/into {}
                  :query/db (db/database req)
                  :query/args
                  ['{:find [?key ?content]
                     :in [$ ?lang]
                     :where [[?e :field/key ?key]
                             [?e :field/content ?content]
                             [?e :field/lang ?lang]
                             (not-join [?e] [_ :translatable/fields ?e])]}
                   (lang req)]}))

(defmethod bread/action ::add-lang-query
  [req _ _]
  (query/add req {:query/name ::bread/value
                  :query/key :lang
                  :query/value (lang req)}))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [lang-param fallback-lang supported-langs
            query-strings? query-lang? format-fields? compact-fields?]
     :or {lang-param      :lang
          fallback-lang   :en
          supported-langs #{:en}
          query-strings?  true
          query-lang?     true
          format-fields?  true
          compact-fields? true}}]
   {:config
    {:i18n/lang-param      lang-param
     :i18n/fallback-lang   fallback-lang
     :i18n/supported-langs supported-langs
     :i18n/format-fields?  format-fields?
     :i18n/compact-fields? compact-fields?}
    :hooks
    {::queries
     [{:action/name ::queries
       :action/description "Internationalize the given queries"}]
     :hook/path-params
     [{:action/name ::path-params
       :action/description "Get internationalized path params from route"}]
     ::bread/dispatch
     [(when query-strings?
        {:action/name ::add-strings-query
         :action/description "Add global strings query"})
      (when query-lang?
        {:action/name ::add-lang-query
         :action/description "Add lang value query"})]}}))
