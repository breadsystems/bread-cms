(ns systems.bread.alpha.static-backend-test
  (:require
    [clojure.test :refer [are deftest is]]
    [kaocha.repl :as k]
    [markdown.core :as md]
    [systems.bread.alpha.plugin.static-backend :as static]))

(deftest test-query-fs
  (let [mock-fs
        {"content/en/page.md"  "Markdown doc in English under /content"
         "alt/en/page.md"      "Markdown doc in English under /alt"
         "content/fr/page.md"  "Markdown doc in French under /content"
         "alt/fr/page.md"      "Markdown doc in French under /alt"
         "content/en/other.md" "OTHER doc in English under /content"
         "content/fr/other.md" "OTHER doc in French under /content"
         "content/en/page.ext" ".ext doc in English under /content"
         "content/en/meta.md"  "Title: Whoa, Meta!\n\nDoc with metadata"
         }]
    (with-redefs [clojure.java.io/resource str
                  slurp mock-fs]
      (are
        [content args]
        (= content (apply static/query-fs {} args))

        ;; Passing no opts arg
        {:html "<p>Markdown doc in English under /content</p>"}
        [{:lang "en" :slug "page"}]

        ;; Passing nil opts
        {:html "<p>Markdown doc in English under /content</p>"}
        [{:lang "en" :slug "page"} nil]

        ;; Passing empty opts
        {:html "<p>Markdown doc in English under /content</p>"}
        [{:lang "en" :slug "page"} {}]

        {:html "<p>Markdown doc in English under /alt</p>"}
        [{:lang "en" :slug "page"} {:root "alt"}]

        {:html "<p>Markdown doc in French under /content</p>"}
        [{:lang "fr" :slug "page"} {:root "content"}]

        {:html "<p>Markdown doc in French under /alt</p>"}
        [{:lang "fr" :slug "page"} {:root "alt"}]

        {:html "<p>OTHER doc in English under /content</p>"}
        [{:lang "en" :slug "other"}]

        {:html "<p>OTHER doc in French under /content</p>"}
        [{:lang "fr" :slug "other"}]

        {:html "<p>.ext doc in English under /content</p>"}
        [{:lang "en" :slug "page"} {:ext ".ext"}]

        {:title ["Whoa, Meta!"]
         :html "<p>Doc with metadata</p>"}
        [{:lang "en" :slug "meta"}]

        ;; Ignore meta data
        {:html "<p>Title: Whoa, Meta!</p><p>Doc with metadata</p>"}
        [{:lang "en" :slug "meta"} {:parse-meta? false}]

        ;; With custom parser
        {:html "<div>Markdown doc in English under /content</div>"}
        [{:lang "en" :slug "page"}
         {:parse (fn [markdown]
                   {:html (str "<div>" markdown "</div>")})}]

        ;; With custom lang & slug param keys
        {:html "<p>Markdown doc in English under /content</p>"}
        [{:custom-lang "en" :custom-slug "page"}
         {:lang-param :custom-lang :slug-param :custom-slug}]))))

(deftest test-request-creator
  (are
    [req args]
    (= req (let [[file config] args
                 creator (static/request-creator config)]
             (creator file config)))

    {:uri "/en/one"}
    ["/var/www/content/en/one.md" {:dir "/var/www/content"
                                   :ext ".md"}]

    {:uri "/en/one"}
    ["/var/www/content/en/one.markdown" {:dir "/var/www/content"
                                         :ext ".markdown"}]

    {:uri "/override"}
    ["path.md" {:path->req (constantly {:uri "/override"})}]))

(comment
  (k/run))
