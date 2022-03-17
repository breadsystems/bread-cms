(ns breadbox.data
  (:import
    [java.util UUID]))

(def initial-content
  [{:db/id "page.home"
    :post/type :post.type/page
    :post/slug ""
    :post/fields #{{:field/key :title
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
                                             :img-url "https://via.placeholder.com/300"})}}
    :post/status :post.status/published}
   {:db/id "page.child"
    :post/type :post.type/page
    :post/slug "child-page"
    :post/status :post.status/published
    :post/fields #{{:field/key :title
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
    :post/taxons #{{:taxon/slug "my-cat"
                    :taxon/name "My Cat"
                    :taxon/taxonomy :taxon.taxonomy/category}}}
   {:db/id "page.parent"
    :post/type :post.type/page
    :post/slug "parent-page"
    :post/children ["page.child"]
    :post/status :post.status/published
    :post/fields #{{:field/key :title
                    :field/lang :en
                    :field/content (prn-str "Parent Page")}
                   {:field/key :title
                    :field/lang :fr
                    :field/content (prn-str "La Page Parent")}
                   {:field/key :simple
                    :field/lang :en
                    :field/content
                    (prn-str {:hello "Greetings!"
                              :body "Lorem ipsum dolor sit amet"
                              :goodbye "Goodbye from Parent!"
                              :img-url "https://via.placeholder.com/300"})}
                   {:field/key :simple
                    :field/lang :fr
                    :field/content
                    (prn-str {:hello "Ca va?"
                              :body "Lorem ipsum en francais"
                              :goodbye "Salut de la page parent"
                              :img-url "https://via.placeholder.com/300"})}}}
   #:menu{:locations [:main-nav]
          :key :main
          ;; TODO use UUIDs to accomplish this :P
          :menu/content (prn-str [{:db/id 59     ;; parent
                                   :children
                                   [{:db/id 52}]} ;; child
                                  {:db/id 47}    ;; home
                                  ])}
   #:menu{:locations [:footer-nav]
          :key :footer
          :menu/content (prn-str [{:db/id 47}
                                  {:db/id 52}
                                  {:db/id 59}])}
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
