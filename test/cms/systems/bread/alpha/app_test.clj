(ns systems.bread.alpha.app-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db] ;; TODO ???
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.test-helpers :refer [use-db]]
    [systems.bread.alpha.plugin.defaults :as defaults]))

(def config {:db/type :datahike
             :store {:backend :mem
                     :id "app-test-db"}
             :db/initial-txns
             [;; init post content
              {:db/id "page.home"
               :post/type :post.type/page
               :thing/slug ""
               :post/status :post.status/published
               :translatable/fields
               #{{:field/key :title
                  :field/lang :en
                  :field/format :edn
                  :field/content (pr-str "Home Page")}
                 {:field/key :title
                  :field/lang :fr
                  :field/format :edn
                  :field/content (pr-str "Page D'Accueil")}
                 {:field/key :simple
                  :field/lang :en
                  :field/format :edn
                  :field/content (pr-str {:hello "Hi!"})}
                 {:field/key :simple
                  :field/lang :fr
                  :field/format :edn
                  :field/content (pr-str {:hello "Allo!"})}}}
              {:db/id "page.parent"
               :post/type :post.type/page
               :thing/slug "parent-page"
               :post/status :post.status/published
               :thing/children ["page.child"]
               :translatable/fields
               #{{:field/key :title
                  :field/lang :en
                  :field/format :edn
                  :field/content (pr-str "Parent Page")}
                 {:field/key :title
                  :field/lang :fr
                  :field/format :edn
                  :field/content (pr-str "La Page Parent")}
                 {:field/key :simple
                  :field/lang :en
                  :field/format :edn
                  :field/content
                  (pr-str {:hello "Hello from parent"})}
                 {:field/key :simple
                  :field/lang :fr
                  :field/format :edn
                  :field/content
                  (pr-str {:hello "Bonjour de parent"})}}}
              {:db/id "page.child"
               :post/type :post.type/page
               :thing/slug "child-page"
               :post/status :post.status/published
               :translatable/fields
               #{{:field/key :title
                  :field/lang :en
                  :field/format :edn
                  :field/content (pr-str "Child Page")}
                 {:field/key :title
                  :field/lang :fr
                  :field/format :edn
                  :field/content (pr-str "La Page Enfant")}
                 {:field/key :simple
                  :field/lang :en
                  :field/format :edn
                  :field/content
                  (pr-str {:hello "Hello from child"})}
                 {:field/key :simple
                  :field/lang :fr
                  :field/format :edn
                  :field/content
                  (pr-str {:hello "Bonjour d'enfant"})}}}
              {:field/lang :en
               :field/key :not-found
               :field/format :edn
               :field/content "404 Not Found"}
              {:field/lang :fr
               :field/key :not-found
               :field/format :edn
               :field/content "404 Pas Trouvé"}]})

(use-db :each config)

(defc layout [{:keys [content]}]
  {}
  [:body
   content])

(defc home [{:keys [post]}]
  {:query '[{:translatable/fields [*]}]
   :key :post
   :extends layout}
  (let [{:keys [title simple]} (:translatable/fields post)]
    [:main
     [:h1 title]
     [:p (:hello simple)]]))

(defc page [{:keys [post]}]
  {:query '[{:translatable/fields [*]}]
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
                   :route/params {:field/lang "en"}}
                  "/fr"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component home}
                   :bread/component home
                   :route/params {:field/lang "fr"}}
                  "/en/parent-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:field/lang "en"
                                  :thing/slug* "parent-page"}}
                  "/en/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:field/lang "en"
                                  :thing/slug* "parent-page/child-page"}}
                  "/fr/parent-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:field/lang "fr"
                                  :thing/slug* "parent-page"}}
                  "/fr/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:field/lang "fr"
                                  :thing/slug* "parent-page/child-page"}}
                  "/en/404"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:field/lang "en"
                                  :thing/slug* "not-found"}}
                  "/fr/404"
                  {:bread/dispatcher {:dispatcher/type :dispatcher.type/page
                                      :dispatcher/key :post
                                      :dispatcher/component page}
                   :bread/component page
                   :route/params {:field/lang "fr"
                                  :thing/slug* "not-found"}}}
          router (reify bread/Router
                   (bread/match [router req]
                     (get routes (:uri req)))
                   (bread/params [router match]
                     (:route/params match))
                   (bread/dispatcher [router match]
                     (:bread/dispatcher match)))
          app (defaults/app {:db config
                             :components {:not-found not-found}
                             :routes {:router router}
                             :i18n {:supported-langs #{:en :fr}}
                             :renderer false})
          handler (bread/load-handler app)]
      (are
        [expected req]
        (= expected (-> req handler (select-keys [:body :status])))

        {:body
         [:body [:main
                 [:h1 "Home Page"]
                 [:p "Hi!"]]]
         :status 200}
        {:uri "/en"}

        {:body
         [:body [:main
                 [:h1 "Page D'Accueil"]
                 [:p "Allo!"]]]
         :status 200}
        {:uri "/fr"}

        {:body
         [:body
          [:main.interior-page
           [:h1 "Parent Page"]
           [:p "Hello from parent"]]]
         :status 200}
        {:uri "/en/parent-page"}

        {:body
         [:body
          [:main.interior-page
           [:h1 "La Page Parent"]
           [:p "Bonjour de parent"]]]
         :status 200}
        {:uri "/fr/parent-page"}

        {:body
         [:body
          [:main.interior-page
           [:h1 "Child Page"]
           [:p "Hello from child"]]]
         :status 200}
        {:uri "/en/parent-page/child-page"}

        {:body
         [:body
          [:main.interior-page
           [:h1 "La Page Enfant"]
           [:p "Bonjour d'enfant"]]]
         :status 200}
        {:uri "/fr/parent-page/child-page"}

        {:body
         [:body
          [:main "404 Not Found"]]
         :status 404}
        {:uri "/en/404"}

        {:body
         [:body
          [:main "404 Pas Trouvé"]]
         :status 404}
        {:uri "/fr/404"}

        ))))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
