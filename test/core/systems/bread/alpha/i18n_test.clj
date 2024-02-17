(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [com.rpl.specter :as s]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              use-db]]))

(def config {:db/type :datahike
             :store {:backend :mem
                     :id "test-i18n-db"}
             :db/initial-txns
             ;; TODO test locales e.g. en-gb
             [{:translatable/fields #{{:field/key :one
                                       :field/content "Post One"
                                       :field/lang :en}
                                      {:field/key :one
                                       :field/content "Dossss"
                                       :field/lang :es}}}
              {:field/key :one :field/content "One" :field/lang :en}
              {:field/key :two :field/content "Two" :field/lang :en}
              {:field/key :one :field/content "Uno" :field/lang :es}
              {:field/key :two :field/content "Dos" :field/lang :es}]})

(use-db :each config)

(defmethod bread/action ::naive-params
  [{:keys [uri] :as req} _ _]
  (let [[lang & slugs] (filter (complement empty?)
                               (string/split (or uri "") #"/"))]
    {:lang lang :slugs slugs}))

(def naive-plugin
  {:hooks {::route/params [{:action/name ::naive-params}]}})

(defn- load-app [i18n-config]
  (plugins->loaded [(db/plugin config)
                    (i18n/plugin i18n-config)
                    naive-plugin]))


(deftest test-supported-langs
  (are
    [langs supported]
    (= langs (-> {:supported-langs supported} load-app i18n/supported-langs))

    nil nil
    #{} #{}
    #{:en} #{:en}
    #{:en :es} #{:en :es}))

(deftest test-lang
  (are
    [lang uri]
    (= lang (i18n/lang ((bread/handler
                          (load-app {:supported-langs #{:en :es}}))
                        {:uri uri})))

    :en "/" ;; No lang route; Defaults to :en.
    :en "/qwerty" ;; Ditto.
    :en "/en"
    :en "/en/qwerty"
    :es "/es"
    :es "/es/qwerty"
    :en "/fr" ;; Default to :en, since there's no :fr in the database.

    ))

(deftest test-strings-for
  (are
    [strings lang]
    (= strings (i18n/strings (load-app {:supported-langs #{:en :es}}) lang))

    {:one "Uno" :two "Dos"} :es
    {:one "One" :two "Two"} :en
    {} :fr
    {} :de))

(deftest test-strings
  (are
    [strings uri]
    (= strings (i18n/strings ((bread/handler
                                (load-app {:supported-langs #{:en :es}}))
                              {:uri uri})))

    {:one "Uno" :two "Dos"} "/es"
    {:one "One" :two "Two"} "/en"
    ;; These default to :en.
    {:one "One" :two "Two"} "/fr"
    {:one "One" :two "Two"} "/de"))

;; i18n/plugin loads I18n strings for the given language automatically.
(deftest test-add-i18n-query
  (let [app (plugins->loaded [(db/plugin config)
                              (i18n/plugin {:supported-langs #{:en :es}})
                              (query/plugin)
                              naive-plugin])]
    (are
      [strings uri]
      (= strings (get-in ((bread/handler app) {:uri uri})
                         [::bread/data :i18n]))

      {:one "Uno" :two "Dos"} "/es"
      {:one "One" :two "Two"} "/en"
      {:one "One" :two "Two"} "/"
      ;; These default to :en.
      {:one "One" :two "Two"} "/fr"
      {:one "One" :two "Two"} "/de")

    (are
      [lang uri]
      (= lang (get-in ((bread/handler app) {:uri uri})
                      [::bread/data :lang]))

      :es "/es"
      :en "/en"
      :en "/"
      :en "/fr"
      :en "/de")))

(deftest test-fallback
  (let [load-app #(plugins->loaded
                    [(db/plugin config)
                     (i18n/plugin (merge {:supported-langs #{:en :es}} %))
                     (query/plugin)
                     naive-plugin])]
    (are
      [strings fallback-lang]
      (= strings
         (get-in ((bread/handler (load-app fallback-lang)) {:uri "/"})
                 [::bread/data :i18n]))

      {:one "Uno" :two "Dos"} {:fallback-lang :es}
      {:one "One" :two "Two"} {:fallback-lang :en}

      ;; English is the fallback fallback.
      {:one "One" :two "Two"} {}
      {:one "One" :two "Two"} nil

      ;; Nothing in the database for the configured fallback lang.
      {} {:fallback-lang :fr}
      {} {:fallback-lang :de}

      ;; Support disabling fallback lang.
      {} {:fallback-lang false}

      ;; NOTE: when fallback lang is nil, bread/query gets a nil result for the
      ;; strings query, and therefore sets :i18n to false.
      false {:fallback-lang nil})))

(deftest ^:kaocha/skip test-lang-param-config)

(deftest test-internationalize-query
  (let [attrs-map {:menu/items          {:db/cardinality :db.cardinality/many}
                   :translatable/fields {:db/cardinality :db.cardinality/many}
                   :post/children       {:db/cardinality :db.cardinality/many}
                   :post/taxons         {:db/cardinality :db.cardinality/many}
                   :post/_taxons        {:db/cardinality :db.cardinality/many}}]
    (are
      [queries query lang format-fields? compact-fields?]
      (= queries
         (let [app (plugins->loaded
                     [(i18n/plugin {:supported-langs
                                    #{:en :fr :ru :es :de}
                                    :format-fields? format-fields?
                                    :compact-fields? compact-fields?})
                      ;; Set up an ad-hoc plugin to hard-code lang.
                      {:hooks
                       {:hook/lang [{:action/name ::bread/value
                                     :action/value lang}]
                        ::bread/attrs-map [{:action/name ::bread/value
                                            :action/value attrs-map}]}}])
               counter (atom 0)]
           (bread/hook app ::i18n/queries query)))

      ;; No translatable content; noop.
      [{:query/name ::db/query
        :query/key :post
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id :post/slug])]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}]
      {:query/name ::db/query
       :query/key :post
       :query/db ::FAKEDB
       :query/args
       ['{:find [(pull ?e [:db/id :post/slug])]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :whatever
      true ;; this has no effect without translatable fields present
      true ;; ditto

      ;; With :translatable/fields, but still without :field/content
      [{:query/name ::db/query
        :query/key :post
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :post/slug
                            {:translatable/fields [:field/key :field/lang]}])]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}]
      {:query/name ::db/query
       :query/key :post
       :query/db ::FAKEDB
       :query/args
       ['{:find [(pull ?e [:db/id
                           :post/slug
                           {:translatable/fields [:field/key :field/lang]}])]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :whatever
      true ;; this has no effect without translatable fields present
      true ;; ditto

      ;; With deeply nested, mixed implicit & explicit :field/content;
      ;; no formatting; no compaction.
      [{:query/name ::db/query
        :query/key :post-with-taxons-and-field-content
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :post/slug
                            {:translatable/fields [:db/id
                                                   :field/key
                                                   :field/lang
                                                   :field/content]}
                            {:post/_taxons [:taxon/slug
                                            :taxon/taxonomy
                                            {:translatable/fields [*]}]}])]
           :in [$ ?slug ?type]
           :where [[?e :post/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:query/name ::i18n/fields
        :query/key :post-with-taxons-and-field-content
        :query/description  "Process translatable fields."}
       #_#_#_#_
       {:query/name ::i18n/filter-fields
        :query/key :post-with-taxons-and-field-content
        :field/lang :en
        :spath [:translatable/fields]}
       {:query/name ::i18n/filter-fields
        :query/key :post-with-taxons-and-field-content
        :field/lang :en
        :spath [:post/_taxons s/ALL :translatable/fields]}
       {:query/name ::i18n/format
        :query/key :post-with-taxons-and-field-content
        :spath [:translatable/fields]}
       {:query/name ::i18n/format
        :query/key :post-with-taxons-and-field-content
        :spath [:post/_taxons s/ALL :translatable/fields]}]
      {:query/name ::db/query
       :query/key :post-with-taxons-and-field-content
       :query/db ::FAKEDB
       :query/args
       ['{:find [(pull ?e [:db/id
                           :post/slug
                           {:translatable/fields [;; should add id, key, lang
                                                  :field/content]}
                           {:post/_taxons [:taxon/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}])]
          :in [$ ?slug ?type]
          :where [[?e :post/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      false
      false

      ;; With :field/content; no formatting; no compaction.
      [{:query/name ::db/query
        :query/key :post-with-content
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :post/slug
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}])]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}
       {:query/name ::i18n/fields
        :query/key :post-with-content
        :query/description  "Process translatable fields."}
       #_#_
       {:query/name ::i18n/filter-fields
        :query/key :post-with-content
        :field/lang :fr
        :spath [:translatable/fields]}
       {:query/name ::i18n/format
        :query/key :post-with-content
        :spath [:translatable/fields]}]
      {:query/name ::db/query
       :query/key :post-with-content
       :query/db ::FAKEDB
       :query/args
       ['{:find [(pull ?e [:db/id
                           :post/slug
                           {:translatable/fields
                            [:field/key :field/content]}])]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :fr
      false
      false

      ;; With :field/content; no formatting; with compaction.
      [{:query/name ::db/query
        :query/key :post-with-content
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :post/slug
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}])]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}
       {:query/name ::i18n/fields
        :query/key :post-with-content
        :query/description "Process translatable fields."}
       #_#_#_
       {:query/name ::i18n/filter-fields
        :query/key :post-with-content
        :field/lang :fr
        :spath [:translatable/fields]}
       {:query/name ::i18n/format
        :query/key :post-with-content
        :spath [:translatable/fields]}
       {:query/name ::i18n/compact
        :query/key :post-with-content
        :spath [:translatable/fields]}]
      {:query/name ::db/query
       :query/key :post-with-content
       :query/db ::FAKEDB
       :query/args
       ['{:find [(pull ?e [:db/id
                           :post/slug
                           {:translatable/fields
                            [:field/key :field/content]}])]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :fr
      false
      true

      ;; With deeply nested, mixed implicit & explicit :field/content;
      ;; with formatting; two entities to compact.
      [{:query/name ::db/query
        :query/key :post-with-taxons-and-field-content
        :query/db ::FAKEDB
        :query/args
        ['{:find [(pull ?e [:db/id
                            :post/slug
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:taxon/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}])]
           :in [$ ?slug ?type]
           :where [[?e :post/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:query/name ::i18n/fields
        :query/key :post-with-taxons-and-field-content
        :query/description  "Process translatable fields."}
       #_#_#_#_#_#_
       {:query/name ::i18n/filter-fields
        :query/key :post-with-taxons-and-field-content
        :field/lang :en
        :spath [:translatable/fields]}
       {:query/name ::i18n/filter-fields
        :query/key :post-with-taxons-and-field-content
        :field/lang :en
        :spath [:post/taxons s/ALL :translatable/fields]}
       {:query/name ::i18n/format
        :query/key :post-with-taxons-and-field-content
        :spath [:translatable/fields]}
       {:query/name ::i18n/format
        :query/key :post-with-taxons-and-field-content
        :spath [:post/taxons s/ALL :translatable/fields]}
       {:query/name ::i18n/compact
        :query/key :post-with-taxons-and-field-content
        :spath [:translatable/fields]}
       {:query/name ::i18n/compact
        :query/key :post-with-taxons-and-field-content
        :spath [:post/taxons s/ALL :translatable/fields]}]
      {:query/name ::db/query
       :query/key :post-with-taxons-and-field-content
       :query/db ::FAKEDB
       :query/args
       ['{:find [(pull ?e [:db/id
                           :post/slug
                           {:translatable/fields [:field/key :field/content]}
                           {:post/taxons [:taxon/slug
                                          :taxon/taxonomy
                                          {:translatable/fields [*]}]}])]
          :in [$ ?slug ?type]
          :where [[?e :post/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      true
      true

      ;;
      )))

(deftest test-filter-fields-hook
  (is (false? (bread/query {:query/name ::i18n/filter-fields
                            :query/key :the-query-key}
                           {:the-query-key false})))

  (are
    [filtered e lang spath]
    (= filtered (bread/query {:query/name ::i18n/filter-fields
                              :query/key :the-query-key
                              :field/lang lang
                              :spath spath}
                             {:the-query-key e}))

    ;; Single entity with fields.
    {:translatable/fields [{:field/key :a :field/lang :es}
                           {:field/key :b :field/lang :es}]}
    {:translatable/fields [{:field/key :a :field/lang :fr}
                           {:field/key :a :field/lang :es}
                           {:field/key :b :field/lang :fr}
                           {:field/key :b :field/lang :es}]}
    :es [:translatable/fields]

    ;; Nested entity with fields.
    {:menu.item/entity {:translatable/fields [{:field/key :a :field/lang :es}
                                              {:field/key :b :field/lang :es}]}}
    {:menu.item/entity {:translatable/fields [{:field/key :a :field/lang :fr}
                                              {:field/key :a :field/lang :es}
                                              {:field/key :b :field/lang :fr}
                                              {:field/key :b :field/lang :es}]}}
    :es [:menu.item/entity :translatable/fields]

    ;; Nested fields through a has-many relation.
    {:post/_taxons [{:translatable/fields [{:field/key :a :field/lang :es}
                                           {:field/key :b :field/lang :es}]}
                    {:translatable/fields [{:field/key :c :field/lang :es}
                                           {:field/key :d :field/lang :es}]}]}
    {:post/_taxons [{:translatable/fields [{:field/key :a :field/lang :fr}
                                           {:field/key :a :field/lang :es}
                                           {:field/key :b :field/lang :fr}
                                           {:field/key :b :field/lang :es}]}
                    {:translatable/fields [{:field/key :c :field/lang :fr}
                                           {:field/key :c :field/lang :es}
                                           {:field/key :d :field/lang :fr}
                                           {:field/key :d :field/lang :es}]}]}
    :es [:post/_taxons s/ALL :translatable/fields]

    ;;
    ))

(deftest test-format-hook
  (is (false? (bread/query {:query/name ::i18n/format
                            :query/key :the-query-key}
                           {:the-query-key false})))

  (are
    [compacted e spath]
    (= compacted (bread/query {:query/name ::i18n/format
                               :query/key :the-query-key
                               :spath spath}
                              {:the-query-key e}))

    ;; Single entity with fields; default to raw
    {:translatable/fields [{:field/content "A"} {:field/content "B"}]}
    {:translatable/fields [{:field/content "A"} {:field/content "B"}]}
    [:translatable/fields]

    ;; Single entity with fields with explicit :raw format.
    ;; NOTE: There is no multimethod for :raw, this just uses :default.
    {:translatable/fields [{:field/format :raw :field/content "A"}
                           {:field/format :raw :field/content "B"}]}
    {:translatable/fields [{:field/format :raw :field/content "A"}
                           {:field/format :raw :field/content "B"}]}
    [:translatable/fields]

    ;; Single entity with fields in EDN format.
    {:translatable/fields [{:field/format :edn :field/content "A"}
                           {:field/format :edn :field/content "B"}]}
    {:translatable/fields [{:field/format :edn :field/content (pr-str "A")}
                           {:field/format :edn :field/content (pr-str "B")}]}
    [:translatable/fields]

    ;; Nested entity with fields.
    {:menu.item/entity {:translatable/fields
                        [{:field/format :edn :field/content "A"}
                         {:field/format :edn :field/content "B"}]}}
    {:menu.item/entity {:translatable/fields
                        [{:field/format :edn :field/content (pr-str "A")}
                         {:field/format :edn :field/content (pr-str "B")}]}}
    [:menu.item/entity :translatable/fields]

    ;; Entity with nested fields through a has-many relation.
    {:post/children [{:translatable/fields
                      [{:field/format :edn :field/content "A"}
                       {:field/format :edn :field/content "B"}]}]}
    {:post/children [{:translatable/fields
                      [{:field/format :edn :field/content (pr-str "A")}
                       {:field/format :edn :field/content (pr-str "B")}]}]}
    [:post/children s/ALL :translatable/fields]

    ;;
    ))

(deftest test-compact-hook
  (is (false? (bread/query {:query/name ::i18n/compact
                            :query/key :the-query-key}
                           {:the-query-key false})))

  (are
    [compacted e spath]
    (= compacted (bread/query {:query/name ::i18n/compact
                               :query/key :the-query-key
                               :spath spath}
                              {:the-query-key e}))

    ;; Single entity with fields.
    {:translatable/fields {:a "A" :b "B"}}
    {:translatable/fields [{:field/key :a :field/content "A"}
                           {:field/key :b :field/content "B"}]}
    [:translatable/fields]

    ;; Nested entity with fields.
    {:menu.item/entity {:translatable/fields {:a "A" :b "B"}}}
    {:menu.item/entity {:translatable/fields
                        [{:field/key :a :field/content "A"}
                         {:field/key :b :field/content "B"}]}}
    [:menu.item/entity :translatable/fields]

    ;; Entity with nested fields through a has-many relation.
    {:post/children [{:translatable/fields {:a "A" :b "B"}}]}
    {:post/children [{:translatable/fields
                      [{:field/key :a :field/content "A"}
                       {:field/key :b :field/content "B"}]}]}
    [:post/children s/ALL :translatable/fields]

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
