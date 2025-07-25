(ns systems.bread.alpha.app-test
  (:require
    [clojure.test :refer [are deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.expansion :as expansion]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.test-helpers :refer [naive-params use-db]]
    [systems.bread.alpha.defaults :as defaults]))

(defn edn-field [k lang content]
  {:field/key k
   :field/lang lang
   :field/format :edn
   :field/content (pr-str content)})

(def config {:db/type :datahike
             :store {:backend :mem
                     :id "app-test-db"}
             :db/initial-txns
             [;; init post content
              {:db/id "page.home"
               :post/type :page
               :thing/slug ""
               :post/status :post.status/published
               :thing/fields
               #{(edn-field :title :en "Home Page")
                 (edn-field :title :fr "Page D'Accueil")
                 (edn-field :simple :en {:hello "Hi!"})
                 (edn-field :simple :fr {:hello "Allo!"})}}
              {:db/id "page.parent"
               :post/type :page
               :thing/slug "parent-page"
               :post/status :post.status/published
               :thing/children ["page.child"]
               :thing/fields
               #{(edn-field :title :en "Parent Page")
                 (edn-field :title :fr "La Page Parent")
                 (edn-field :simple :en {:hello "Hello from parent"})
                 (edn-field :simple :fr {:hello "Bonjour de parent"})}}
              {:db/id "page.child"
               :post/type :page
               :thing/slug "child-page"
               :post/status :post.status/published
               :thing/fields
               #{(edn-field :title :en "Child Page")
                 (edn-field :title :fr "La Page Enfant")
                 (edn-field :simple :en {:hello "Hello from child"})
                 (edn-field :simple :fr {:hello "Bonjour d'enfant"})}}
              ;; TODO deserialize global strings in the same way...
              {:field/lang :en
               :field/key :not-found
               :field/format :edn
               :field/content "404 Not Found"}
              {:field/lang :fr
               :field/key :not-found
               :field/format :edn
               :field/content "404 Pas Trouvé"}]})

(use-db :each config)

(defc Layout [{:keys [content]}]
  {}
  [:body
   content])

(defc Home [{:keys [post]}]
  {:query '[{:thing/fields [*]}]
   :key :post
   :extends Layout}
  (let [{:keys [title simple]} (:thing/fields post)]
    [:main
     [:h1 title]
     [:p (:hello simple)]]))

(defc Page [{:keys [post]}]
  {:query '[{:thing/fields [*]}]
   :key :post
   :extends Layout}
  (let [{:keys [title simple]} (:thing/fields post)]
    [:main.interior-page
     [:h1 title]
     [:p (:hello simple)]]))

(defc NotFound [{:keys [i18n]}]
  {:extends Layout}
  [:main (:not-found i18n)])

(deftest test-app-lifecycle

  (testing "it renders a localized Ring response"
    (let [routes {"/en"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Home}
                   :bread/component Home
                   :route/params {:field/lang "en"}}
                  "/fr"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Home}
                   :bread/component Home
                   :route/params {:field/lang "fr"}}
                  "/en/parent-page"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Page}
                   :bread/component Page
                   :route/params {:field/lang "en"
                                  :thing/slug* "parent-page"}}
                  "/en/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Page}
                   :bread/component Page
                   :route/params {:field/lang "en"
                                  :thing/slug* "parent-page/child-page"}}
                  "/fr/parent-page"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Page}
                   :bread/component Page
                   :route/params {:field/lang "fr"
                                  :thing/slug* "parent-page"}}
                  "/fr/parent-page/child-page"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Page}
                   :bread/component Page
                   :route/params {:field/lang "fr"
                                  :thing/slug* "parent-page/child-page"}}
                  "/en/404"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Page}
                   :bread/component Page
                   :route/params {:field/lang "en"
                                  :thing/slug* "not-found"}}
                  "/fr/404"
                  {:bread/dispatcher {:dispatcher/type ::post/page=>
                                      :dispatcher/key :post
                                      :dispatcher/component Page}
                   :bread/component Page
                   :route/params {:field/lang "fr"
                                  :thing/slug* "not-found"}}}
          router (reify bread/Router
                   (bread/route-params [router req]
                     (:route/params (get routes (:uri req))))
                   (bread/route-dispatcher [router req]
                     (:bread/dispatcher (get routes (:uri req)))))
          plugins (defaults/plugins
                    {:db config
                     :components {:not-found NotFound}
                     :routes {:router router}
                     :i18n {:supported-langs #{:en :fr}}
                     :renderer false})
          handler (->> {:plugins plugins} bread/app bread/load-app bread/handler)]
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
