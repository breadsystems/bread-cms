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
  (let [component-meta (meta component)]
    {:id (:name component-meta)
     :title (:name component-meta)
     :children (map (fn [example]
                      (assoc example :type :example))
                    (:examples component-meta))}))

(defn- ->id [s]
  (apply str (map (fn [c] (if (Character/isWhitespace c) \_ c)) s)))

(comment
  (->id "How to do stuff"))

(defc TableOfContents [{:as data :keys [patterns]}]
  [:nav
   [:h1#contents "Table of contents"]
   [:ul
    [:<> (doall (map (fn [pattern]
                       (let [{:keys [id title children]} (ContentsItem pattern)]
                         [:li
                          [:a {:href (str "#" (name id))} title]
                          [:ul
                           (map (fn [{:as child :keys [doc]}]
                                  (when doc
                                    [:li [:a {:href (str "#" (->id doc))} doc]]))
                                children)]]))
                     patterns))]]])

(defn- remove-noop-elements [html]
  (walk/postwalk (fn [x]
                   (if (and (vector? x) (not (map-entry? x)))
                     (filterv (complement (partial contains? #{nil [:<>]})) x)
                     x)) html))

(defn- md->hiccup [s]
  (when s (-> s md2h/md->hiccup md2h/component)))

(defn- html-comment [s]
  (str "<!-- " s " -->\n"))

(defn- render-html [content]
  (if (map? content)
    (mapcat (juxt (comp html-comment name key)
                           (comp #(str % "\n")
                                 rum/render-static-markup
                                 remove-noop-elements
                                 val))
                     content)
    (rum/render-static-markup content)))

(defmethod Pattern :default DocSection [{:keys [content id title]}]
  [:section.pattern {:id id}
   [:h1 title]
   [:a.section-link {:href (str "#" (name id)) :title title} "#"]
   (if (string? content)
     (md->hiccup content)
     content)
   [:a {:href "#contents"} "Back to top"]])

(defmethod Pattern ::component/component ComponentSection [component]
  (let [{component-name :name
         :keys [doc doc/show-html? doc/default-data expr examples doc/post-render]
         :or {show-html? true
              post-render identity}}
        (meta component)]
    (let [component-name (name component-name)]
      [:article.pattern {:id component-name :data-component component-name}
       [:h1 component-name]
       [:a.section-link {:href (str "#" component-name)
                         :title (str "Link to " component-name)}
        "#"]
       (md->hiccup doc)
       (map (fn [{:as example :keys [doc description args]}]
              (let [post-render (or (:doc/post-render example) post-render)
                    args' (cons (merge-with merge default-data (first args)) (rest args))
                    id (->id doc)
                    content (apply component args')
                    formatted-content (-> content remove-noop-elements post-render pp)
                    formatted-html (-> content post-render render-html)]
                [:section.example {:id id}
                 (when doc
                   [:<>
                    [:h2 doc]
                    [:a.section-link {:href (str "#" id) :title (str "Link to " doc)} "#"]])
                 (md->hiccup description)
                 [:pre [:code.clj (pp (apply list (symbol component-name) args))]]
                 [:pre [:code.clj formatted-content]]
                 [:pre [:code.xml formatted-html]]]))
            examples)
       [:details
        [:summary "Show source"]
        [:pre [:code.clj (pp (apply list 'defc (symbol component-name) expr))]]]
       [:a {:href "#contents"} "Back to top"]])))

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
