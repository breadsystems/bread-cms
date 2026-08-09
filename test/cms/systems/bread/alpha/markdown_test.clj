(ns systems.bread.alpha.markdown-test
  (:require
    [clojure.test :refer [are deftest]]
    [markdown.core :as md]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]
    [systems.bread.alpha.plugin.markdown :as markdown]))

(deftest test-query-fs
  (let [mock-fs
        {"content/en/page.md"  "Markdown doc in English under /content"
         "alt/en/page.md"      "Markdown doc in English under /alt"
         "content/fr/page.md"  "Markdown doc in French under /content"
         "alt/fr/page.md"      "Markdown doc in French under /alt"
         "content/en/other.md" "OTHER doc in English under /content"
         "content/fr/other.md" "OTHER doc in French under /content"
         "content/en/page.ext" ".ext doc in English under /content"
         "content/en/meta.md"  "Title: Whoa, Meta!\n\nDoc with metadata"}
        default-opts {:root "content"
                      :ext ".md"
                      :lang-param :lang
                      :slug-param :slug
                      :parse md/md-to-html-string-with-meta}]
    (with-redefs [clojure.java.io/resource str
                  slurp mock-fs]
      (are
        [content args]
        (= content (let [[params opts] args]
                     (markdown/query-fs {} params (merge default-opts opts))))

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
        [{:lang "en" :slug "meta"} {:parse md/md-to-html-string}]

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
                 creator (markdown/request-creator config)]
             (markdown/create-request creator file config)))

    {:uri "/en/one"}
    ["/var/www/content/en/one.md" {:dir "/var/www/content"
                                   :ext ".md"}]

    {:uri "/en/one"}
    ["/var/www/content/en/one.markdown" {:dir "/var/www/content"
                                         :ext ".markdown"}]

    {:uri "/override"}
    ["path.md" {:path->req (constantly {:uri "/override"})}]

    ;; A map with a :uri key is treated as a shorthand for
    ;; a simple URI formatter.
    {:uri "/override"}
    ["/var/www/content/en/one.md" {:dir "/var/www/content"
                                   :path->req
                                   {:uri ["override"]}}]

    {:uri "/a/b/c"}
    ["/var/www/content/a/b/c.md" {:dir "/var/www/content"
                                  :ext ".md"
                                  :path->req
                                  {:uri [0 1 2]}}]

    {:uri "/c/b/a"}
    ["/var/www/content/a/b/c.md" {:dir "/var/www/content"
                                  :ext ".md"
                                  :path->req
                                  {:uri [2 1 0]}}]

    ;; A vector v is shorthand for {:uri v}
    {:uri "/a/b/c"}
    ["/var/www/content/a/b/c.md" {:dir "/var/www/content"
                                  :ext ".md"
                                  :path->req
                                  [0 1 2]}]
    ))

(deftest singularize-metadata-hook
  (let [metadata* {:a [:first :second :third]
                   :b [0 1 2]}]
    (are
      [expected markdown-config dispatcher hooks]
      (= expected (let [app (plugins->loaded [(markdown/plugin markdown-config)
                                              {:hooks hooks}])
                        req (assoc app ::bread/dispatcher dispatcher)
                        content {:metadata metadata*}
                        result (bread/hook req ::markdown/html content)]
                    (:metadata result)))

      {:a :first :b 0}
      {}
      {:dispatcher/type ::markdown/markdown=>}
      nil

      {:a :first :b 0}
      {:singularize-metadata? true}
      {:dispatcher/type ::markdown/markdown=>}
      nil

      metadata*
      {:singularize-metadata? false}
      {:dispatcher/type ::markdown/markdown=>}
      nil

      metadata*
      {:singularize-metadata? nil}
      {:dispatcher/type ::markdown/markdown=>}
      nil

      {:a :first :b 0}
      {:singularize-metadata? false}
      {:dispatcher/type ::markdown/markdown=>
       :singularize-metadata? true}
      nil

      metadata*
      {:singularize-metadata? true}
      {:dispatcher/type ::markdown/markdown=>
       :singularize-metadata? false}
      nil

      metadata*
      {:singularize-metadata? true}
      {:dispatcher/type ::markdown/markdown=>
       :singularize-metadata? true}
      {::markdown/singularize-metadata? [{:action/name ::bread/value
                                          :action/value false}]}

      {:a :first :b 0}
      {:singularize-metadata? false}
      {:dispatcher/type ::markdown/markdown=>}
      {::markdown/singularize-metadata? [{:action/name ::bread/value
                                          :action/value true}]}

      ,)))

(deftest test-markdown-expansion
  (let [mock-fs
        {"public/en/page.md"   "Markdown doc in English under /public"
         "public/en/meta.md"   "Title: Whoa, Meta!\n\nDoc with metadata"}]
    (with-redefs [clojure.java.io/resource str
                  slurp mock-fs]
      (are
        [expected expansion]
        (= expected (bread/expand expansion {}))

        nil
        {:expansion/name ::markdown/markdown
         :filepaths ["non-existent/file/path"]
         :hook (partial bread/hook {})}

        nil
        {:expansion/name ::markdown/markdown
         :filepaths ["non-existent/file/path" "another/non-existent/path"]
         :hook (partial bread/hook {})}

        {:metadata nil
         :html "<p>Markdown doc in English under /public</p>"}
        {:expansion/name ::markdown/markdown
         :filepaths ["public/en/page.md"]
         :hook (partial bread/hook {})}

        {:metadata {:title ["Whoa, Meta!"]}
         :html "<p>Doc with metadata</p>"}
        {:expansion/name ::markdown/markdown
         :filepaths ["public/en/meta.md"]
         :hook (partial bread/hook {::bread/hooks
                                    {::markdown/html
                                     [{:action/name ::markdown/singularize-metadata}]}})}

        {:metadata {:title "Whoa, Meta!"}
         :html "<p>Doc with metadata</p>"}
        {:expansion/name ::markdown/markdown
         :filepaths ["public/en/meta.md"]
         :hook (partial bread/hook {::bread/config
                                    {:markdown/singularize-metadata? true}
                                    ::bread/hooks
                                    {::markdown/html
                                     [{:action/name ::markdown/singularize-metadata}]}})}

        ,))))

(comment
  (require '[kaocha.repl :as k])
  (k/run))
