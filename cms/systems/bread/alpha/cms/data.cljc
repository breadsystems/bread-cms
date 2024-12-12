(ns systems.bread.alpha.cms.data
  (:require
    [systems.bread.alpha.i18n :as i18n]))

(def initial
  [{:db/id "page.home"
    :post/type :post.type/page
    :thing/slug ""
    :post/status :post.status/published
    :thing/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "The Title"}
      {:field/key :title
       :field/lang :fr
       :field/content "Le Titre"}
      {:field/key :content
       :field/lang :en
       :field/format :edn
       :field/content (pr-str [{:a "some content" :b "more content"}])}
      {:field/key :content
       :field/lang :fr
       :field/format :edn
       :field/content (pr-str [{:a "le content" :b "et plus"}])}}}
   {:db/id "page.child"
    :post/type :post.type/page
    :thing/slug "child-page"
    :post/status :post.status/published
    :thing/children ["page.grandchild"]
    :thing/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Child"}
      {:field/key :title
       :field/lang :fr
       :field/content "Enfant"}
      {:field/key :content
       :field/lang :en
       :field/format :edn
       :field/content (pr-str [{:a "lorem ipsum" :b "dolor sit amet"}])}
      {:field/key :content
       :field/lang :fr
       :field/format :edn
       :field/content (pr-str [{:a "loreme ipsumee" :b "dolore siter amet"}])}}}
   {:db/id "page.daughter"
    :post/type :post.type/page
    :thing/slug "daughter-page"
    :post/status :post.status/draft
    :thing/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Daughter Page"}
      {:field/key :title
       :field/lang :fr
       :field/content "La Page Fille"}}}
   {:db/id "page.parent"
    :post/type :post.type/page
    :thing/slug "hello"
    :thing/children ["page.child" "page.daughter"]
    :post/taxons ["tag.one" "tag.two"]
    :post/status :post.status/published
    :thing/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Hello!"}
      {:field/key :title
       :field/lang :fr
       :field/content "Bonjour!"}
      {:field/key :rte
       :field/lang :en
       :field/format :edn
       :field/content
       (pr-str [:<>
                [:h1 "This will be demoted to a <p> by default"]
                ;; TODO headings...?
                [:h2 "This is a heading"]
                [:p "This is some paragraph text."]
                #_ ;; TODO parse imgs
                [:img {:src "/assets/cat.jpeg" :alt "It's a kitty!"}]
                [:ul
                 [:li "some"]
                 [:li "list"]
                 [:li "items"]]
                [:hr]
                [:ol
                 [:li "some"]
                 [:li "numbered"]
                 [:li "list"]
                 [:li "items"]]
                [:p
                 "This isn't code but "
                 [:code "this is some inline code."]]
                [:p "And here's a code block:"]
                [:pre [:code "(println \"Hello, World!\")"]]
                [:p
                 "Here is some prose with "
                 [:sup "superscript"]
                 " and some with "
                 [:sub "subscript"]
                 ". Now, here is some "
                 [:del "struck text"]
                 " and some "
                 [:mark "highlighted text"]
                 "."]
                [:p
                 "Here is a paragraph"
                 [:br]
                 "with some line breaks"
                 [:br]
                 "in the middle of it."]])}}}
   {:db/id "page.grandchild"
    :post/type :post.type/page
    :thing/slug "grandchild-page"
    :post/status :post.status/published
    :thing/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Grandchild Page"}
      {:field/key :title
       :field/lang :fr
       :field/content "Petit Enfant Page"}}}
   {:db/id "tag.one"
    :thing/slug "one"
    :taxon/taxonomy :taxon.taxonomy/tag
    :thing/fields
    [{:field/key :name
      :field/content "One"
      :field/lang :en}
     {:field/key :name
      :field/content "Un"
      :field/lang :fr}]}
   {:db/id "tag.two"
    :thing/slug "two"
    :taxon/taxonomy :taxon.taxonomy/tag
    :thing/fields
    [{:field/key :name
      :field/content "Two"
      :field/lang :en}
     {:field/key :name
      :field/content "Deux"
      :field/lang :fr}]}
   {:db/id "menu-item.zero"
    :menu.item/entity "page.parent"
    :thing/order 0}
   {:db/id "menu-item.one"
    :thing/order 1
    :post/type :post.type/menu-item
    :thing/children ["menu-item.child"]
    :thing/fields
    [{:field/key :title
      :field/content "Thing One"
      :field/lang :en}
     {:field/key :title
      :field/content "La Chose Un"
      :field/lang :fr}
     {:field/key :uri
      :field/format ::i18n/uri
      :field/content (pr-str [:field/lang "thing-one"])}]}
   {:db/id "menu-item.two"
    :thing/order 2
    :post/type :post.type/menu-item
    :thing/fields
    [{:field/key :title
      :field/content "Thing Two"
      :field/lang :en}
     {:field/key :title
      :field/content "La Chose Deux"
      :field/lang :fr}
     {:field/key :uri
      :field/format ::i18n/uri
      :field/content (pr-str [:field/lang "thing-two"])}]}
   {:db/id "menu-item.child"
    :thing/fields
    [{:field/key :uri
      :field/lang :en
      :field/content "/en/child-item"}]
    :thing/children ["menu-item.grandchild"]}
   {:db/id "menu-item.grandchild"
    :thing/fields
    [{:field/key :uri
      :field/lang :en
      :field/content "/en/grandchild-item"}]
    :thing/children []}
   {:menu/key :main-nav
    :menu/locations [:primary]
    :menu/items ["menu-item.zero" "menu-item.one" "menu-item.two"]}

   ;; Site-wide translations
   {:field/lang :en
    :field/key :not-found
    :field/content "404 Not Found"}
   {:field/lang :es
    :field/key :not-found
    :field/content "404 Pas Trouvé"}])
