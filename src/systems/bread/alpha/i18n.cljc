(ns systems.bread.alpha.i18n
  (:require
    [clojure.edn :as edn]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as store]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.internal.query-inference :as inf]
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
   (->> (store/q (store/database req)
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

(defn- syms
  ([prefix]
   (syms prefix 0))
  ([prefix start]
   (for [n (range)] (symbol (str prefix (+ start n))))))

(defn- construct-fields-query [lang {k :query/key :as orig-query} spec path]
  (let [fields-pull (cons :db/id (get spec (last path)))
        rels (cons :field/lang (reverse (rest path)))
        depth (dec (count rels))
        left-syms (cons '?e (take depth (syms "?e")))
        right-syms (cons '?lang (butlast left-syms))
        ;; TODO support specifying :field/key's to filter by...
        input-sym (last left-syms)
        where-clause (map (fn [s k s']
                            (if (d/relation-reversed? k)
                              [s' (d/reverse-relation k) s]
                              [s k s']))
                          left-syms rels right-syms)
        id-path (if (sequential? k)
                  (concat [::bread/data] k [:db/id])
                  [::bread/data k :db/id])]
    {:query/name ::store/query
     :query/db (:query/db orig-query)
     :query/key path
     :query/args
     [{:find [(list 'pull '?e fields-pull)]
       :in ['$ input-sym '?lang]
       :where where-clause}
      id-path
      lang]}))

(defn- field-content-binding? [binding-map]
  (let [k (first (keys binding-map))
        v (get binding-map k)]
    (some #{:field/content '*} v)))

(defn- field-kv [field]
  (let [field (if (sequential? field) (first field) field)
        {k :field/key content :field/content} field]
    ;; TODO :field/format ?
    (when k [k (edn/read-string content)])))

(defn- compact-fields [fields]
  (if (map? fields)
    fields
    (into {} (map field-kv fields))))

(defn compact
  "Takes an entity with a :translatable/fields attr in raw form (as from a
  datalog query) and compacts fields into a single map where the keys and
  values are the :field/key and (EDN-parsed) :field/content, respectively.

  The :translatable/fields attr can take the form of:
  - a map (this is a noop)
  - a vector of maps e.g. [{:field/key ... :field/content ...} ...]
  - a vector of length-1 vectors of maps e.g.
    [[{:field/key ... :field/content ...}] [...]]"
  [{fields :translatable/fields :as entity}]
  (if (and entity (seq entity))
    (let [entity (if (sequential? entity) (first entity) entity)]
      (update entity :translatable/fields compact-fields))
    entity))

(defmethod bread/action ::queries
  i18n-queries
  [req _ [queries]]
  "Internationalizes the given query, returning a vector of queries for
  translated content (i.e. :field/content in the appropriate lang).
  If no translation is needed, returns a length-1 vector containing only the
  original query."
  (inf/infer
    queries
    {:translatable/fields field-content-binding?}
    (partial construct-fields-query (lang req))))

(defmethod bread/action ::path-params
  [req _ [params]]
  (assoc params (bread/config req :i18n/lang-param) (lang req)))

(defmethod bread/action ::add-strings-query
  [req _ _]
  (query/add req {:query/name ::store/query
                  :query/key :i18n
                  :query/into {}
                  :query/db (store/database req)
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
            query-strings? query-lang?]
     :or {lang-param :lang
          fallback-lang :en
          supported-langs #{:en}
          query-strings? true
          query-lang? true}}]
   {:config
    {:i18n/lang-param lang-param
     :i18n/fallback-lang fallback-lang
     :i18n/supported-langs supported-langs}
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
