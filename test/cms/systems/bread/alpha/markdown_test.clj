(ns systems.bread.alpha.markdown-test
  (:require
    [clojure.test :refer [are deftest]]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]
    [systems.bread.alpha.plugin.markdown :as markdown]))

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

        ;; TODO singularize-metadata-keys

        ,))))

;; TODO test ::markdown=>

(comment
  (require '[kaocha.repl :as k])
  (k/run))
