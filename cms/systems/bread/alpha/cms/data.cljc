(ns systems.bread.alpha.cms.data
  (:require
    [systems.bread.alpha.i18n :as i18n]))

(def initial
  [{:db/id "page.home"
    :post/type :page
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
       :field/format :html
       :field/content
       "<h2>Some content</h2>
<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam vel egestas sapien, vel gravida lacus. Morbi placerat lorem justo, luctus pretium sem mattis ac. Maecenas convallis lorem a enim iaculis luctus. Pellentesque malesuada elit et sodales efficitur. Integer quis blandit felis. Aenean fringilla magna quis lacinia egestas. Praesent neque mauris, aliquam quis elementum quis, sagittis sit amet diam. Nulla eu scelerisque tortor, nec condimentum erat.</p>
<h2>More content</h2>
<p>Cras tellus purus, ultricies nec augue ut, lacinia dignissim leo. Quisque eros erat, semper ut ornare et, mattis at ipsum. Pellentesque odio dui, hendrerit sollicitudin ultricies fringilla, feugiat at turpis. Curabitur quam felis, dapibus mollis tellus a, accumsan euismod mauris. Suspendisse dictum commodo arcu, eget pharetra nibh aliquet ut. Duis accumsan nec leo a dignissim. Nam nunc massa, vulputate non neque vel, convallis congue nisi. Maecenas ut cursus orci.</p>
<p>Duis ipsum nunc, gravida ut est sit amet, facilisis luctus enim. Maecenas eget sapien aliquam odio luctus eleifend sed sit amet metus. Nulla malesuada efficitur odio. Aenean ligula ipsum, faucibus maximus consectetur eget, condimentum placerat nibh. Nunc laoreet luctus velit vel dignissim. Pellentesque hendrerit purus et leo tincidunt, egestas finibus turpis tempor. Curabitur ut ligula sit amet justo vulputate condimentum. Phasellus aliquet magna et est convallis blandit. Nullam eros magna, ornare at tempus et, pellentesque non justo.</p>
<p>Integer faucibus mauris quis lobortis consequat. Sed ac congue arcu. Nunc convallis, massa non imperdiet consequat, mi nibh iaculis elit, mollis dignissim ipsum quam vitae felis. Aliquam laoreet tellus odio, nec molestie erat pretium sed. Quisque lacus tortor, hendrerit ut nulla at, rhoncus malesuada velit. Nunc rhoncus interdum mi, nec dictum nisi ullamcorper vel. Fusce sed porta felis. Suspendisse sagittis ornare nulla. Suspendisse potenti. Sed sed ligula malesuada, pulvinar mauris sed, vehicula sapien. Vestibulum nec ipsum quis orci condimentum volutpat. Quisque sollicitudin mi in enim mollis, ac pretium enim interdum.</p>
<p>Aenean nunc nulla, finibus sed tortor eget, lobortis viverra tortor. In in orci maximus, mollis nulla finibus, blandit nunc. Nulla facilisi. Morbi quis nisi a massa consequat porta nec ut turpis. Proin varius porttitor euismod. Vivamus elit felis, suscipit vel ullamcorper luctus, eleifend vel lacus. Vivamus vestibulum elit quis volutpat commodo. Curabitur dapibus porttitor ultrices. Etiam facilisis lorem vitae diam pulvinar, at ullamcorper augue rutrum.</p>
<h2>And some more...</h2>
<p>Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum tortor quam, feugiat vitae, ultricies eget, tempor sit amet, ante. Donec eu libero sit amet quam egestas semper. Aenean ultricies mi vitae est. Mauris placerat eleifend leo. Quisque sit amet est et sapien ullamcorper pharetra. Vestibulum erat wisi, condimentum sed, commodo vitae, ornare sit amet, wisi. Aenean fermentum, elit eget tincidunt condimentum, eros ipsum rutrum orci, sagittis tempus lacus enim ac dui. Donec non enim in turpis pulvinar facilisis. Ut felis. Praesent dapibus, neque id cursus faucibus, tortor neque egestas augue, eu vulputate magna eros eu erat. Aliquam erat volutpat. Nam dui mi, tincidunt quis, accumsan porttitor, facilisis luctus, metus</p>
<p>Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum tortor quam, feugiat vitae, ultricies eget, tempor sit amet, ante. Donec eu libero sit amet quam egestas semper. Aenean ultricies mi vitae est. Mauris placerat eleifend leo.</p>
<p>Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.</p>"
       ,}
      {:field/key :content
       :field/lang :fr
       :field/format :html
       :field/content
       "<h2>Le content</h2>
<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam vel egestas sapien, vel gravida lacus. Morbi placerat lorem justo, luctus pretium sem mattis ac. Maecenas convallis lorem a enim iaculis luctus. Pellentesque malesuada elit et sodales efficitur. Integer quis blandit felis. Aenean fringilla magna quis lacinia egestas. Praesent neque mauris, aliquam quis elementum quis, sagittis sit amet diam. Nulla eu scelerisque tortor, nec condimentum erat.</p>
<h2>Et plus</h2>
<p>Cras tellus purus, ultricies nec augue ut, lacinia dignissim leo. Quisque eros erat, semper ut ornare et, mattis at ipsum. Pellentesque odio dui, hendrerit sollicitudin ultricies fringilla, feugiat at turpis. Curabitur quam felis, dapibus mollis tellus a, accumsan euismod mauris. Suspendisse dictum commodo arcu, eget pharetra nibh aliquet ut. Duis accumsan nec leo a dignissim. Nam nunc massa, vulputate non neque vel, convallis congue nisi. Maecenas ut cursus orci.</p>
<p>Duis ipsum nunc, gravida ut est sit amet, facilisis luctus enim. Maecenas eget sapien aliquam odio luctus eleifend sed sit amet metus. Nulla malesuada efficitur odio. Aenean ligula ipsum, faucibus maximus consectetur eget, condimentum placerat nibh. Nunc laoreet luctus velit vel dignissim. Pellentesque hendrerit purus et leo tincidunt, egestas finibus turpis tempor. Curabitur ut ligula sit amet justo vulputate condimentum. Phasellus aliquet magna et est convallis blandit. Nullam eros magna, ornare at tempus et, pellentesque non justo.</p>
<p>Integer faucibus mauris quis lobortis consequat. Sed ac congue arcu. Nunc convallis, massa non imperdiet consequat, mi nibh iaculis elit, mollis dignissim ipsum quam vitae felis. Aliquam laoreet tellus odio, nec molestie erat pretium sed. Quisque lacus tortor, hendrerit ut nulla at, rhoncus malesuada velit. Nunc rhoncus interdum mi, nec dictum nisi ullamcorper vel. Fusce sed porta felis. Suspendisse sagittis ornare nulla. Suspendisse potenti. Sed sed ligula malesuada, pulvinar mauris sed, vehicula sapien. Vestibulum nec ipsum quis orci condimentum volutpat. Quisque sollicitudin mi in enim mollis, ac pretium enim interdum.</p>
<p>Aenean nunc nulla, finibus sed tortor eget, lobortis viverra tortor. In in orci maximus, mollis nulla finibus, blandit nunc. Nulla facilisi. Morbi quis nisi a massa consequat porta nec ut turpis. Proin varius porttitor euismod. Vivamus elit felis, suscipit vel ullamcorper luctus, eleifend vel lacus. Vivamus vestibulum elit quis volutpat commodo. Curabitur dapibus porttitor ultrices. Etiam facilisis lorem vitae diam pulvinar, at ullamcorper augue rutrum.</p>
<h2>Et maintenant, plus...</h2>
<p>Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum tortor quam, feugiat vitae, ultricies eget, tempor sit amet, ante. Donec eu libero sit amet quam egestas semper. Aenean ultricies mi vitae est. Mauris placerat eleifend leo. Quisque sit amet est et sapien ullamcorper pharetra. Vestibulum erat wisi, condimentum sed, commodo vitae, ornare sit amet, wisi. Aenean fermentum, elit eget tincidunt condimentum, eros ipsum rutrum orci, sagittis tempus lacus enim ac dui. Donec non enim in turpis pulvinar facilisis. Ut felis. Praesent dapibus, neque id cursus faucibus, tortor neque egestas augue, eu vulputate magna eros eu erat. Aliquam erat volutpat. Nam dui mi, tincidunt quis, accumsan porttitor, facilisis luctus, metus</p>
<p>Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Vestibulum tortor quam, feugiat vitae, ultricies eget, tempor sit amet, ante. Donec eu libero sit amet quam egestas semper. Aenean ultricies mi vitae est. Mauris placerat eleifend leo.</p>
<p>Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.</p>"
       ,}}}
   {:db/id "page.child"
    :post/type :page
    :thing/slug "child-page"
    :post/status :post.status/published
    :post/taxons ["tag.two"]
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
    :post/type :page
    :thing/slug "daughter-page"
    :post/status :post.status/draft
    :post/taxons ["tag.one"]
    :thing/fields
    #{{:field/key :title
       :field/lang :en
       :field/content "Daughter Page"}
      {:field/key :title
       :field/lang :fr
       :field/content "La Page Fille"}}}
   {:db/id "page.parent"
    :post/type :page
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
       :field/format :html
       :field/content
       "<h1>This will be demoted to a &lt;p&gt; by default</h1>
       <h2>Level 2 heading</h2>
       <h3>Level 3 sub-heading</h3>
       <h4>Level 4 sub-heading</h4>
       <h5>Level 5 sub-heading</h5>
       <h6>Level 6 sub-heading</h6>
       <p>This is some paragraph text with some special characters. <>'\"&-</p>
       <ul>
        <li>some</li>
        <li>list</li>
        <li>items</li>
       </ul>
       <hr>
       <ol>
        <li>some</li>
        <li>ordered</li>
        <li>list</li>
        <li>items</li>
       </ol>
       <p>This isn't code but <code>this is some inline code.</code>
       <pre><code> (println \"Hello, World!\")</code></pre>
       <p>Here is some prose with <sup>superscript</sup> and some with <sub>subscript</sub>
          Now, here is some <del>struck text</del> and some <mark>highlighted text</mark>.
       <p>Here is a paragraph<br>with some line breaks</br>in the middle of it.
       "}}}
   {:db/id "page.grandchild"
    :post/type :page
    :thing/slug "grandchild-page"
    :post/status :post.status/published
    :post/taxons ["tag.one" "tag.two"]
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

   ;; Media
   {:post/type :media
    :thing/slug "cat.jpeg"
    :post/taxons ["tag.one" "tag.two"]
    :post/status :post.status/published
    :thing/fields
    #{{:field/key :uri
       :field/content "/assets/cat.jpeg"}
      {:field/key :alt-text
       :field/lang :en
       :field/content "Kitty"}
      {:field/key :alt-text
       :field/lang :fr
       :field/content "un chat"}}}
   {:post/type :media
    :thing/slug "dog.jpg"
    :post/taxons ["tag.one"]
    :post/status :post.status/published
    :thing/fields
    #{{:field/key :uri
       :field/content "/assets/dog.png"}
      {:field/key :alt-text
       :field/lang :en
       :field/content "Doggo"}
      {:field/key :alt-text
       :field/lang :fr
       :field/content "un chien"}}}

   ;; Site-wide translations
   {:field/lang :en
    :field/key :not-found
    :field/content "404 Not Found"}
   {:field/lang :es
    :field/key :not-found
    :field/content "404 Pas Trouvé"}])
