(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest are is testing]]
    [kaocha.repl :as k]
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              map->route-plugin]]))

(deftest test-route-dispatch
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        routes {"/en/home"
                {:bread/resolver :resolver.type/home
                 :bread/component 'home
                 :route/params {:lang "en"}}
                "/en/keyword"
                {:bread/resolver :resolver.type/page
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "keyword"}}
                "/en/default"
                {:bread/resolver :default
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "default"}}
                "/en/empty-resolver-map"
                {:bread/resolver {}
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "empty-resolver-map"}}
                "/en/no-defaults"
                {:bread/resolver {:resolver/type :whatevs
                                  :resolver/defaults? false}
                 :bread/component 'page
                 :route/params {:lang "en"
                                :slug "no-defaults"}}
                "/en/no-component"
                {:bread/resolver {:resolver/type :whatevs}
                 :route/params {:lang "en"
                                :slug "no-component"}}
                "/en/not-found"
                {:bread/resolver {:resolver/type :whatevs}
                 :bread/component 'page
                 :bread/not-found-component 'not-found
                 :route/params {:lang "en"
                                :slug "not-found"}}
                "/overridden"
                {:bread/resolver {:resolver/i18n? false}
                 :bread/component 'page
                 :route/params {:lang nil
                                :slug "overridden"}}}
        ;; Mock the component registry with key/pull values.
        ;; These values are not valid for the default schema but are meant to
        ;; be illustrative.
        registry (atom {'home {:key :home :query [:db/id :home/slug]}
                        'page {:key :page :query [:db/id :page/slug]}})
        app (plugins->loaded [(map->route-plugin routes)])]

    (are [resolver uri] (= resolver
                           (binding [component/*registry* registry]
                             (-> {:uri uri}
                                 (merge app)
                                 (bread/hook :hook/dispatch)
                                 ::bread/resolver)))

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :resolver/component nil
          :resolver/not-found-component nil
          :resolver/key nil
          :resolver/pull nil
          :post/type :post.type/page
          :route/params nil
          :route/match nil}
         "/nil"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :resolver/component 'home
          :resolver/not-found-component nil
          :resolver/key :home
          :resolver/pull [:db/id :home/slug]
          :post/type :post.type/page
          :route/params {:lang "en"}
          :route/match {:bread/resolver :resolver.type/home
                        :bread/component 'home
                        :route/params {:lang "en"}}}
         "/en/home"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :post/type :post.type/page
          :resolver/component 'page
          :resolver/not-found-component nil
          :resolver/key :page
          :resolver/pull [:db/id :page/slug]
          :route/params {:lang "en" :slug "keyword"}
          :route/match {:bread/resolver :resolver.type/page
                        :bread/component 'page
                        :route/params {:lang "en" :slug "keyword"}}}
         "/en/keyword"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :post/type :post.type/page
          :resolver/component 'page
          :resolver/not-found-component nil
          :resolver/key :page
          :resolver/pull [:db/id :page/slug]
          :route/params {:lang "en"
                         :slug "empty-resolver-map"}
          :route/match {:bread/resolver {}
                        :bread/component 'page
                        :route/params {:lang "en"
                                       :slug "empty-resolver-map"}}}
         "/en/empty-resolver-map"

         {:resolver/type :resolver.type/page
          :resolver/i18n? true
          :resolver/component 'page
          :resolver/not-found-component nil
          :resolver/key :page
          :resolver/pull [:db/id :page/slug]
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "default"}
          :route/match {:bread/resolver :default
                        :bread/component 'page
                        :route/params {:lang "en"
                                       :slug "default"}}}
         "/en/default"

         {:resolver/type :resolver.type/page
          :resolver/i18n? false
          :resolver/component 'page
          :resolver/not-found-component nil
          :resolver/key :page
          :resolver/pull [:db/id :page/slug]
          :post/type :post.type/page
          :route/params {:lang nil :slug "overridden"}
          :route/match {:bread/resolver {:resolver/i18n? false}
                        :route/params {:lang nil :slug "overridden"}
                        :bread/component 'page}}
         "/overridden"

         {:resolver/type :whatevs
          :resolver/defaults? false
          :resolver/component 'page
          :resolver/not-found-component nil
          :resolver/key :page
          :resolver/pull [:db/id :page/slug]
          :route/params {:lang "en"
                         :slug "no-defaults"}
          :route/match {:bread/resolver {:resolver/type :whatevs
                                         :resolver/defaults? false}
                        :bread/component 'page
                        :route/params {:lang "en"
                                       :slug "no-defaults"}}}
         "/en/no-defaults"

         {:resolver/type :whatevs
          :resolver/i18n? true
          :resolver/component 'page
          :resolver/not-found-component 'not-found
          :resolver/key :page
          :post/type :post.type/page
          :resolver/pull [:db/id :page/slug]
          :route/params {:lang "en"
                         :slug "not-found"}
          :route/match {:bread/resolver {:resolver/type :whatevs}
                        :bread/component 'page
                        :bread/not-found-component 'not-found
                        :route/params {:lang "en"
                                       :slug "not-found"}}}
         "/en/not-found"

         {:resolver/type :whatevs
          :resolver/i18n? true
          :resolver/component nil
          :resolver/not-found-component nil
          :resolver/key nil
          :resolver/pull nil
          :post/type :post.type/page
          :route/params {:lang "en"
                         :slug "no-component"}
          :route/match {:bread/resolver {:resolver/type :whatevs}
                        :route/params {:lang "en"
                                       :slug "no-component"}}}
         "/en/no-component"

        ;;
        )

    (testing "with a custom resolver hook"
      (let [opinionated-resolver-plugin
            (fn [app]
              (bread/add-hook
                app :hook/resolver
                (constantly {:resolver/stuff :totally-different})))
            app (plugins->loaded [(map->route-plugin routes)
                                  opinionated-resolver-plugin])]
        (is (= {:resolver/stuff :totally-different}
               (route/resolver (merge app {:uri "/whatever"}))))))

    ))

(comment
  (k/run))
