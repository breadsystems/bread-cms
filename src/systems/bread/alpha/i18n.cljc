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

(defn compact-fields
  "Takes a sequence of translatable fields and compacts it down to a single map,
  using :field/key and :field/content as the map keys and values, respectively."
  [fields]
  (into {} (map (juxt :field/key :field/content)) fields))

(defmulti format-field-content (fn [fmt _field] fmt))

(defmethod format-field-content :edn [_ content]
  (edn/read-string content))

(defmethod format-field-content :default [_ content]
  content)

(defn format-fields
  "Formats each field's :field/content according to :field/format (by calling
  format-field-content)."
  [fields]
  (map (fn [{fmt :field/format :as field}]
         (update field :field/content (partial format-field-content fmt)))
       fields))

(defmethod bread/query ::fields
  [{k :query/key lang :field/lang :keys [format? compact? spaths]} data]
  (let [e (query/get-at data k)]
    (if e
      (let [chain [(when compact? compact-fields)
                   (when format? format-fields)
                   (fn [fields]
                     (filter #(= lang (:field/lang %)) fields))]
            process (apply comp (conj (filterv identity chain)))]
        (reduce
          (fn [e spath]
            (s/transform spath process e))
          e spaths))
      false)))

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
        querying-many? (not= '. (last (:find (d/normalize-query dbq))))]
    (if (seq bindings)
      [(reduce
         (fn [query {:keys [binding-path relation entity-index]}]
           (s/transform (concat [:query/args 0 ;; datalog query
                                 :find         ;; find clause
                                 entity-index  ;; find position
                                 s/LAST]       ;; pull-expr
                                binding-path)  ;; within pull-expr
                        (partial d/ensure-attrs
                                 [:field/lang :field/key :db/id])
                        query))
         query bindings)
       {:query/name ::fields
        :query/key (:query/key query)
        :query/description "Process translatable fields."
        :field/lang (lang req)
        :compact? (bread/config req :i18n/compact-fields?)
        :format? (bread/config req :i18n/format-fields?)
        :recur-attrs nil
        :spaths
        (map (comp #(if querying-many? (concat [s/ALL s/ALL] %) %)
                   (partial qi/relation->spath
                            (bread/hook req ::bread/attrs-map))
                   :relation)
             bindings)}]
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
