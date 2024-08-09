(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [com.rpl.specter :as s]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.expansion :as expansion]
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
    {:field/lang lang :slugs slugs}))

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
                              (expansion/plugin)
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
                      [::bread/data :field/lang]))

      :es "/es"
      :en "/en"
      :en "/"
      :en "/fr"
      :en "/de")))

(deftest test-fallback
  (let [load-app #(plugins->loaded
                    [(db/plugin config)
                     (i18n/plugin (merge {:supported-langs #{:en :es}} %))
                     (expansion/plugin)
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

      ;; NOTE: when fallback lang is nil, bread/expand gets a nil result for the
      ;; strings query, and therefore sets :i18n to false.
      false {:fallback-lang nil})))

(deftest ^:kaocha/skip test-lang-param-config)

(deftest test-internationalize-query
  (let [attrs-map {:menu/items          {:db/cardinality :db.cardinality/many}
                   :translatable/fields {:db/cardinality :db.cardinality/many}
                   :thing/children      {:db/cardinality :db.cardinality/many}
                   :post/taxons         {:db/cardinality :db.cardinality/many}
                   :post/_taxons        {:db/cardinality :db.cardinality/many}}]
    (are
      [expansions query lang format-fields? compact-fields?]
      (= expansions
         (let [app (plugins->loaded
                     [(i18n/plugin {:supported-langs
                                    #{:en :fr :ru :es :de}
                                    :format-fields? format-fields?
                                    :compact-fields? compact-fields?})
                      ;; Set up an ad-hoc plugin to hard-code lang.
                      {:hooks
                       {::i18n/lang [{:action/name ::bread/value
                                      :action/value lang}]
                        ::bread/attrs-map [{:action/name ::bread/value
                                            :action/value attrs-map}]}}])
               counter (atom 0)]
           (bread/hook app ::i18n/expansions query)))

      ;; No translatable content; noop.
      [{:expansion/name ::db/query
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id :thing/slug])]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}]
      {:expansion/name ::db/query
       :expansion/key :post
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id :thing/slug])]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :whatever
      true ;; this has no effect without translatable fields present
      true ;; ditto

      ;; With :translatable/fields, but still without :field/content
      [{:expansion/name ::db/query
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields [:field/key :field/lang]}]) .]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}]
      {:expansion/name ::db/query
       :expansion/key :post
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:translatable/fields [:field/key :field/lang]}]) .]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :whatever
      true ;; this has no effect without translatable fields present
      true ;; ditto

      ;; With deeply nested, mixed implicit & explicit :field/content;
      ;; no formatting; no compaction; querying many.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields [:db/id
                                                   :field/key
                                                   :field/lang
                                                   :field/content]}
                            {:post/_taxons [:thing/slug
                                            :taxon/taxonomy
                                            {:translatable/fields [*]}]}])]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? false
        :compact? false
        :recur-attrs #{}
        :spaths [[s/ALL s/ALL :translatable/fields]
                 [s/ALL s/ALL :post/_taxons s/ALL :translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:translatable/fields [;; should add id, key, lang
                                                  :field/content]}
                           {:post/_taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}])]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      false
      false

      ;; With deeply nested, mixed implicit & explicit :field/content;
      ;; no formatting; no compaction.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields [:db/id
                                                   :field/key
                                                   :field/lang
                                                   :field/content]}
                            {:post/_taxons [:thing/slug
                                            :taxon/taxonomy
                                            {:translatable/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? false
        :compact? false
        :recur-attrs #{}
        :spaths [[:translatable/fields]
                 [:post/_taxons s/ALL :translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:translatable/fields [;; should add id, key, lang
                                                  :field/content]}
                           {:post/_taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      false
      false

      ;; With :field/content; no formatting; no compaction.
      [{:expansion/name ::db/query
        :expansion/key :post-with-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-content
        :expansion/description  "Process translatable fields."
        :field/lang :fr
        :format? false
        :compact? false
        :recur-attrs #{}
        :spaths [[:translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:translatable/fields
                            [:field/key :field/content]}]) .]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :fr
      false
      false

      ;; With :field/content; no formatting; with compaction.
      [{:expansion/name ::db/query
        :expansion/key :post-with-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-content
        :expansion/description "Process translatable fields."
        :field/lang :fr
        :format? false
        :compact? true
        :recur-attrs #{}
        :spaths [[:translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:translatable/fields
                            [:field/key :field/content]}]) .]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :post.type/page]}
      :fr
      false
      true

      ;; With deeply nested, mixed implicit & explicit :field/content;
      ;; with formatting; two entities to compact.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}
        :spaths [[:translatable/fields]
                 [:post/taxons s/ALL :translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:translatable/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          {:translatable/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      true
      true

      ;; All the things, with a recursive spec that can be disregarded because
      ;; it doesn't coincide with any translatable fields.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/children ...}
                            {:some/relation [{:hierarchical/stuff ...}]}
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        ;; "disregarded" means it doesn't show up here:
        :recur-attrs #{:thing/children}
        :spaths [[:translatable/fields]
                 [:post/taxons s/ALL :translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/children ...}
                           {:some/relation [;; We can safely disregard this
                                            ;; recursive binding.
                                            {:hierarchical/stuff ...}]}
                           {:translatable/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          {:translatable/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      true
      true

      ;; All the things, plus a recursive spec.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/children ...}
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:translatable/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{:thing/children}
        :spaths [[:translatable/fields]
                 [:post/taxons s/ALL :translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/children ...}
                           {:translatable/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          {:translatable/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      true
      true

      ;; All the things, plus TWO recursive specs including a nested one.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/children 3}
                            {:translatable/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/_children ...}
                                           {:translatable/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :post.type/page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{:thing/children :thing/_children}
        :spaths [[:translatable/fields]
                 [:post/taxons s/ALL :translatable/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/children 3}
                           {:translatable/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          ;; We might do this e.g. to get
                                          ;; a heirarchical URL
                                          {:thing/_children ...}
                                          {:translatable/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :post.type/page]}
      :en
      true
      true

      ;;
      )))

(deftest test-fields-hook
  (are
    [entity query data]
    (= entity (bread/expand query data))

    false {:expansion/name ::i18n/fields :expansion/key :the-key} {:the-key false}

    ;; With direct fields; no formatting; no compaction.
    {:translatable/fields [{:field/key :opposite-of-occupied
                            :field/format :edn
                            :field/content (pr-str "عكس المحتل")
                            :field/lang :ar}
                           {:field/key :not-vacant
                            :field/format :edn
                            :field/content (pr-str "ليس الشاغر")
                            :field/lang :ar}]}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :ar
     :format? false
     :compact? false
     :recur-attrs #{}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{:field/key :opposite-of-occupied
                 :field/format :edn
                 :field/content (pr-str "عكس المحتل")
                 :field/lang :ar}
                {:field/key :opposite-of-occupied
                 :field/format :edn
                 :field/content (pr-str "The opposite of occupied")
                 :field/lang :en}
                {:field/key :not-vacant
                 :field/format :edn
                 :field/content (pr-str "ليس الشاغر")
                 :field/lang :ar}
                {:field/key :not-vacant
                 :field/format :edn
                 :field/content (pr-str "is not vacant")
                 :field/lang :en}]}}

    ;; With direct fields; EDN formatting; no compaction.
    {:translatable/fields [{:field/key :from-the-river
                            :field/format :edn
                            :field/content "מהנהר"
                            :field/lang :he}
                           {:field/key :to-the-sea
                            :field/format :edn
                            :field/content "לים"
                            :field/lang :he}]}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? false
     :recur-attrs #{}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "מהנהר")
                 :field/lang :he}
                {:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "From the river")
                 :field/lang :en}
                {:field/key :to-the-sea
                 :field/format :edn
                 :field/content (pr-str "לים")
                 :field/lang :he}
                {:field/key :to-the-sea
                 :field/format :edn
                 :field/content (pr-str "to the sea")
                 :field/lang :en}]}}

    ;; With direct fields; EDN formatting; with compaction.
    {:translatable/fields {:uri "/es/the-slug"}}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :es
     :format? true
     :compact? true
     :recur-attrs #{}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{:field/key :uri
                 :field/format ::i18n/uri
                 :field/content (pr-str [:field/lang "the-slug"])}]}}

    ;; With direct fields; no formatting; compactions.
    {:translatable/fields {:from-the-river (pr-str "מהנהר")
                           :to-the-sea (pr-str "לים")}}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? false
     :compact? true
     :recur-attrs #{}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "מהנהר")
                 :field/lang :he}
                {:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "From the river")
                 :field/lang :en}
                {:field/key :to-the-sea
                 :field/format :edn
                 :field/content (pr-str "לים")
                 :field/lang :he}
                {:field/key :to-the-sea
                 :field/format :edn
                 :field/content (pr-str "to the sea")
                 :field/lang :en}]}}

    ;; With direct fields; EDN formatting; compactions.
    {:translatable/fields {:from-the-river "מהנהר" :to-the-sea "לים"}}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? true
     :recur-attrs #{}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "מהנהר")
                 :field/lang :he}
                {:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "From the river")
                 :field/lang :en}
                {:field/key :to-the-sea
                 :field/format :edn
                 :field/content (pr-str "לים")
                 :field/lang :he}
                {:field/key :to-the-sea
                 :field/format :edn
                 :field/content (pr-str "to the sea")
                 :field/lang :en}]}}

    ;; With direct fields; EDN formatting; compactions; recursive data.
    {:translatable/fields {:from-the-river "מהנהר"}
     :thing/children [{:translatable/fields {:to-the-sea "לים"}}]}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? true
     :recur-attrs #{:thing/children}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "מהנהר")
                 :field/lang :he}
                {:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "From the river")
                 :field/lang :en}]
               :thing/children
               [{:translatable/fields
                 [{:field/key :to-the-sea
                   :field/format :edn
                   :field/content (pr-str "לים")
                   :field/lang :he}
                  {:field/key :to-the-sea
                   :field/format :edn
                   :field/content (pr-str "to the sea")
                   :field/lang :en}]}]}}

    ;; With direct fields; raw formatting; compactions; universal fields.
    {:translatable/fields {:uri "/abc"}
     :thing/children [{:translatable/fields {:uri "/def"}}]}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? true
     :recur-attrs #{:thing/children}
     :spaths [[:translatable/fields]]}
    {:the-key {:translatable/fields
               [{;; no :field/lang
                 :field/key :uri
                 :field/content "/abc"}]
               :thing/children
               [{:translatable/fields
                 [{;; no :field/lang
                   :field/key :uri
                   :field/content "/def"}]}]}}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
