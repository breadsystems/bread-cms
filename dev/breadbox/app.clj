(ns breadbox.app
  (:require
   [breadbox.env]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.templates :as tpl]
   [mount.core :as mount :refer [defstate]]
   [org.httpkit.server :as http]
   [ring.middleware.reload :refer [wrap-reload]]
   [rum.core :as rum :exclude [cljsjs/react cljsjs/react-dom]]))


(def handler (-> {:plugins [;; TODO logging plugin
                            #_(tpl/response->plugin
                               {:headers {"Content-Type" "text/html"}
                                :body [:html
                                       [:head
                                        [:title "Breadbox"]
                                        [:meta {:charset "utf-8"}]]
                                       [:body
                                        [:div.bread-app [:h1 "Hello, Breadster!"]]]]})
                            (static/static-site-plugin {:src "pages"
                                                        :dest "dist"})
                            (tpl/renderer->plugin (fn [body]
                                                    ;; TODO i18n
                                                    [:html {:lang "en"}
                                                     [:head
                                                      ;; TODO SEO
                                                      [:title "Main Title"]]
                                                     [:body
                                                      [:main
                                                       [:h1 "Main Title"]
                                                       [:article (tpl/dangerous body)]]]]))
                            (tpl/renderer->plugin rum/render-static-markup {:precedence 2})]}
                 (bread/app)
                 (bread/app->handler)))

(comment
  (rum/render-static-markup [:p "hi"])
  (bread/hook (handler {:url "one"}) :slug)
  (:body (handler {:url "one"}))
  ;; TODO test this out!
  (static/generate! handler)

  (defn translate-string [s] s)
  ;; TODO ideas for i18n
  ;; Dispatch on content field vs. hard-coded transation keys in markup...
  (def html [:html {:lang "en"}
             [:head
              [:title :text/hello]]
             [:body
              [:main
               [:h1 :text/hello]
               [:h2 :text/invalid]
               [:article (tpl/dangerous (translate-string "Some rich text content..."))]]]])

  ;; Here we delegate to tempura or similar
  ;; https://github.com/ptaoussanis/tempura
  (defn tr [x]
    (let [dict {:missing "MISSING"
                :hello "Hello"}]
      (get dict (:missing dict))))

  (defn i18n [x]
    ;; TODO
    (if (and (keyword? x) (= "text" (namespace x)))
      (name x)
      x))

  (clojure.walk/postwalk i18n html)

  (defn profiler-for-hooks [hooks f]
    (fn [call]
      (when (contains? hooks (:hook call)) (f call))))

  (defn prn-keys [ks m]
    (prn (select-keys m ks)))

  (binding [bread/*hook-profiler* (profiler-for-hooks #{:hook/render} (partial prn-keys [:hook :args]))]
    (handler {:url "one"})
    (slurp "dist/one.html"))
  (:body (handler {:url "two"})))


(defonce stop-http (atom nil))

(defn start! []
  (let [port (Integer. (or (System/getenv "HTTP_PORT") 8080))]
    (println (str "Running HTTP server at localhost:" port))
    (reset! stop-http (http/run-server (wrap-reload handler) {:port port})))
  nil)

(defn stop! []
  (println "Stopping HTTP server")
  (when (fn? @stop-http)
    (@stop-http))
  (reset! stop-http nil))

(defstate http-server
  :start (start!)
  :stop  (stop!))


(defn -main [& _args]
  (mount/start))


(defn restart []
  (mount/stop #'http-server)
  (mount/start #'http-server))


(comment
  (mount/stop #'http-server)
  (mount/start #'http-server)
  (restart))