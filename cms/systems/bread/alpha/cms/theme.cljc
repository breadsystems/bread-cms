;; TODO figure out how themes work :P
(ns systems.bread.alpha.cms.theme
  (:require
    [clojure.walk :as walk]
    [markdown-to-hiccup.core :as md2h]
    [rum.core :as rum]

    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc] :as component]
    [systems.bread.alpha.plugin.marx :as marx]))

(defn title [& strs]
  (clojure.string/join " | " (filter seq strs)))

(defn- pp [x]
  (with-out-str (clojure.pprint/pprint x)))

(defn pattern-type [pattern]
  (or (:type pattern)
      (:type (meta pattern))))

(defmulti ContentsItem pattern-type)
(defmulti Pattern pattern-type)

(defmethod ContentsItem :default [pattern] pattern)

(defmethod ContentsItem ::component/component [component]
  (let [cpt-name (:name (meta component))]
    {:id cpt-name :title cpt-name}))

(defc TableOfContents [{:as data :keys [patterns]}]
  [:nav
   [:h1#contents "Table of contents"]
   [:ul
    [:<> (doall (map (fn [pattern]
                       (let [{:keys [id title]} (ContentsItem pattern)]
                         [:li [:a {:href (str "#" (name id))} title]]))
                     patterns))]]])

(defn- remove-noop-elements [html]
  (walk/postwalk (fn [x]
                   (if (and (vector? x) (not (map-entry? x)))
                     (filterv (complement (partial contains? #{nil [:<>]})) x)
                     x)) html))

(defn- md->hiccup [s]
  (-> s md2h/md->hiccup md2h/component))

(defmethod Pattern :default DocSection [{:keys [content id title]}]
  [:section.pattern {:id id}
   [:h1 title]
   [:a.section-link {:href (str "#" (name id)) :title title} "#"]
   (if (string? content)
     (md->hiccup content)
     content)
   [:a {:href "#contents"} "Back to top"]])

(defmethod Pattern ::component/component ComponentSection [component]
  (let [{:as cmeta cname :name
         :keys [doc doc/show-html? doc/default-data expr examples]
         :or {show-html? true}}
        (meta component)]
    [:article.pattern {:id cname :data-component cname}
     [:h1 cname]
     [:a.section-link {:href (str "#" (name cname))
                       :title (str "Link to " (name cname))}
      "#"]
     (md->hiccup doc)
     (map (fn [{:keys [doc description args]}]
            (let [args' (cons (merge default-data (first args)) (rest args))]
              [:section.example
               [:h2 doc]
               (md->hiccup description)
               [:pre [:code.clj (pp (apply list (symbol cname) args))]]
               [:pre [:code.clj (pp (remove-noop-elements (apply component args')))]]
               [:pre [:code.xml (rum/render-static-markup (apply component args'))]]]))
          examples)
     [:details
      [:summary "Show source"]
      [:pre (pp (apply list 'defc (symbol cname) expr))]]
     [:a {:href "#contents"} "Back to top"]]))

(defn pattern->section [pattern]
  (if (= ::component/component (:type (meta pattern)))
    {:component pattern}
    pattern))

(comment
  (macroexpand
    '(defc P [{:keys [text] :or {text "Default text."}}]
       {:doc "Example description"
        :examples
        '[{:doc "With text"
           :args ({:text "Sample text."})}
          {:doc "With default text"
           :args ({})}]}
       [:p text]))
  (ComponentSection {:component P}))

;; TODO vvvvv DELETE vvvvv

(defn- NavItem [{:keys [children uri]
                       {:keys [title] :as fields} :thing/fields
                       :as item}]
  [:li
   [:div
    [:a {:href uri} title]]
   (map NavItem children)])

(defn- Nav [{items :menu/items}]
  [:nav
   [:ul
    (map NavItem items)]])

(defc MainLayout [{:keys [lang content user]
                   {:keys [main-nav]} :menus
                   :as data}]
  {}
  [:html {:lang lang}
   [:head
    [:meta {:content-type "utf-8"}]
    [:title "hey"]
    [:link {:rel :stylesheet :href "/assets/site.css"}]]
   [:body
    (Nav main-nav)
    content
    (marx/Embed data)]])

(defc NotFoundPage
  [{:keys [lang]}]
  {:extends MainLayout}
  [:main
   "404"])

(defc HomePage
  [{:keys [lang user post]}]
  {:extends MainLayout
   :key :post
   :query '[:thing/children
            :thing/slug
            :post/authors
            {:thing/fields [*]}]}
  {:content
   [:main {:role :main}
    [:h1 (:title (:thing/fields post))]
    [:pre (pr-str post)]
    [:pre (pr-str (user/can? user :edit-posts))]]})

(defc Tag
  [{{fields :thing/fields :as tag} :tag}]
  {:extends MainLayout
   :key :tag
   :query '[:thing/slug
            {:thing/fields [*]}
            {:post/_taxons
             [{:post/authors [*]}
              {:thing/fields [*]}]}]}
  [:main
   [:h1 (:name fields)]
   [:h2 [:code (:thing/slug tag)]]])

(defc InteriorPage
  [{{{:as fields field-defs :bread/fields} :thing/fields tags :post/taxons :as post} :post
    {:keys [main-nav]} :menus
    {:keys [user]} :session
    :keys [hook]}]
  {:extends MainLayout
   :key :post
   :query '[{:thing/fields [*]}
            {:post/taxons [{:thing/fields [*]}]}]}
  (let [Field (partial marx/Field post)]
    [:<>
     [:main
      (Field :text :title :tag :h1)
      [:h2 (:db/id post)]
      (Field :rich-text :rte)
      [:div.tags-list
       [:p "TAGS"]
       (map (fn [{tag :thing/fields}]
              [:span.tag (:name tag)])
            tags)]]]))

(defmethod bread/action ::html.head.pattern-library [req _ [head]]
  (let [head (or head [:<>])]
    (conj head
          [:link {:rel :stylesheet :href "/assets/highlight/styles/atom-one-dark.min.css"}]
          [:script {:src "/assets/highlight/highlight.min.js"}]
          [:script "hljs.highlightAll()"])))

(defn plugin
  ([] (plugin {}))
  ([_]
   {:hooks
    {::html.head.pattern-library
     [{:action/name ::html.head.pattern-library
       :action/description
       "Call this hook inside the <head> of your theme's PatternLibrary component
       to automatically include standard assets, e.g. for syntax highlighting."}]}}))
