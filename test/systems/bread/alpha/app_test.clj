(ns ^{:doc "High-level abstractions for composing a Bread app"
      :author "Coby Tamayo"}
  systems.bread.alpha.app-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [use-datastore
                                              map->route-plugin]])
  (:import
    [java.util UUID]))

(def config {:datastore/type :datahike
             :store {:backend :mem
                     :id "breadbox-db"}
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
              #_
              {:db/ident :post/status
               :db/valueType :db.type/keyword
               :db/cardinality :db.cardinality/one}
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
              #:post{:type :post.type/page
                     :uuid (UUID/randomUUID)
                     :title "Home Page"
                     :slug ""
                     :fields #{{:field/key :title
                                :field/lang :en
                                :field/content (prn-str "Home Page")}
                               {:field/key :simple
                                :field/lang :fr
                                :field/content (prn-str "Page D'Accueil")}
                               {:field/key :simple
                                :field/lang :en
                                :field/content (prn-str {:hello "Hi!"})}
                               {:field/key :simple
                                :field/lang :fr
                                :field/content (prn-str {:hello "Allo!"})}}
                     :status :post.status/published}
              #:post{:type :post.type/page
                     :uuid (UUID/randomUUID)
                     :title "Parent Page"
                     :slug "parent-page"
                     :status :post.status/published
                     :fields #{}}
              #:post{:type :post.type/page
                     :uuid (UUID/randomUUID)
                     :title "Child Page OLD TITLE"
                     :slug "child-page"
                     :status :post.status/published
                     ;; TODO fix this hard-coded eid somehow...
                     :parent 47 ;; NOTE: don't do this :P
                     :fields #{{:field/key :title
                                :field/lang :en
                                :field/content (prn-str "Child Page")}
                               {:field/key :title
                                :field/lang :fr
                                :field/content (prn-str "La Page Enfant")}
                               {:field/key :simple
                                :field/lang :en
                                :field/content
                                (prn-str {:hello "Hello"
                                          :body "Lorem ipsum dolor sit amet"
                                          :goodbye "Bye!"
                                          :img-url "https://via.placeholder.com/300"})}
                               {:field/key :simple
                                :field/lang :fr
                                :field/content
                                (prn-str {:hello "Bonjour"
                                          :body "Lorem ipsum en francais"
                                          :goodbye "Salut"
                                          :img-url "https://via.placeholder.com/300"})}
                               {:field/key :flex-content
                                :field/lang :en
                                :field/content (prn-str {:todo "TODO"})}}}
              ]})

(use-datastore :each config)

(component/defc home [{:keys [post]}]
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:main
     [:h1 title]
     [:p (:hello simple)]]))

(k/run (deftest test-app-lifecycle

  (testing "it does a thing"
    (let [routes {"/en"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component home
                   :route/params {:lang "en"}}
                  }
          app (bread/app {:plugins [(store/plugin config)
                                    (route/plugin)
                                    (resolver/plugin)
                                    (query/plugin)
                                    (component/plugin)
                                    (map->route-plugin routes)]})
          handler (bread/load-handler app)]
      (is (= {:body [:main
                     [:h1 "Home Page"]
                     [:p "Hi!"]]}
             (select-keys (handler {:uri "/en"}) [:body])))))))

(comment
  (k/run))
