;; TODO figure out how themes work :P
(ns systems.bread.alpha.cms.theme
  (:require
    [clojure.walk :as walk]
    [rum.core :as rum]

    [systems.bread.alpha.user :as user]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc] :as component]
    [systems.bread.alpha.plugin.marx :as marx]))

(defn title [& strs]
  (clojure.string/join " | " (filter seq strs)))

(defn- pp [x]
  (with-out-str (clojure.pprint/pprint x)))

(defn name->id [cname]
  (str "theme-cpt-" (name cname)))

(defc TableOfContents [{:as data :keys [sections]}]
  [:nav
   [:h1#contents "Table of contents"]
   [:ul
    [:<> (doall (map (fn [{:keys [component id title]}]
                       (let [{:keys [id title]}
                             (if-let [cmeta (meta component)]
                               {:id (:name cmeta)
                                :title (:name cmeta)}
                               {:id id :title title})]
                         [:li [:a {:href (str "#" (name id))} title]]))
                     sections))]]])

(defn- remove-noop-elements [html]
  (walk/postwalk (fn [x]
                   (if (and (vector? x) (not (map-entry? x)))
                     (filterv (complement (partial contains? #{nil [:<>]})) x)
                     x)) html))

(defc DocSection [{:keys [content id title]}]
  [:section {:id id}
   [:h1 title]
   content
   [:a {:href "#contents"} "Back to top"]])

(defc ComponentSection [{:keys [component]}]
  (let [{:as cmeta cname :name
         :keys [doc doc/show-html? doc/default-data expr examples]
         :or {show-html? true}}
        (meta component)]
    (when (= "Page" cname)
      (def component component)
      (comment
        (meta component)
        (merge (:doc/default-data (meta component)))
        (remove-noop-elements (apply list (symbol (:name (meta component))) args))
        ))
    [:article {:id cname :data-component cname}
     [:h1 cname]
     [:p doc]
     (map (fn [{:keys [doc description args]}]
            (let [args' (cons (merge default-data (first args)) (rest args))]
              [:section.example
               [:h2 doc]
               [:p description]
               [:pre (pp (apply list (symbol cname) args))]
               ;; TODO toggle these
               [:pre (pp (remove-noop-elements (apply component args')))]
               (when show-html?
                 [:pre (rum/render-static-markup (apply component args'))])]))
          examples)
     [:details
      [:summary "Show source"]
      [:pre (pp (apply list 'defc (symbol cname) expr))]]
     [:a {:href "#contents"} "Back to top"]]))

(defn pattern->section [pattern]
  (if (= ::component/component (:type (meta pattern)))
    {:component pattern}
    pattern))

(defn ns->patterns [ns*]
  (let [pattern? #(:doc/pattern (meta %) true)
        xform (comp (map (comp deref val))
                    (filter pattern?))]
    (into [] xform (ns-publics ns*))))

(comment
  (map
    pattern->section
    (ns->patterns 'systems.bread.alpha.cms.theme.crust))

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
