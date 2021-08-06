(ns systems.bread.alpha.datahike-query-test
  (:require
    [clojure.test :refer [are deftest is]]
    [kaocha.repl :as k]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded use-datastore]]))

(def config {:datastore/type :datahike
             :store {:backend :mem :id "expand-db"}
             :datastore/initial-txns
             [;; init simplified schema
              {:db/ident :post/slug
               :db/valueType :db.type/string
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :post/parent
               :db/valueType :db.type/ref
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :post/fields
               :db/valueType :db.type/ref
               :db/index true
               :db/cardinality :db.cardinality/many}
              {:db/ident :field/key
               :db/valueType :db.type/keyword
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :field/lang
               :db/valueType :db.type/keyword
               :db/index true
               :db/cardinality :db.cardinality/one}
              {:db/ident :field/content
               :db/valueType :db.type/string
               :db/index true
               :db/cardinality :db.cardinality/one}

              ;; init post content
              {:db/id 100
               :post/slug "parent-post"
               :post/fields [{:field/key :stuff
                              :field/lang :en
                              :field/content "hello"}
                             {:field/key :thingy
                              :field/lang :en
                              :field/content "thing"}
                             {:field/key :stuff
                              :field/lang :fr
                              :field/content "bonjour"}
                             {:field/key :thingy
                              :field/lang :fr
                              :field/content "chose"}]}]})

(use-datastore :each config)

(deftest test-datahike-query

  (let [app (plugins->loaded [(store/plugin config) (query/plugin)])
        db (store/datastore app)]
    (are
      [data queries]
      (= data (-> app
                  (assoc ::bread/queries queries
                         ::bread/resolver {:resolver/type :resolver.type/page
                                           :resolver/not-found?
                                           #(nil? (:post %))})
                  (bread/hook :hook/expand)
                  ::bread/data))

       ;; Querying for a non-existent post
       {:post nil :not-found? true}
       [[:post db '{:find [(pull ?e [:post/slug {:post/fields
                                                 [:field/key :field/lang]}]) .]
                    :in [$ ?slug]
                    :where [[?e :post/slug ?slug]]}
         "non-existent-slug"]]

       ;; Querying for a non-existent post and its fields
       {:post nil :post/fields nil :not-found? true}
       [[:post db '{:find [(pull ?e [:post/slug {:post/fields
                                                 [:field/key :field/lang]}]) .]
                    :in [$ ?slug]
                    :where [[?e :post/slug ?slug]]}
         "non-existent-slug"]
        [:post/fields db '{:find [(pull ?e [:field/key :field/content])]
                           :in [$ ?p ?lang]
                           :where [[?p :post/fields ?e]
                                   [?e :field/lang ?lang]]}
         [::bread/data :post :db/id]
         :en]]

       {:post {:post/slug "parent-post"
               :post/fields [{:field/key :stuff :field/lang :en}
                             {:field/key :thingy :field/lang :en}
                             {:field/key :stuff :field/lang :fr}
                             {:field/key :thingy :field/lang :fr}
                             ]}
        :not-found? false}
       [[:post db '{:find [(pull ?e [:post/slug {:post/fields
                                                 [:field/key :field/lang]}]) .]
                    :in [$]
                    :where [[?e :post/slug ?slug]]}]]

       ;; with query input args (slug)
       {:post {:post/slug "parent-post"
               :post/fields [{:field/key :stuff :field/lang :en}
                             {:field/key :thingy :field/lang :en}
                             {:field/key :stuff :field/lang :fr}
                             {:field/key :thingy :field/lang :fr}]}
        :not-found? false}
       [[:post db '{:find [(pull ?e [:post/slug {:post/fields
                                                 [:field/key
                                                  :field/lang]}]) .]
                    :in [$ ?slug]
                    :where [[?e :post/slug ?slug]]}
         "parent-post"]]

       ;; with explicit input args (for i18n)
       {:post {:post/slug "parent-post"
               :post/fields [[{:field/key :thingy
                               :field/content "thing"}]
                             [{:field/key :stuff
                              :field/content "hello"}]]}
        :not-found? false}
       [[:post db '{:find [(pull ?e [:post/slug]) .]
                    :in [$ ?slug]
                    :where [[?e :post/slug ?slug]]} "parent-post"]
        [:post/fields db '{:find [(pull ?e [:field/key :field/content])]
                           :in [$ ?p ?lang]
                           :where [[?p :post/fields ?e]
                                   [?e :field/lang ?lang]]}
         100
         :en]]

       ;; deriving input args from previous data (for i18n)
       {:post {:db/id 100 :post/slug "parent-post"
               :post/fields [[{:field/key :thingy
                               :field/content "thing"}]
                             [{:field/key :stuff
                               :field/content "hello"}]]}
        :not-found? false}
       [[:post db '{:find [(pull ?e [:db/id :post/slug]) .]
                    :in [$ ?slug]
                    :where [[?e :post/slug ?slug]]} "parent-post"]
        [:post/fields db '{:find [(pull ?e [:field/key :field/content])]
                           :in [$ ?p ?lang]
                           :where [[?p :post/fields ?e]
                                   [?e :field/lang ?lang]]}
         [::bread/data :post :db/id]
         :en]])))

(comment
  (k/run))
