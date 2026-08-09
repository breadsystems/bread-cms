(ns systems.bread.alpha.markdown-test
  (:require
    [clojure.test :refer [are deftest]]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded]]
    [systems.bread.alpha.plugin.markdown :as markdown]))

(deftest join-metadata-hook
  (let [metadata* {:a ["first" "second" "third"]}]
    (are
      [expected markdown-config dispatcher hooks]
      (= expected (let [app (plugins->loaded [(markdown/plugin markdown-config)
                                              {:hooks hooks}])
                        req (assoc app ::bread/dispatcher dispatcher)
                        content {:metadata metadata*}
                        result (bread/hook req ::markdown/parsed content)]
                    (:metadata result)))

      {:a "first\nsecond\nthird"}
      {}
      {:dispatcher/type ::markdown/page=>}
      nil

      {:a "first\nsecond\nthird"}
      {:join-metadata? true}
      {:dispatcher/type ::markdown/page=>}
      nil

      metadata*
      {:join-metadata? false}
      {:dispatcher/type ::markdown/page=>}
      nil

      metadata*
      {:join-metadata? nil}
      {:dispatcher/type ::markdown/page=>}
      nil

      {:a "first\nsecond\nthird"}
      {:join-metadata? false}
      {:dispatcher/type ::markdown/page=>
       :join-metadata? true}
      nil

      metadata*
      {:join-metadata? true}
      {:dispatcher/type ::markdown/page=>
       :join-metadata? false}
      nil

      metadata*
      {:join-metadata? true}
      {:dispatcher/type ::markdown/page=>
       :join-metadata? true}
      {::markdown/join-metadata? [{:action/name ::bread/value
                                          :action/value false}]}

      {:a "first\nsecond\nthird"}
      {:join-metadata? false}
      {:dispatcher/type ::markdown/page=>}
      {::markdown/join-metadata? [{:action/name ::bread/value
                                          :action/value true}]}

      ,)))

(deftest test-markdown-expansion
  (let [mock-fs
        {"public/en/page.md"  "Markdown doc in English under /public"
         "public/en/meta.md"  "Title: Whoa, Meta!\n\nDoc with metadata"
         "public/en/multi.md" "Title: One\n    Two\n\nDoc with multi-line metadata"}]
    (with-redefs [clojure.java.io/resource str
                  slurp mock-fs]
      (are
        [expected expansion* config]
        (= expected (let [app {::bread/config config
                               ::bread/hooks
                               {::markdown/parsed
                                [{:action/name ::markdown/join-metadata}]}}
                          hook (partial bread/hook app)
                          expansion (assoc expansion* :hook hook)]
                      (bread/expand expansion {})))

        nil
        {:expansion/name ::markdown/page
         :filepaths ["non-existent/file/path"]}
        {}

        nil
        {:expansion/name ::markdown/page
         :filepaths ["non-existent/file/path" "another/non-existent/path"]}
        {}

        {:metadata nil
         :html "<p>Markdown doc in English under /public</p>"}
        {:expansion/name ::markdown/page
         :filepaths ["public/en/page.md"]}
        {}

        {:metadata {:title ["Whoa, Meta!"]}
         :html "<p>Doc with metadata</p>"}
        {:expansion/name ::markdown/page
         :filepaths ["public/en/meta.md"]
         :hook (partial bread/hook {::bread/hooks
                                    {::markdown/parsed
                                     [{:action/name ::markdown/join-metadata}]}})}
        {}

        {:metadata {:title "Whoa, Meta!"}
         :html "<p>Doc with metadata</p>"}
        {:expansion/name ::markdown/page
         :filepaths ["public/en/meta.md"]}
        {:markdown/join-metadata? true}

        {:metadata {:title "One\nTwo"}
         :html "<p>Doc with multi-line metadata</p>"}
        {:expansion/name ::markdown/page
         :filepaths ["public/en/multi.md"]}
        {:markdown/join-metadata? true}

        ,))))

;; TODO test ::markdown=>

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
