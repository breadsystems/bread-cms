(ns breadbox.data
  (:import
    [java.util UUID]))

(def parent-uuid
  #uuid "cd255e79-93c3-447c-ad94-045758de9b31")

(def initial-content
  [#:post{:type :post.type/page
          :uuid (UUID/randomUUID)
          :slug ""
          :fields #{{:field/key :title
                     :field/lang :en
                     :field/content (prn-str "The Title")}
                    {:field/key :title
                     :field/lang :fr
                     :field/content (prn-str "Le Titre")}
                    {:field/key :simple
                     :field/lang :en
                     :field/content (prn-str {:hello "Hi!"
                                              :img-url "https://via.placeholder.com/300"})}
                    {:field/key :simple
                     :field/lang :fr
                     :field/content (prn-str {:hello "Allo!"
                                              :img-url "https://via.placeholder.com/300"})}
                    }
          :status :post.status/published}
   #:post{:type :post.type/page
          :uuid parent-uuid
          :slug "parent-page"
          :status :post.status/published
          :fields #{{:field/key :title
                     :field/lang :en
                     :field/content (prn-str "Parent Page")}
                    {:field/key :title
                     :field/lang :fr
                     :field/content (prn-str "La Page Parent")}
                    }}
   #:post{:type :post.type/page
          :uuid (UUID/randomUUID)
          :slug "child-page"
          :status :post.status/published
          :parent [:post/uuid parent-uuid]
          :fields #{{:field/key :title
                     :field/lang :en
                     :field/content (prn-str "Child Page")}
                    {:field/key :title
                     :field/lang :fr
                     :field/content (prn-str "La Page Enfant")}
                    {:field/key :simple
                     :field/lang :en
                     :field/content
                     (prn-str {:hello "Hello"
                               :body "Lorem ipsum dolor sit amet"
                               :goodbye "Bye!"
                               :img-url "https://via.placeholder.com/300"})}
                    {:field/key :simple
                     :field/lang :fr
                     :field/content
                     (prn-str {:hello "Bonjour"
                               :body "Lorem ipsum en francais"
                               :goodbye "Salut"
                               :img-url "https://via.placeholder.com/300"})}
                    {:field/key :flex-content
                     :field/lang :en
                     :field/content (prn-str {:todo "TODO"})}}
          :taxons #{{:taxon/slug "my-cat"
                     :taxon/name "My Cat"
                     :taxon/taxonomy :taxon.taxonomy/category}}}
   #:menu{:locations [:main-nav]
          :key :main
          :menu/content (prn-str [{:post/id 52     ;; parent
                                   :children
                                   [{:post/id 55}]} ;; child
                                  {:post/id 47}    ;; home
                                  ])}
   #:menu{:locations [:footer-nav]
          :key :footer
          :menu/content (prn-str [{:post/id 47}
                                  {:post/id 52}
                                  {:post/id 55}])}
   #:i18n{:lang :en
          :key :not-found
          :string "404 Not Found"}
   #:i18n{:lang :fr
          :key :not-found
          :string "404 Pas Trouvé"}
   #:i18n{:lang :fr
          :key :breadbox
          :string "Boite à pain"}
   #:i18n{:lang :en
          :key :breadbox
          :string "Breadbox"}
   ])
