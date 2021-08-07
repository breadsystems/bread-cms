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

(def parent-uuid (UUID/randomUUID))

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
                     :slug ""
                     :fields #{{:field/key :title
                                :field/lang :en
                                :field/content (prn-str "Home Page")}
                               {:field/key :title
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
                     :uuid parent-uuid
                     :slug "parent-page"
                     :status :post.status/published
                     :fields #{{:field/key :title
                                :field/lang :en
                                :field/content (prn-str "Parent Page")}
                               {:field/key :title
                                :field/lang :fr
                                :field/content (prn-str "La Page Parent")}
                               {:field/key :simple
                                :field/lang :en
                                :field/content
                                (prn-str {:hello "Hello from parent"})}
                               {:field/key :simple
                                :field/lang :fr
                                :field/content
                                (prn-str {:hello "Bonjour de parent"})}
                               }}
              #:post{:type :post.type/page
                     :uuid (UUID/randomUUID)
                     :slug "child-page"
                     :status :post.status/published
                     :parent [:post/uuid parent-uuid]
                     :fields #{{:field/key :title
                                :field/lang :en
                                :field/content (prn-str "Child Page")}
                               {:field/key :title
                                :field/lang :fr
                                :field/content (prn-str "La Page Enfant")}
                               {:field/key :simple
                                :field/lang :en
                                :field/content
                                (prn-str {:hello "Hello from child"})}
                               {:field/key :simple
                                :field/lang :fr
                                :field/content
                                (prn-str {:hello "Bonjour d'enfant"})}
                               }}
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

(component/defc page [{:keys [post]}]
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:main.interior-page
     [:h1 title]
     [:p (:hello simple)]]))

(k/run (deftest test-app-lifecycle

  (testing "it renders a basic Ring response"
    (let [routes {"/en"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component home
                   :route/params {:lang "en"}}
                  "/fr"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component home
                   :route/params {:lang "fr"}}
                  "/en/parent-page"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component page
                   :route/params {:lang "en"
                                  :slugs "parent-page"}}
                  "/en/parent-page/child-page"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component page
                   :route/params {:lang "en"
                                  :slugs "parent-page/child-page"}}
                  "/fr/parent-page"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component page
                   :route/params {:lang "fr"
                                  :slugs "parent-page"}}
                  "/fr/parent-page/child-page"
                  {:bread/resolver {:resolver/type :resolver.type/page}
                   :bread/component page
                   :route/params {:lang "fr"
                                  :slugs "parent-page/child-page"}}
                  }
          app (bread/app {:plugins [(store/plugin config)
                                    (route/plugin)
                                    (resolver/plugin)
                                    (query/plugin)
                                    (component/plugin)
                                    (map->route-plugin routes)]})
          handler (bread/load-handler app)]
      (are
        [expected res]
        (= expected (select-keys res [:body]))

        {:body
         [:main
          [:h1 "Home Page"]
          [:p "Hi!"]]}
        (handler {:uri "/en"})

        {:body
         [:main
          [:h1 "Page D'Accueil"]
          [:p "Allo!"]]}
        (handler {:uri "/fr"})

        {:body
         [:main.interior-page
          [:h1 "Parent Page"]
          [:p "Hello from parent"]]}
        (handler {:uri "/en/parent-page"})

        {:body
         [:main.interior-page
          [:h1 "La Page Parent"]
          [:p "Bonjour de parent"]]}
        (handler {:uri "/fr/parent-page"})

        {:body
         [:main.interior-page
          [:h1 "Child Page"]
          [:p "Hello from child"]]}
        (handler {:uri "/en/parent-page/child-page"})

        {:body
         [:main.interior-page
          [:h1 "La Page Enfant"]
          [:p "Bonjour d'enfant"]]}
        (handler {:uri "/fr/parent-page/child-page"})

        )))))

(comment
  (k/run))
