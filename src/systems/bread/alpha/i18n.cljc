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

(defn- remove-bindings [[_ sym spec] rm]
  (let [pred (complement (set rm))]
    (list 'pull sym (walk/postwalk (fn [x]
                                     (if (and (sequential? x)
                                              (not (map-entry? x)))
                                       (filterv pred x)
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
  (let [rel (last path)
        spec (get spec rel)
        depth (dec (count path))
        ;; TODO eliminate this reverse...
        ;; We don't actually need this reverse here, it's just what the tests
        ;; expect and it's a very surgical procedure to change that.
        left-syms (reverse (cons '?e (take depth (syms "?e"))))
        where-clause
        (conj
          (mapv (fn [s1 k s2]
                  (if (relation-reversed? k)
                    [s2 (reverse-relation k) s1]
                    [s1 k s2]))
                left-syms (rest path) (rest left-syms))
          '[?e :field/lang ?lang])]
    {:query/name ::store/query
     :query/db (:query/db orig-query)
     :query/key path
     :query/args
     [{:find [(list 'pull '?e (cons :db/id spec))]
       :in ['$ (first left-syms) '?lang]
       :where where-clause}
      [::bread/data k :db/id]
      lang]}))

(defn internationalize-query
  "Takes a Bread query and separates out any translatable fields into their
  own subsequent queries, returning the entire sequence of one or more queries.
  The underlying Datalog query must be a map (i.e. with :find as a key) and
  must contain a pull as the first item in its :find clause."
  [attrs query lang]
  (let [{:query/keys [args db] k :query/key} query
        ;; {:find [(pull ?e _____)]}
        ;;        this here ^^^^^
        pull (some-> args first :find first rest second)
        translatables (translatable-paths attrs k pull)]
    (if (seq translatables)
      (concat
        (let [bindings-to-rm (map first translatables)
              pull (-> args first :find first (remove-bindings
                                                bindings-to-rm))]
          [(update query :query/args #(-> % vec (assoc-in [0 :find 0] pull)))])
        (map (fn [[spec path]]
               (construct-fields-query lang query k spec path))
             translatables))
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
