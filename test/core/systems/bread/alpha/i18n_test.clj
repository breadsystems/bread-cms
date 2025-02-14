(ns systems.bread.alpha.i18n-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [com.rpl.specter :as s]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              naive-plugin
                                              naive-router
                                              use-db]]))

(def config {:db/type :datahike
             :store {:backend :mem
                     :id "test-i18n-db"}
             :db/initial-txns
             ;; TODO test locales e.g. en-gb
             [{:thing/fields #{{:field/key :one
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

(defn- load-app [i18n-config]
  (plugins->loaded [(db/plugin config)
                    (i18n/plugin i18n-config)
                    naive-plugin]))


(deftest test-supported-langs
  (are
    [langs supported]
    (= langs (let [handler (-> [(db/plugin config)
                                (i18n/plugin {:supported-langs supported})
                                (route/plugin {:router (naive-router)})]
                               plugins->loaded bread/handler)]
               (i18n/supported-langs (handler {:uri ""}))))

    nil nil
    #{} #{}
    #{:en} #{:en}
    #{:en :es} #{:en :es}))

(deftest test-lang
  (are
    [lang uri]
    (= lang (let [handler (-> [(i18n/plugin {:supported-langs #{:en :es}
                                             :query-global-strings? false})
                               (route/plugin {:router (naive-router)})]
                              plugins->loaded bread/handler)]
              (i18n/lang (handler {:uri uri}))))

    :en "/" ;; No lang route; Defaults to :en.
    :en "/qwerty" ;; Ditto.
    :en "/en"
    :en "/en/qwerty"
    :es "/es"
    :es "/es/qwerty"
    :en "/fr" ;; Default to :en, since :fr is not in supported-langs
    :en "/de" ;; Default to :en, since :de is not in supported-langs

    ))

(deftest test-strings-for
  (are
    [strings lang]
    (= strings (let [handler (-> [(db/plugin config)
                                  (i18n/plugin {:supported-langs #{:en :es}})
                                  (route/plugin {:router (naive-router)})]
                                 plugins->loaded bread/handler)]
                 (i18n/strings (handler {:uri ""}) lang)))

    {:one "Uno" :two "Dos"} :es
    {:one "One" :two "Two"} :en
    {} :fr
    {} :de))

(deftest test-strings
  (are
    [strings uri]
    (= strings (let [handler (-> [(db/plugin config)
                                  (i18n/plugin {:supported-langs #{:en :es}})
                                  (route/plugin {:router (naive-router)})]
                                 plugins->loaded bread/handler)]
                 (i18n/strings (handler {:uri uri}))))

    {:one "Uno" :two "Dos"} "/es"
    {:one "One" :two "Two"} "/en"
    ;; These default to :en.
    {:one "One" :two "Two"} "/fr"
    {:one "One" :two "Two"} "/de"))

(deftest test-global-strings-hook
  (are
    [strings config req extra-plugin]
    (= strings (let [i18n-config (merge config {:query-global-strings? false
                                                :query-lang? false
                                                :supported-langs #{:fr :es :en}})]
                 (-> (plugins->loaded [(expansion/plugin)
                                       (route/plugin {:router (naive-router)})
                                       (i18n/plugin i18n-config)
                                       extra-plugin])
                     (merge req)
                     (bread/hook ::bread/route)
                     (bread/hook ::bread/dispatch)
                     (bread/hook ::bread/expand)
                     (get-in [::bread/data :i18n]))))

    ;; Passing nil, strings exiplicitly disabled.
    nil {:global-strings nil} {:uri "/en/_"} nil

    ;; Passing map, strings exiplicitly disabled.
    nil {:global-strings nil} {:uri "/en/_"} nil
    nil {:global-strings nil} {:uri "/en/_"} nil
    nil {:global-strings nil} {:uri "/es/_"} nil
    nil {:global-strings nil} {:uri "/fr/_"} nil

    ;; Any logical false works to disable the setting.
    nil {:global-strings false} {:uri "/en/_"} nil
    nil {:global-strings false} {:uri "/es/_"} nil
    nil {:global-strings false} {:uri "/fr/_"} nil

    ;; No global-strings hook is called when setting is disabled.
    nil {:global-strings false} {:uri "/en/_"}
    {:hooks {::i18n/global-strings
             [{:action/name ::bread/value
               :action/value {:some :data}}]}}

    ;; When enabled, it should pick the right language for the request.
    ;; Here the current request is for Español...
    {:one "Uno"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/es/_"}
    nil

    ;; ...and here, for Français...
    {:one "Un"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/fr/_"}
    nil

    ;; ...and for English.
    {:one "One"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/en/_"}
    nil

    ;; For an unsupported lang, it defaults back to fallback-lang.
    {:one "One"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/nah/_"}
    nil

    ;; Test that ::global-strings gets called when building the expansion.
    ;; This also tests the ::merge-global-strings convenience hook.
    {:one "Uno" :my/string "¡Hola!"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/es/_"}
    {:hooks
     {::i18n/global-strings
      [{:action/name ::i18n/merge-global-strings
        :strings {:es {:my/string "¡Hola!"}
                  :en {:my/string "Hello!"}}}]}}

    ;; This time with English...
    {:one "One" :my/string "Hello!"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/en/_"}
    {:hooks
     {::i18n/global-strings
      [{:action/name ::i18n/merge-global-strings
        :strings {:es {:my/string "¡Hola!"}
                  :en {:my/string "Hello!"}}}]}}

    ;; Merging unsupported or irrelevant languages should have no effect.
    {:one "Un"}
    {:global-strings {:es {:one "Uno"} :fr {:one "Un"} :en {:one "One"}}}
    {:uri "/fr/_"}
    {:hooks
     {::i18n/global-strings
      [{:action/name ::i18n/merge-global-strings
        :strings {:jabbertalky
                  {:one "TWO THE VORPAL BLADE WENT SKICKER-SNACK!"}}}]}}

    ;;
    ))

;; i18n/plugin loads I18n strings for the given language automatically.
(deftest test-add-i18n-query
  (let [app (plugins->loaded [(db/plugin config)
                              (i18n/plugin {:supported-langs #{:en :es}})
                              (expansion/plugin)
                              (route/plugin {:router (naive-router)})])]
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
  (are
    [strings i18n-config]
    (= strings
       (let [handler (-> [(db/plugin config)
                          (i18n/plugin (merge {:supported-langs #{:en :es}}
                                              i18n-config))
                          (expansion/plugin)
                          (route/plugin {:router (naive-router)})]
                         plugins->loaded bread/handler)]
         (get-in (handler {:uri "/"}) [::bread/data :i18n])))

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
    false {:fallback-lang nil}))

(deftest ^:kaocha/skip test-lang-param-config)

(deftest test-internationalize-query
  (let [attrs-map {:menu/items     {:db/cardinality :db.cardinality/many}
                   :thing/fields   {:db/cardinality :db.cardinality/many}
                   :thing/children {:db/cardinality :db.cardinality/many}
                   :post/taxons    {:db/cardinality :db.cardinality/many}
                   :post/_taxons   {:db/cardinality :db.cardinality/many}}]
    (are
      [expected expansion lang format-fields? compact-fields?]
      (= expected
         (let [app (plugins->loaded
                     [(i18n/plugin {:supported-langs
                                    #{:en :fr :ru :es :de}
                                    :query-global-strings? false
                                    :format-fields? format-fields?
                                    :compact-fields? compact-fields?})
                      (route/plugin {:router (naive-router)})
                      ;; Set up an ad-hoc plugin to hard-code lang.
                      {:hooks
                       {::i18n/lang [{:action/name ::bread/value
                                      :action/value lang}]
                        ::bread/attrs-map [{:action/name ::bread/value
                                            :action/value attrs-map}]}}])
               counter (atom 0)]
           (bread/hook app ::i18n/expansions expansion)))

      ;; No translatable content; noop.
      [{:expansion/name ::db/query
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id :thing/slug])]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :page]}]
      {:expansion/name ::db/query
       :expansion/key :post
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id :thing/slug])]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :page]}
      :whatever
      true ;; this has no effect without translatable fields present
      true ;; ditto

      ;; With :thing/fields, but still without :field/content
      [{:expansion/name ::db/query
        :expansion/key :post
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [:field/key :field/lang]}]) .]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :page]}]
      {:expansion/name ::db/query
       :expansion/key :post
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields [:field/key :field/lang]}]) .]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :page]}
      :whatever
      true ;; this has no effect without translatable fields present
      true ;; ditto

      ;; Explicitly opting out with :expansion/i18n? false
      [{:expansion/i18n? false
        :expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [;; should add id, key, lang
                                            :field/content]}
                            {:post/_taxons [:thing/slug
                                            :taxon/taxonomy
                                            {:thing/fields [*]}]}])]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}]
      {:expansion/i18n? false
       :expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields [;; should add id, key, lang
                                           :field/content]}
                           {:post/_taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/fields [*]}]}])]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
      :en
      false
      false

      ;; With deeply nested, mixed implicit & explicit :field/content;
      ;; no formatting; no compaction; querying many.
      [{:expansion/name ::db/query
        :expansion/key :post-with-taxons-and-field-content
        :expansion/db ::FAKEDB
        :expansion/args
        ['{:find [(pull ?e [:db/id
                            :thing/slug
                            {:thing/fields [:db/id
                                            :field/key
                                            :field/lang
                                            :field/content]}
                            {:post/_taxons [:thing/slug
                                            :taxon/taxonomy
                                            {:thing/fields [*]}]}])]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? false
        :compact? false
        :recur-attrs #{}
        :spaths [[s/ALL s/ALL :thing/fields]
                 [s/ALL s/ALL :post/_taxons s/ALL :thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields [;; should add id, key, lang
                                           :field/content]}
                           {:post/_taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/fields [*]}]}])]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
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
                            {:thing/fields [:db/id
                                            :field/key
                                            :field/lang
                                            :field/content]}
                            {:post/_taxons [:thing/slug
                                            :taxon/taxonomy
                                            {:thing/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? false
        :compact? false
        :recur-attrs #{}
        :spaths [[:thing/fields]
                 [:post/_taxons s/ALL :thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields [;; should add id, key, lang
                                           :field/content]}
                           {:post/_taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
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
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-content
        :expansion/description  "Process translatable fields."
        :field/lang :fr
        :format? false
        :compact? false
        :recur-attrs #{}
        :spaths [[:thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields
                            [:field/key :field/content]}]) .]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :page]}
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
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}]) .]
           :in [$ ?type]
           :where [[?e :post/type ?type]]}
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-content
        :expansion/description "Process translatable fields."
        :field/lang :fr
        :format? false
        :compact? true
        :recur-attrs #{}
        :spaths [[:thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields
                            [:field/key :field/content]}]) .]
          :in [$ ?type]
          :where [[?e :post/type ?type]]}
        :page]}
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
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{}
        :spaths [[:thing/fields]
                 [:post/taxons s/ALL :thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          {:thing/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
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
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        ;; "disregarded" means it doesn't show up here:
        :recur-attrs #{:thing/children}
        :spaths [[:thing/fields]
                 [:post/taxons s/ALL :thing/fields]]}]
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
                           {:thing/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          {:thing/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
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
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{:thing/children}
        :spaths [[:thing/fields]
                 [:post/taxons s/ALL :thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/children ...}
                           {:thing/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          {:thing/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
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
                            {:thing/fields
                             [:db/id :field/lang :field/key :field/content]}
                            {:post/taxons [:thing/slug
                                           :taxon/taxonomy
                                           {:thing/_children ...}
                                           {:thing/fields [*]}]}]) .]
           :in [$ ?slug ?type]
           :where [[?e :thing/slug ?slug]
                   [?e :post/type ?type]]}
         "my-post"
         :page]}
       {:expansion/name ::i18n/fields
        :expansion/key :post-with-taxons-and-field-content
        :expansion/description  "Process translatable fields."
        :field/lang :en
        :format? true
        :compact? true
        :recur-attrs #{:thing/children :thing/_children}
        :spaths [[:thing/fields]
                 [:post/taxons s/ALL :thing/fields]]}]
      {:expansion/name ::db/query
       :expansion/key :post-with-taxons-and-field-content
       :expansion/db ::FAKEDB
       :expansion/args
       ['{:find [(pull ?e [:db/id
                           :thing/slug
                           {:thing/children 3}
                           {:thing/fields [:field/key :field/content]}
                           {:post/taxons [:thing/slug
                                          :taxon/taxonomy
                                          ;; We might do this e.g. to get
                                          ;; a heirarchical URL
                                          {:thing/_children ...}
                                          {:thing/fields [*]}]}]) .]
          :in [$ ?slug ?type]
          :where [[?e :thing/slug ?slug]
                  [?e :post/type ?type]]}
        "my-post"
        :page]}
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
    {:thing/fields [{:field/key :opposite-of-occupied
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
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
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
    {:thing/fields [{:field/key :from-the-river
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
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
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
    {:thing/fields {:uri "/es/the-slug"}}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :es
     :format? true
     :compact? true
     :recur-attrs #{}
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
               [{:field/key :uri
                 :field/format ::i18n/uri
                 :field/content (pr-str [:field/lang "the-slug"])}]}}

    ;; With direct fields; no formatting; compactions.
    {:thing/fields {:from-the-river (pr-str "מהנהר")
                    :to-the-sea (pr-str "לים")}}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? false
     :compact? true
     :recur-attrs #{}
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
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
    {:thing/fields {:from-the-river "מהנהר" :to-the-sea "לים"}}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? true
     :recur-attrs #{}
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
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
    {:thing/fields {:from-the-river "מהנהר"}
     :thing/children [{:thing/fields {:to-the-sea "לים"}}]}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? true
     :recur-attrs #{:thing/children}
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
               [{:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "מהנהר")
                 :field/lang :he}
                {:field/key :from-the-river
                 :field/format :edn
                 :field/content (pr-str "From the river")
                 :field/lang :en}]
               :thing/children
               [{:thing/fields
                 [{:field/key :to-the-sea
                   :field/format :edn
                   :field/content (pr-str "לים")
                   :field/lang :he}
                  {:field/key :to-the-sea
                   :field/format :edn
                   :field/content (pr-str "to the sea")
                   :field/lang :en}]}]}}

    ;; With direct fields; raw formatting; compactions; universal fields.
    {:thing/fields {:uri "/abc"}
     :thing/children [{:thing/fields {:uri "/def"}}]}
    {:expansion/name ::i18n/fields
     :expansion/key :the-key
     :field/lang :he
     :format? true
     :compact? true
     :recur-attrs #{:thing/children}
     :spaths [[:thing/fields]]}
    {:the-key {:thing/fields
               [{;; no :field/lang
                 :field/key :uri
                 :field/content "/abc"}]
               :thing/children
               [{:thing/fields
                 [{;; no :field/lang
                   :field/key :uri
                   :field/content "/def"}]}]}}

    ;;
    ))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
