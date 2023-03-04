(ns ^{:doc "High-level abstractions for composing a Bread app"
      :author "Coby Tamayo"}
  systems.bread.alpha.app-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.test-helpers :refer [use-datastore]]))

(def config {:datastore/type :datahike
             :store {:backend :mem
                     :id "app-test-db"}
             :datastore/initial-txns
             [;; init post content
              {:db/id "page.home"
               :post/type :post.type/page
               :post/slug ""
               :post/status :post.status/published
               :post/fields
               #{{:field/key :title
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
                  :field/content (prn-str {:hello "Allo!"})}}}
              {:db/id "page.parent"
               :post/type :post.type/page
               :post/slug "parent-page"
               :post/status :post.status/published
               :post/children ["page.child"]
               :post/fields
               #{{:field/key :title
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
                  (prn-str {:hello "Bonjour de parent"})}}}
              {:db/id "page.child"
               :post/type :post.type/page
               :post/slug "child-page"
               :post/status :post.status/published
               :post/fields
               #{{:field/key :title
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
                  (prn-str {:hello "Bonjour d'enfant"})}}}
              {:i18n/lang :en
               :i18n/key :not-found
               :i18n/string "404 Not Found"}
              {:i18n/lang :fr
               :i18n/key :not-found
               :i18n/string "404 Pas Trouvé"}]})

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

(component/defc not-found [{:keys [i18n]}]
  {}
  [:main (:not-found i18n)])

(deftest test-app-lifecycle

  (testing "it renders a localized Ring response"
    (let [routes {"/en"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component home}
                   :route/params {:lang "en"}}
                  "/fr"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component home}
                   :route/params {:lang "fr"}}
                  "/en/parent-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :route/params {:lang "en"
                                  :slugs "parent-page"}}
                  "/en/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :route/params {:lang "en"
                                  :slugs "parent-page/child-page"}}
                  "/fr/parent-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :route/params {:lang "fr"
                                  :slugs "parent-page"}}
                  "/fr/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :route/params {:lang "fr"
                                  :slugs "parent-page/child-page"}}
                  "/en/404"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page
                                      :dispatcher/not-found-component not-found}
                   :route/params {:lang "en"
                                  :slugs "not-found"}}
                  "/fr/404"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page
                                      :dispatcher/not-found-component not-found}
                   :route/params {:lang "fr"
                                  :slugs "not-found"}}}
          router (reify bread/Router
                   (bread/match [router req]
                     (get routes (:uri req)))
                   (bread/params [router match]
                     (:route/params match))
                   (bread/dispatcher [router match]
                     (:bread/dispatcher match))
                   (bread/component [_ match]
                     (:dispatcher/component (:bread/dispatcher match)))
                   (bread/not-found-component [router match]
                     (:dispatcher/not-found-component (:bread/dispatcher match))))
          app (defaults/app {:datastore config
                             :routes {:router router}
                             :i18n {:supported-langs #{:en :fr}}})
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
  (k/run))
