(ns breadbox.data
  (:import
    [java.util UUID]))

(def initial-content
  [{:db/id "page.home"
    :post/type :post.type/page
    :post/slug ""
    :post/fields
    #{{:field/key :title
       :field/lang :en
       :field/content (prn-str "The Title")}
      {:field/key :title
       :field/lang :fr
       :field/content (prn-str "Le Titre")}
      {:field/key :simple
       :field/lang :en
       :field/content
       (prn-str {:hello "Hi!"
                 :img-url "https://via.placeholder.com/300"})}
      {:field/key :simple
       :field/lang :fr
       :field/content
       (prn-str {:hello "Allo!"
                 :img-url "https://via.placeholder.com/300"})}}
    :post/status :post.status/published}
   {:db/id "page.child"
    :post/type :post.type/page
    :post/slug "child-page"
    :post/status :post.status/published
    ;; TODO helper tools for generating test data
    :post/fields
    #{{:field/key :title
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
    ;; TODO use unique refs for this
    :post/taxons
    #{{:taxon/slug "my-cat"
       :taxon/name "My Cat"
       :taxon/taxonomy :taxon.taxonomy/category}}}
   {:db/id "page.sister"
    :post/type :post.type/page
    :post/slug "sister-page"
    :post/status :post.status/draft
    ;; TODO helper tools for generating test data
    :post/fields
    #{{:field/key :title
       :field/lang :en
       :field/content (prn-str "Sister Page - DRAFT")}
      {:field/key :title
       :field/lang :fr
       :field/content (prn-str "La Page Soeur")}
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
    :post/taxons
    #{{:taxon/slug "my-cat"
       :taxon/name "My Cat"
       :taxon/taxonomy :taxon.taxonomy/category}}}
   {:db/id "page.parent"
    :post/type :post.type/page
    :post/slug "parent-page"
    :post/children ["page.child"]
    :post/status :post.status/published
    :post/fields
    #{{:field/key :title
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

   ;; Menu items
   {:db/id "menu.item.home"
    :menu.item/entity "page.home"
    :menu.item/order 2
    :menu.item/children []}
   {:db/id "menu.item.parent"
    :menu.item/entity "page.parent"
    :menu.item/order 1
    :menu.item/children ["menu.item.child"]}
   {:db/id "menu.item.child"
    :menu.item/entity "page.child"
    :menu.item/order 0
    :menu.item/children []}

   ;; Global menus
   {:menu/locations [:main-nav]
    :menu/key :main
    :menu/items ["menu.item.home" "menu.item.parent"]}
   {:menu/locations [:footer-nav]
    :menu/key :footer
    :menu/items ["menu.item.home" "menu.item.parent"]}

   ;; Site-wide translations
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
