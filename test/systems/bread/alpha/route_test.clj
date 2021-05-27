(ns systems.bread.alpha.route-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]))

(deftest test-route-resolver-matching
  (let [;; Plugin a simplistic router with hard-coded uri->match logic.
        route->match (fn [req _]
                       (get {"/en" {:resolver/type :home}
                             "/en/default" :default
                             "/en/empty-resolver-map" {}
                             "/en/no-defaults" {:resolver/type :whatevs
                                                :resolver/defaults? false}
                             "/overridden" {:resolver/internationalize? false
                                            :resolver/ancestry? false}}
                            (:uri req)))
        simplistic-route-plugin (fn [app]
                                  (bread/add-hooks-> app
                                    (:hook/match-route route->match)
                                    (:hook/match->resolver (fn [_ match]
                                                             match))))
        app (plugins->loaded [simplistic-route-plugin])]

    (testing "default resolver - implicit from nil"
      (is (= {:resolver/attr :slugs
              :resolver/type :post
              :resolver/internationalize? true
              :resolver/ancestry? true
              :post/type :post.type/page}
               ;; No explicit entry for /nil, so here we expect the
               ;; hard-coded default map.
             (route/resolver (merge app {:uri "/nil"})))))

    (testing "default resolver - implicit from {}"
      (is (= {:resolver/attr :slugs
              :resolver/type :post
              :resolver/internationalize? true
              :resolver/ancestry? true
              :post/type :post.type/page}
               ;; No explicit entry for /default, so here we expect the
               ;; hard-coded default map.
             (route/resolver (merge app {:uri "/en/empty-resolver-map"})))))

    (testing "default resolver - explicit"
      (is (= {:resolver/attr :slugs
              :resolver/type :post
              :resolver/internationalize? true
              :resolver/ancestry? true
              :post/type :post.type/page}
              ;; Here, (resolver ...) picks up on the the explicit :default.
             (route/resolver (merge app {:uri "/en/default"})))))

    (testing "default resolver with overrides"
      (is (= {:resolver/attr :slugs
              :resolver/type :post
              :resolver/internationalize? false
              :resolver/ancestry? false
              :post/type :post.type/page}
             (route/resolver (merge app {:uri "/overridden"})))))

    (testing "with defaults disabled"
      (is (= {:resolver/type :whatevs
              :resolver/defaults? false}
             (route/resolver (merge app {:uri "/en/no-defaults"})))))

    (testing "with a custom resolver hook"
      (let [opinionated-resolver-plugin
            (fn [app]
              (bread/add-hook
                app :hook/resolver
                (constantly {:resolver/stuff :totally-different})))
            app (plugins->loaded [simplistic-route-plugin
                                  opinionated-resolver-plugin])]
        (is (= {:resolver/stuff :totally-different}
               (route/resolver (merge app {:uri "/whatever"}))))))

    ))
