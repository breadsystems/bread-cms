(ns systems.bread.alpha.pages-test
  (:require
    [clojure.test :refer [are deftest is]]
    [org.httpkit.client :as http])
  (:import
    [org.jsoup Jsoup]))

(defn GET
  ([uri]
   (GET uri {}))
  ([uri opts]
   (let [base-uri (or (System/getenv "TEST_ROOT_URI") "http://localhost:1312")]
     @(http/get (str base-uri uri) opts))))

(deftest test-root-redirect
  (let [{:keys [status headers]} (GET "/" {:follow-redirects false})]
    (is (= 302 status))
    (is (= "/ar" (headers :location)))))

(deftest test-home-page
  ;; TODO test without trailing /
  (let [{:keys [body status]} (GET "/en/")
        doc (Jsoup/parse body)]
    (is (= 200 status))
    (is (= "Breadbox" (.text (.getElementsByTag doc "title"))))
    (is (= "The Title" (.text (.getElementsByTag doc "h1"))))
    (is (= ["Some content" "More content" "And some more..."]
           (map #(.text %) (.getElementsByTag doc "h2"))))
    (is (clojure.string/starts-with?
          (.text (.getElementsByTag doc "article"))
          "The Title Some content Lorem ipsum dolor sit amet, consectetur")))

  (let [{:keys [body status]} (GET "/fr/")
        doc (Jsoup/parse body)]
    (is (= 200 status))
    (is (= "Breadbox" (.text (.getElementsByTag doc "title"))))
    (is (= "Le Titre" (.text (.getElementsByTag doc "h1"))))
    (let [h2-headings (map #(.text %) (.getElementsByTag doc "h2"))]
      (is (= ["Le content" "Et plus" "Et maintenant, plus..."] h2-headings)))
    (is (clojure.string/starts-with?
           (.text (.getElementsByTag doc "article"))
           "Le Titre Le content Lorem ipsum dolor sit amet, consectetur"))))

(deftest test-not-found
  (let [{:keys [body status]} (GET "/en/404")
        doc (Jsoup/parse body)]
    (is (= 404 status))
    (is (= "404 The page you are looking for was not found."
           (.text (.getElementsByTag doc "article")))))
  (let [{:keys [body status]} (GET "/fr/404")
        doc (Jsoup/parse body)]
    (is (= 404 status))
    ;; TODO i18n
    (is (= "404 The page you are looking for was not found."
           (.text (.getElementsByTag doc "article"))))))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
