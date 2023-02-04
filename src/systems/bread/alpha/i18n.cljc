(ns systems.bread.alpha.i18n
  (:require
    [clojure.string :as string]
    [clojure.walk :as walk]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.util.datalog :as datalog]))

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

(defn- get-binding [search data]
  (let [field (search data)]
    (cond
      field [field []]
      (seqable? data) (some (fn [[k v]]
                              (when-let [[field path]
                                         (get-binding search v)]

                                [field (cons k path)]))
                            (if (map? data)
                              data (map-indexed vector data))))))

(defn translatable-paths [ks qk data]
  (reduce (fn [paths search-key]
            (let [search (partial datalog/attr-binding search-key)
                  [field-binding path] (get-binding search data)]
              (if field-binding
                (conj paths [field-binding
                             (concat [qk]
                                     (filterv keyword? path)
                                     [search-key])])
                paths)))
          [] ks))

(comment
  (translatable-paths
    #{:post/fields :taxon/fields} :post
    [:db/id :post/slug {:post/fields [:field/content]}])
  (translatable-paths
    #{:taxon/fields} :x
    [:taxon/slug {:taxon/fields [:field/key :field/content]}]))

(defn- replace-bindings [[_ sym spec] bindings]
  (let [pred (set bindings)]
    (list 'pull sym
          (walk/postwalk
            (fn [x]
              ;; If the current node is a binding map matching one of our
              ;; field-bindings, replace it with its sole key. We do this so
              ;; we have a :db/id in the query results to walk over and
              ;; replace with the full result later.
              (if-let [binding-map (pred x)]
                (first (keys binding-map))
                x))
            spec))))

(defn- syms
  ([prefix]
   (syms prefix 0))
  ([prefix start]
   (for [n (range)] (symbol (str prefix (+ start n))))))

(defn- relation-reversed? [k]
  (string/starts-with? (name k) "_"))

(defn- reverse-relation [k]
  (let [[kns kname] ((juxt namespace name) k)]
    (keyword (string/join "/" [kns (subs kname 1)]))))

(comment
  (take 3 (syms "?e"))
  (reverse-relation :post/_taxons))

(defn- construct-fields-query [lang orig-query k spec path]
  (let [fields-pull (cons :db/id (get spec (last path)))
        rels (cons :field/lang (reverse (rest path)))
        depth (dec (count rels))
        left-syms (cons '?e (take depth (syms "?e")))
        right-syms (cons '?lang (butlast left-syms))
        ;; TODO support specifying :field/key's to filter by...
        input-sym (last left-syms)
        where-clause (map (fn [s k s']
                            (if (relation-reversed? k)
                              [s' (reverse-relation k) s]
                              [s k s']))
                          left-syms rels right-syms)]
    {:query/name ::store/query
     :query/db (:query/db orig-query)
     :query/key path
     :query/args
     [{:find [(list 'pull '?e fields-pull)]
       :in ['$ input-sym '?lang]
       :where where-clause}
      [::bread/data k :db/id]
      lang]}))

(defn- restructure [query pairs f]
  (if (seq pairs)
    (vec (concat
           (let [bindings (map first pairs)
                 pull (-> query :query/args first :find first (replace-bindings
                                                                bindings))]
             [(update query :query/args
                      #(-> % vec (assoc-in [0 :find 0] pull)))])
           (map #(apply f %) pairs)))
    [query]))

(defn- extract-pull [{:query/keys [args]}]
  ;; {:find [(pull ?e _____)]}
  ;;                  ^^^^^ this
  (-> args first :find first rest second))

(defmethod bread/action ::queries
  i18n-queries
  [req _ [{k :query/key :as query}]]
  "Internationalizes the given query, returning a vector of queries for
  translated content (i.e. :field/content in the appropriate lang).
  If no translation is needed, returns a length-1 vector containing only the
  original query."
  (let [attrs (bread/config req :i18n/db-attrs)
        translatable-pairs (translatable-paths attrs k (extract-pull query))
        req-lang (lang req)
        f (partial construct-fields-query req-lang query k)]
    (restructure query translatable-pairs f)))

(defmethod bread/action ::path-params
  [req _ [params]]
  (assoc params (bread/config req :i18n/lang-param) (lang req)))

(defmethod bread/action ::add-strings-query
  [req _ _]
  (query/add req {:query/name ::store/query
                  :query/key :i18n
                  :query/into {}
                  :query/db (store/datastore req)
                  :query/args
                  ['{:find [?key ?string]
                     :in [$ ?lang]
                     :where [[?e :i18n/key ?key]
                             [?e :i18n/string ?string]
                             [?e :i18n/lang ?lang]]}
                   (lang req)]}))

(defmethod bread/action ::add-lang-query
  [req _ _]
  (query/add req {:query/name ::bread/value
                  :query/key :lang
                  :query/value (lang req)}))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [lang-param fallback-lang supported-langs db-attrs
            query-strings? query-lang?]
     :or {lang-param :lang
          fallback-lang :en
          supported-langs #{:en}
          db-attrs #{:post/fields :taxon/fields :user/fields}
          query-strings? true
          query-lang? true}}]
   {:config
    {:i18n/lang-param lang-param
     :i18n/fallback-lang fallback-lang
     :i18n/supported-langs supported-langs
     :i18n/db-attrs db-attrs}
    :hooks
    {::queries
     [{:action/name ::queries
       :action/description "Internationalize a query"}]
     :hook/path-params
     [{:action/name ::path-params
       :action/description "Get internationalized path params from route"}]
     ::bread/dispatch
     (filterv
       identity
       [(when query-strings?
          {:action/name ::add-strings-query
           :action/description "Add global strings query"})
        (when query-lang?
          {:action/name ::add-lang-query
           :action/description "Add lang value query"})])}}))
