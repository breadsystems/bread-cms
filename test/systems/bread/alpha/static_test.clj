(ns systems.bread.alpha.static-test
  (:require
    [clojure.test :refer [deftest is]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.static :as static]
    [systems.bread.alpha.templates :as tpl]
    [clojure.java.io :as io]))


(deftest test-static-site-plugin

  (let [markdown-renderer #(str "<markdown>" % "</markdown>")
        hiccup-renderer str
        handler (-> {:plugins [(static/static-site-plugin {:src      "pages"
                                                           :dest     "dist"
                                                           :renderer markdown-renderer})
                               (tpl/renderer->plugin (fn [body]
                                                       [:html {:lang "en"}
                                                        [:body body]]))
                               ;; Realistically, you're going to have more than just a static
                               ;; markdown renderer because you want to surround the content
                               ;; from your markdown in template markup.
                               (tpl/renderer->plugin hiccup-renderer {:precedence 2})]}
                    (bread/app)
                    (bread/app->handler))
        mock-resource (fn [path]
                        (let [pages {"pages/my-page.md" "my page content"}]
                          (char-array (get pages path))))
        response (with-redefs [io/resource mock-resource]
                   (handler {:uri "/my-page"}))]

    ;; Assert that our fake hiccup and markdown renderers ran.
    (is (= (str [:html {:lang "en"} [:body "<markdown>my page content</markdown>"]])
           (:body response)))))