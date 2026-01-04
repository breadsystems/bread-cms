(ns systems.bread.alpha.i18n
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [com.rpl.specter :as s]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.internal.query-inference :as qi]
    [systems.bread.alpha.util.datalog :as d]))

(defn supported-langs
  "Checks all supported languages in the database. Returns supported langs
  as a set of keywords."
  [req]
  (bread/hook req ::supported-langs (bread/config req :i18n/supported-langs)))

(comment
  (->double "0.4")
  (->double "1.4234")
  (->double "1.4234x")
  (accepted-lang-ranges "*")
  (accepted-lang-ranges "; DROP TABLE users;--")
  ;;
  )

(defn- ->double [x]
  (try
    (Double. (str x))
    (catch java.lang.NumberFormatException _)))

(defn- accepted-lang-ranges [header]
  (when header
    (->> (string/split header #",")
         (map (fn [lang-range]
                (let [[lang-range quality]
                      (map string/trim (string/split lang-range #";q="))]
                  {:lang (keyword lang-range) :quality (or (->double quality) 1)})))
         ;; TODO formalize validation
         (filter #(< 1 (count (name (:lang %))) 7))
         (sort-by :quality)
         (reverse)
         (map :lang))))

(defn- accept-first [candidates lang-ranges]
  (when (seq lang-ranges)
    (reduce (fn
              ([] nil)
              ([_ lang-range]
               (cond
                 (contains? candidates lang-range) (reduced lang-range)
                 (re-find #"-" (name lang-range))
                 (reduced (keyword (first (string/split (name lang-range) #"-")))))))
            [] lang-ranges)))

(defn lang
  "High-level fn for getting the language for the current request."
  [{:as req :keys [headers]}]
  (let [router (route/router req)
        params (when router (bread/route-params router req))
        lang-param (bread/config req :i18n/lang-param)
        candidates (supported-langs req)
        supported (get candidates (keyword (get params lang-param)))
        ;; TODO support sublangs
        accept-langs (accepted-lang-ranges (get headers "accept-language"))
        lang (or supported
                 (accept-first candidates accept-langs)
                 (bread/config req :i18n/fallback-lang))]
    (bread/hook req ::lang lang)))

(defn rtl?
  "Whether the lang for the current request is written right-to-left according
  to :i18n/rtl-langs config."
  [req]
  (contains? (bread/config req :i18n/rtl-langs) (lang req)))

(defn dir
  "Whether the lang for the current request is written right-to-left according
  to :i18n/rtl-langs config."
  [req]
  (if (rtl? req) :rtl :ltr))

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
                        (not-join [?e] [_ :thing/fields ?e])]}
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
  (translatable-binding? [:thing/slug :post/authors :post/fields]))

(defn compact-fields
  "Takes a sequence of translatable fields and compacts it down to a single map,
  using :field/key and :field/content as the map keys and values, respectively."
  [fields]
  (let [content-by-key (into {} (map (juxt :field/key :field/content)) fields)
        data-by-key (into {} (map (juxt :field/key identity)) fields)]
    (assoc content-by-key :bread/fields data-by-key)))

(defmulti deserialize :field/format)
(defmulti serialize :field/format)

(defmethod deserialize :default [field]
  (:field/content field))

(defmethod serialize :default [field]
  (:field/content field))

(defmethod deserialize :edn [field]
  (edn/read-string (:field/content field)))

(defmethod serialize :edn [field]
  (pr-str (:field/content field)))

(defmethod deserialize ::uri
  [{content :field/content :as field}]
  (->> content
       edn/read-string
       (map #(name (get field % %)))
       (cons "") ;; ensure a leading slash
       (string/join "/")))

(defn with-serialized [field]
  (assoc field :field/content (serialize field)))

(defn format-fields
  "Formats each field's :field/content according to :field/format (by calling deserialize)."
  [context fields]
  (map (fn [field]
         (->> (merge context field) deserialize
              (assoc field :field/content)))
       fields))

(defmethod bread/expand ::fields
  [{k :expansion/key
    lang :field/lang
    :keys [format? compact? spaths recur-attrs]} data]
  (let [e (expansion/get-at data k)]
    (if e
      (let [chain [;; NOTE: chain gets passed to comp, so these operations
                   ;; happen in reverse!
                   (when compact? compact-fields)
                   ;; TODO hook for passing extra context...
                   (when format? (partial format-fields {:field/lang lang}))
                   (fn [fields]
                     (filter #(or (nil? (:field/lang %))
                                  (= lang (:field/lang %)))
                             fields))]
            process* (apply comp (conj (filterv identity chain)))
            [process spaths]
            (if (seq recur-attrs)
              ;; Query is recursive:
              ;; Wrap our process chain in a recursive transform.
              (let [walker (qi/attrs-walker :thing/fields recur-attrs)]
                [#(s/transform walker process* %) (map butlast spaths)])
              ;; Non-recursive query.
              [process* spaths])]
        (reduce
          (fn [e spath]
            (s/transform spath process e))
          e spaths))
      false)))

(defmethod bread/action ::expansions
  i18n-queries
  [req _ [{:as expansion :expansion/keys [db i18n?] :or {i18n? true}}]]
  "Internationalizes the given db expansion, returning a vector of queries for
  translated content (i.e. :field/content in the appropriate lang).
  If no translation is needed, returns a length-1 vector containing only the
  original query."
  (if i18n?
    (let [dbq (first (:expansion/args expansion))
          {:keys [bindings]} (qi/infer-query-bindings
                               :thing/fields
                               translatable-binding?
                               dbq)
          {recursive-specs :bindings} (qi/infer-query-bindings
                                        keyword?
                                        #(or (integer? %) (= '... %))
                                        dbq)
          ;; We only care about recursive specs at the same "level" or higher as
          ;; any of the field bindings, because if there's recursion at a lower
          ;; level than the fields', then by definition they don't include field
          ;; data and we don't need to worry about them.
          coincidental-paths (reduce (fn [tree b]
                                       (assoc-in tree (:relation b) true))
                                     {} bindings)
          recur-attrs (->> recursive-specs
                           (filter (fn [{rel :relation}]
                                     (get-in coincidental-paths (butlast rel))))
                           (map :attr) set)
          querying-many? (not= '. (last (:find (d/normalize-query dbq))))]
      (if (seq bindings)
        [(reduce
           (fn [query {:keys [binding-path relation entity-index]}]
             (s/transform (concat [:expansion/args 0 ;; datalog query
                                   :find             ;; find clause
                                   entity-index      ;; find position
                                   s/LAST]           ;; pull-expr
                                  binding-path)      ;; within pull-expr
                          (partial d/ensure-attrs
                                   [:field/lang :field/key :db/id])
                          query))
           expansion bindings)
         {:expansion/name ::fields
          :expansion/key (:expansion/key expansion)
          :expansion/description "Process translatable fields."
          :field/lang (lang req)
          :compact? (bread/config req :i18n/compact-fields?)
          :format? (bread/config req :i18n/format-fields?)
          :recur-attrs recur-attrs
          :spaths
          (map (comp #(if querying-many? (concat [s/ALL] %) %)
                     (partial qi/relation->spath
                              (bread/hook req ::bread/attrs-map nil))
                     :relation)
               bindings)}]
        [expansion]))
    [expansion]))

(defmethod bread/action ::path-params
  [req _ [params]]
  (assoc params (bread/config req :i18n/lang-param) (lang req)))

(defmethod bread/action ::expand-global-strings
  [req {:keys [global-strings]} _]
  (let [strings (bread/hook req ::global-strings
                            (get global-strings (lang req) {}))]
    (expansion/add req {:expansion/key :i18n
                        :expansion/name ::bread/value
                        :expansion/value strings})))

(defmethod bread/action ::merge-global-strings
  [req {:keys [strings]} [req-strings]]
  (merge req-strings (get strings (lang req))))

(defmethod bread/action ::add-strings-query
  [req _ _]
  (expansion/add req {:expansion/name ::db/query
                      :expansion/key :i18n
                      :expansion/into {}
                      :expansion/db (db/database req)
                      :expansion/args
                      ['{:find [?key ?content]
                         :in [$ ?lang]
                         :where [[?e :field/key ?key]
                                 [?e :field/content ?content]
                                 [?e :field/lang ?lang]
                                 (not-join [?e] [_ :thing/fields ?e])]}
                       (lang req)]}))

(defmethod bread/action ::add-rtl-expansion
  [req _ _]
  (expansion/add req {:expansion/name ::bread/value
                      :expansion/key :rtl?
                      :expansion/value (rtl? req)}))

(defmethod bread/action ::add-dir-expansion
  [req _ _]
  (expansion/add req {:expansion/name ::bread/value
                      :expansion/key :dir
                      :expansion/value (if (rtl? req) :rtl :ltr)}))

(defmethod bread/action ::add-lang-query
  [req _ _]
  (expansion/add req {:expansion/name ::bread/value
                      :expansion/key :field/lang
                      :expansion/value (lang req)}))

(defmethod bread/dispatch ::lang=> [req]
  (let [home-route (bread/config req :i18n/home-route)
        lang-param (bread/config req :i18n/lang-param)
        redirect-to (route/uri req home-route {lang-param (lang req)})]
    (when redirect-to
      (ring/redirect=> {:to redirect-to}))))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [lang-param fallback-lang supported-langs global-strings
            query-global-strings? query-lang? format-fields? compact-fields?
            rtl-langs lang-names home-route]
     :or {lang-param      :field/lang
          fallback-lang   :en
          supported-langs #{:en}
          global-strings {}
          query-global-strings?  true
          query-lang?     true
          format-fields?  true
          compact-fields? true
          rtl-langs #{:ar :he :fa :ur :ps :yi :ku
                      :sy :arc :dv :ug :sd :brh}
          lang-names {:ar "عربي"
                      :en "English"
                      :es "Español"
                      :fr "Français"}
          home-route :field/lang}}]
   {:config
    {:i18n/lang-param      lang-param
     :i18n/fallback-lang   fallback-lang
     :i18n/supported-langs supported-langs
     :i18n/format-fields?  format-fields?
     :i18n/compact-fields? compact-fields?
     :i18n/rtl-langs       rtl-langs
     :i18n/lang-names      lang-names
     :i18n/home-route      home-route}
    :hooks
    {::expansions
     [{:action/name ::expansions
       :action/description "Internationalize the given queries"}]
     ::path-params
     [{:action/name ::path-params
       :action/description "Get internationalized path params from route"}]
     ::bread/dispatch
     [(when rtl-langs
        {:action/name ::add-rtl-expansion
         :action/description "Add an expansion for whether req lang is RTL."})
      (when rtl-langs
        {:action/name ::add-dir-expansion
         :action/description "Add an expansion for req text direction."})
      (when global-strings
        {:action/name ::expand-global-strings
         :action/description "Add an expansion for globally configured strings."
         :global-strings global-strings})
      (when query-global-strings?
        {:action/name ::add-strings-query
         :action/description "Add global strings query"})
      (when query-lang?
        {:action/name ::add-lang-query
         :action/description "Add lang value query"})]}}))
