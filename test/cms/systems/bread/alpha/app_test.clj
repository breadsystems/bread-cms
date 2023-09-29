(ns systems.bread.alpha.app-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.query :as query]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.test-helpers :refer [use-datastore]]
    [systems.bread.alpha.cms.defaults :as defaults]))

(def config {:datastore/type :datahike
             :store {:backend :mem
                     :id "app-test-db"}
             :datastore/initial-txns
             [;; init post content
              {:db/id "page.home"
               :post/type :post.type/page
               :post/slug ""
               :post/status :post.status/published
               :translatable/fields
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
               :translatable/fields
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
               :translatable/fields
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
              {:field/lang :en
               :field/key :not-found
               :field/content "404 Not Found"}
              {:field/lang :fr
               :field/key :not-found
               :field/content "404 Pas Trouvé"}]})

(use-datastore :each config)

(defc layout [{:keys [content]}]
  {}
  [:body
   content])

(defc home [{:keys [post]}]
  {:query [{:translatable/fields [:field/key :field/content]}]
   :key :post
   :extends layout}
  (let [post (i18n/compact post)
        {:keys [title simple]} (:translatable/fields post)]
    [:main
     [:h1 title]
     [:p (:hello simple)]]))

(defc page [{:keys [post]}]
  {:query [{:translatable/fields [:field/key :field/content]}]
   :key :post
   :extends layout}
  (let [{:keys [title simple]} (:translatable/fields post)]
    [:main.interior-page
     [:h1 title]
     [:p (:hello simple)]]))

(defc not-found [{:keys [i18n]}]
  {:extends layout}
  [:main (:not-found i18n)])

(deftest test-app-lifecycle

  (testing "it renders a localized Ring response"
    (let [routes {"/en"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component home}
                   :bread/component home
                   :route/params {:lang "en"}}
                  "/fr"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component home}
                   :bread/component home
                   :route/params {:lang "fr"}}
                  "/en/parent-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:lang "en"
                                  :slugs "parent-page"}}
                  "/en/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:lang "en"
                                  :slugs "parent-page/child-page"}}
                  "/fr/parent-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:lang "fr"
                                  :slugs "parent-page"}}
                  "/fr/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:lang "fr"
                                  :slugs "parent-page/child-page"}}
                  "/en/404"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:lang "en"
                                  :slugs "not-found"}}
                  "/fr/404"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:lang "fr"
                                  :slugs "not-found"}}}
          router (reify bread/Router
                   (bread/match [router req]
                     (get routes (:uri req)))
                   (bread/params [router match]
                     (:route/params match))
                   (bread/dispatcher [router match]
                     (:bread/dispatcher match)))
          app (defaults/app {:datastore config
                             :components {:not-found not-found}
                             :routes {:router router}
                             :i18n {:supported-langs #{:en :fr}}
                             :renderer false})
          handler (bread/load-handler app)]
      (are
        [expected res]
        (= expected (select-keys res [:body]))

        {:body
         [:body [:main
                 [:h1 "Home Page"]
                 [:p "Hi!"]]]}
        (handler {:uri "/en"})

        {:body
         [:body [:main
                 [:h1 "Page D'Accueil"]
                 [:p "Allo!"]]]}
        (handler {:uri "/fr"})

        {:body
         [:body
          [:main.interior-page
           [:h1 "Parent Page"]
           [:p "Hello from parent"]]]}
        (handler {:uri "/en/parent-page"})

        {:body
         [:body
          [:main.interior-page
           [:h1 "La Page Parent"]
           [:p "Bonjour de parent"]]]}
        (handler {:uri "/fr/parent-page"})

        {:body
         [:body
          [:main.interior-page
           [:h1 "Child Page"]
           [:p "Hello from child"]]]}
        (handler {:uri "/en/parent-page/child-page"})

        {:body
         [:body
          [:main.interior-page
           [:h1 "La Page Enfant"]
           [:p "Bonjour d'enfant"]]]}
        (handler {:uri "/fr/parent-page/child-page"})

        {:body
         [:body
          [:main "404 Not Found"]]}
        (handler {:uri "/en/404"})

        {:body
         [:body
          [:main "404 Pas Trouvé"]]}
        (handler {:uri "/fr/404"})

        ))))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
