(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              map->route-plugin
                                              naive-router]]))

(defc Home [_]
  {:key :home
   :query [:thing/slug]})

(defc Page [_]
  {:key :page
   :query [:db/id :thing/slug]})

(defn- handler [_])

(deftest test-route-dispatch
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        routes {"/en/home"
                {:dispatcher/type :dispatcher.type/home
                 :dispatcher/component 'home
                 :route/params {:lang "en"}}
                "/en/empty-dispatcher-map"
                {:dispatcher/component Page
                 :route/params {:lang "en" :slug "empty-dispatcher-map"}}
                "/en/no-defaults"
                {:dispatcher/type :whatevs
                 :dispatcher/component Page
                 :dispatcher/defaults? false
                 :route/params {:lang "en" :slug "no-defaults"}}
                "/en/no-component"
                {:dispatcher/type :whatevs
                 :route/params {:lang "en" :slug "no-component"}}
                "/en/not-found"
                {:dispatcher/type :whatevs
                 :dispatcher/component Page
                 :route/params {:lang "en" :slug "not-found"}}
                "/overridden"
                {:dispatcher/i18n? false
                 :dispatcher/component Page
                 :route/params {:lang nil :slug "overridden"}}
                "/function"
                handler
                "/var"
                #'handler}
        app (plugins->loaded [(map->route-plugin routes)])]

    (are [dispatcher uri] (= dispatcher
                             (-> {:uri uri}
                                 (merge app)
                                 (bread/hook ::bread/route)
                                 ::bread/dispatcher))

         {:dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang "en"
                         :slug "empty-dispatcher-map"}}
         "/en/empty-dispatcher-map"

         {:dispatcher/i18n? false
          :dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang nil :slug "overridden"}}
         "/overridden"

         {:dispatcher/type :whatevs
          :dispatcher/defaults? false
          :dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang "en"
                         :slug "no-defaults"}}
         "/en/no-defaults"

         {:dispatcher/type :whatevs
          :dispatcher/component Page
          :dispatcher/key :page
          :dispatcher/pull [:db/id :thing/slug]
          :route/params {:lang "en"
                         :slug "not-found"}}
         "/en/not-found"

         {:dispatcher/type :whatevs
          :dispatcher/component nil
          :dispatcher/key nil
          :dispatcher/pull nil
          :route/params {:lang "en"
                         :slug "no-component"}}
         "/en/no-component"

         handler
         "/function"

         #'handler
         "/var"

        ;;
        )

    (testing "with a custom dispatcher hook"
      (let [app (plugins->loaded [(map->route-plugin routes)
                                  {:hooks
                                   {::route/dispatcher
                                    [{:action/name ::bread/value
                                      :action/value ::FAKE_DISPATCHER}]}}])]
        (is (= ::FAKE_DISPATCHER
               (route/dispatcher (merge app {:uri "/whatever"}))))))

    ))

(deftest test-router
  (is (= ::ROUTER (route/router (plugins->loaded [(route/plugin
                                                    {:router ::ROUTER})])))))

(deftest test-uri
  (let [routes {"/en"
                {:name :articles
                 :route/spec [:field/lang]}
                "/en/the-slug"
                {:name :article
                 :route/spec [:field/lang :thing/slug]}
                "/en/article/the-slug"
                {:name :article-nested
                 :route/spec [:field/lang "article" :thing/slug]}
                "/en/a/b/c"
                {:name :wildcard
                 :route/spec [:field/lang :slugs]}
                "/en/page/a/b/c"
                {:name :wildcard-nested
                 :route/spec [:field/lang "page" :slugs]}}
        app (plugins->loaded [(map->route-plugin routes)])]
    (are
      [expected route-name thing+params]
      (= expected (route/uri app route-name thing+params))

      nil nil nil
      nil nil {}
      "/en" :articles {}
      "/en/the-slug" :article {:thing/slug "the-slug"}
      "/en/article/the-slug" :article-nested {:thing/slug "the-slug"}

      "/en/a/b/c"
      :wildcard
      {:thing/slug "c"
       :thing/_children [{:thing/slug "b"
                          :thing/_children [{:thing/slug "c"}]}]}

      "/en/page/a/b/c"
      :wildcard-nested
      {:thing/slug "a"
       :thing/_children [{:thing/slug "b"
                          :thing/_children [{:thing/slug "c"}]}]}

      ;;
      )))

(deftest test-uri-helper
  (let [home-route {:name :home :route/spec [:field/lang]}
        page-route {:name :page :route/spec [:field/lang :thing/slug]}
        page-action-route {:name :page-action
                           :route/spec [:field/lang :thing/slug "action"]}
        routes {"/en" (assoc home-route :route/params {:field/lang :en})
                "/es" (assoc home-route :route/params {:field/lang :es})
                "/en/123"
                (assoc page-route :route/params {:field/lang :en :thing/slug "123"})
                "/en/456"
                (assoc page-route :route/params {:field/lang :en :thing/slug "456"})
                "/es/123"
                (assoc page-route :route/params {:field/lang :es :thing/slug "123"})
                "/es/456"
                (assoc page-route :route/params {:field/lang :es :thing/slug "456"})
                "/en/123/action"
                (assoc page-action-route :route/params {:field/lang :en :thing/slug "123"})
                "/en/456/action"
                (assoc page-action-route :route/params {:field/lang :en :thing/slug "456"})
                "/es/123/action"
                (assoc page-action-route :route/params {:field/lang :es :thing/slug "123"})
                "/es/456/action"
                (assoc page-action-route :route/params {:field/lang :es :thing/slug "456"})}
        router (naive-router routes)
        app (plugins->loaded [(route/plugin {:router router})])
        ->helper (fn [uri]
                   (let [handler (bread/handler app)
                         res (handler {:uri uri})]
                     (-> res ::bread/data :route/uri)))]
    (are
      [expected uri args]
      (= expected (apply (->helper uri) args))

      ;; The naive-router implementation will return "/" if route is not found.
      "/" "/en" [:missing]
      "/" "/es" [:missing]

      "/en" "/en" [:home]
      ;; NOTE: params override the request.
      ;; This is so that we can render e.g. a link to /es from an English page.
      "/es" "/en" [:home {:field/lang "es"}]
      "/es" "/en/blah" [:home {:field/lang "es"}]
      "/en" "/es/blah" [:home {:field/lang "en"}]
      "/en/123" "/es" [:page {:field/lang "en" :thing/slug "123"}]
      "/es/123" "/en" [:page {:field/lang "es" :thing/slug "123"}]
      "/es/123/action" "/en" [:page-action {:field/lang "es" :thing/slug "123"}]

      ;; Let :field/lang default.
      "/es/123" "/es" [:page {:thing/slug "123"}]
      "/en/123" "/en" [:page {:thing/slug "123"}]
      "/en/123/action" "/en" [:page-action {:thing/slug "123"}]
      "/es/123/action" "/es" [:page-action {:thing/slug "123"}]

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
