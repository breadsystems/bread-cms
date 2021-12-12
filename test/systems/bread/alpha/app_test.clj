(ns ^{:doc "High-level abstractions for composing a Bread app"
      :author "Coby Tamayo"}
  systems.bread.alpha.app-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.resolver :as resolver]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [use-datastore]])
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
               :db/cardinality :db.cardinality/one}

              {:db/ident :i18n/key
               :db/valueType :db.type/keyword
               :db/cardinality :db.cardinality/one}
              {:db/ident :i18n/lang
               :db/valueType :db.type/keyword
               :db/cardinality :db.cardinality/one}
              {:db/ident :i18n/string
               :db/valueType :db.type/string
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
              #:i18n{:lang :en
                     :key :not-found
                     :string "404 Not Found"}
              #:i18n{:lang :fr
                     :key :not-found
                     :string "404 Pas Trouvé"}
              ]})

(use-datastore :each config)

(component/defc home [{:keys [post]}]
  {:query [{:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:main
     [:h1 title]
     [:p (:hello simple)]]))

(component/defc page [{:keys [post]}]
  {:query [{:post/fields [:field/key :field/content]}]
   :key :post}
  (let [post (post/compact-fields post)
        {:keys [title simple]} (:post/fields post)]
    [:main.interior-page
     [:h1 title]
     [:p (:hello simple)]]))

(component/defc ^:not-found not-found [{:keys [i18n]}]
  {}
  [:main (:not-found i18n)])

(deftest test-app-lifecycle

  (testing "it renders a localized Ring response"
    (let [routes {"/en"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component home}
                   :route/params {:lang "en"}}
                  "/fr"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component home}
                   :route/params {:lang "fr"}}
                  "/en/parent-page"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component page}
                   :route/params {:lang "en"
                                  :slugs "parent-page"}}
                  "/en/parent-page/child-page"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component page}
                   :route/params {:lang "en"
                                  :slugs "parent-page/child-page"}}
                  "/fr/parent-page"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component page}
                   :route/params {:lang "fr"
                                  :slugs "parent-page"}}
                  "/fr/parent-page/child-page"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component page}
                   :route/params {:lang "fr"
                                  :slugs "parent-page/child-page"}}
                  "/en/404"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component page}
                   :bread/not-found-component not-found
                   :route/params {:lang "en"
                                  :slugs "not-found"}}
                  "/fr/404"
                  {:bread/resolver {:resolver/type :resolver.type/page
                                    :resolver/component page}
                   :bread/not-found-component not-found
                   :route/params {:lang "fr"
                                  :slugs "not-found"}}}
          router (reify bread/Router
                   (bread/match [router req]
                     (get routes (:uri req)))
                   (bread/params [router match]
                     (:route/params match))
                   (bread/resolver [router match]
                     (:bread/resolver match))
                   (bread/component [_ match]
                     (:resolver/component (:bread/resolver match)))
                   (bread/not-found-component [router match]
                     (:resolver/not-found-component (:bread/resolver match)))
                   (bread/dispatch [router req]
                     (assoc req ::bread/resolver (route/resolver req))))
          ;; TODO call (cms/defaults ...)
          app (bread/app {:plugins [(store/plugin config)
                                    (route/plugin router)
                                    (resolver/plugin)
                                    (query/plugin)
                                    (i18n/plugin)
                                    (component/plugin)]})
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

        {:body
         [:main "404 Not Found"]}
        (handler {:uri "/en/404"})

        {:body
         [:main "404 Pas Trouvé"]}
        (handler {:uri "/fr/404"})

        ))))

(comment
  (test-app-lifecycle)
  (k/run))
