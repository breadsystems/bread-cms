(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              map->route-plugin]]))

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
                {:name ::articles
                 :route/spec [:field/lang]}
                "/en/the-slug"
                {:name ::article
                 :route/spec [:field/lang :thing/slug]}
                "/en/article/the-slug"
                {:name ::article-nested
                 :route/spec [:field/lang "article" :thing/slug]}
                "/en/a/b/c"
                {:name ::wildcard
                 :route/spec [:field/lang :thing/slug*]}
                "/en/page/a/b/c"
                {:name ::wildcard-nested
                 :route/spec [:field/lang "page" :thing/slug*]}}
        app (plugins->loaded [(map->route-plugin routes)])]
    (are
      [expected route-name thing+params]
      (= expected (route/uri app route-name thing+params))

      nil nil nil
      nil nil {}
      "/en" ::articles {}
      "/en/the-slug" ::article {:thing/slug "the-slug"}
      "/en/article/the-slug" ::article-nested {:thing/slug "the-slug"}

      "/en/a/b/c"
      ::wildcard
      {:thing/slug "c"
       :thing/_children [{:thing/slug "b"
                          :thing/_children [{:thing/slug "c"}]}]}

      "/en/page/a/b/c"
      ::wildcard-nested
      {:thing/slug "c"
       :thing/_children [{:thing/slug "b"
                          :thing/_children [{:thing/slug "c"}]}]}

      ;;
      )))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
