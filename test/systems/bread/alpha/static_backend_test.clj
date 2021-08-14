(ns systems.bread.alpha.static-backend-test
  (:require
    [clojure.test :refer [are deftest is]]
    [kaocha.repl :as k]
    [markdown.core :as md]
    [systems.bread.alpha.plugin.static-backend :as static]))

(k/run (deftest test-query-fs
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

        {:html "<p>Markdown doc in English under /content</p>"}
        [{:lang "en" :slug "page"}
         {:root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        {:html "<p>Markdown doc in English under /alt</p>"}
        [{:lang "en" :slug "page"}
         {:root "alt" :ext ".md" :lang-param :lang :slug-param :slug}]

        {:html "<p>Markdown doc in French under /content</p>"}
        [{:lang "fr" :slug "page"}
         {:root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        {:html "<p>Markdown doc in French under /alt</p>"}
        [{:lang "fr" :slug "page"}
         {:root "alt" :ext ".md" :lang-param :lang :slug-param :slug}]

        {:html "<p>OTHER doc in English under /content</p>"}
        [{:lang "en" :slug "other"}
         {:root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        {:html "<p>OTHER doc in French under /content</p>"}
        [{:lang "fr" :slug "other"}
         {:root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        {:html "<p>.ext doc in English under /content</p>"}
        [{:lang "en" :slug "page"}
         {:root "content" :ext ".ext" :lang-param :lang :slug-param :slug}]

        {:title ["Whoa, Meta!"]
         :html "<p>Doc with metadata</p>"}
        [{:lang "en" :slug "meta"}
         {:root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        ;; Ignore meta data
        {:html "<p>Title: Whoa, Meta!</p><p>Doc with metadata</p>"}
        [{:lang "en" :slug "meta"}
         {:parse-meta? false
          :root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        ;; With custom parser
        {:html "<div>Markdown doc in English under /content</div>"}
        [{:lang "en" :slug "page"}
         {:parse (fn [markdown]
                   {:html (str "<div>" markdown "</div>")})
          :root "content" :ext ".md" :lang-param :lang :slug-param :slug}]

        ;; With custom lang & slug param keys
        {:html "<p>Markdown doc in English under /content</p>"}
        [{:custom-lang "en" :custom-slug "page"}
         {:root "content" :ext ".md"
          :lang-param :custom-lang :slug-param :custom-slug}]
        )))))

(comment
  (k/run))
