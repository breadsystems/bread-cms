# BreadCMS

<p style="font-weight:700;color:#800">WORK IN PROGRESS. Code is pre-alpha status. A lot of this does not work yet.</p>

<blockquote style="text-align:center">After bread has been secured, leisure is the supreme aim.
<p>— Pyotr Kropotkin</p>
</blockquote>

Liberate your content.

## Basic Usage

```clojure
(ns my-project
    (:require [systems.bread.alpha.core :as bread]))

(defn hello [_]
    [:h1 "Hello, Breadsters!"])

(def handler
    (-> (bread/app)
        (bread/post-type :page
                         {:browse hello})
        (bread/app->handler)))

(handler {:uri "/"})
;; => [:h1 "Breadsters!"]
```

### A more detailed example

```clojure
;; namespace where your blog post type is defined
(ns my.project.blog
    (:require [systems.bread.alpha.finders :as finders]
              [my.project.helpers :as h])
    (:import [java.text SimpleDateFormat]))

(defn main-layout [{:keys [title headline content]}]
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title (or title "My Cool Blog")]]
     [:body
      [:header
       [:h1 (or headline title "My Cool Blog")]]
      [:main
       content]
      [:footer
       [:div "Main footer content"]]]])

;; Normal actions use main-layout. These are your standard
;; Read, Edit, Add, and Delete, AKA BREAD operations, minus the B for Browse.
(defn details [_] ...)
(defn edit [_] ...)
(defn add [_] ...)
(defn delete [_] ...)

(def fmt (SimpleDateFormat. "MMMM d"))

(defn blog-card [{:post/keys [title excerpt published]}]
    [:article.blog-card
     [:h1 title]
     [:h2 (.format fmt published)]
     [:div.excerpt excerpt]])

(defn finder-layout [{:keys [posts-content params]}]
    (main-layout
        {:title "Blog search"
         :headline (str "Posted in " (h/month-and-year (:month params))))
         :content
         [:<>
          [:div.blog-cards
           posts-content]
          [:section.pagination
           ;; (h/prev-month "2020-11") => "2020-10"
           [:a {:href (finders/filter-url {:month (h/prev-month (:month param))})}
            "Previous month"]
           ;; (h/next-month "2020-11") => "2020-12"
           [:a {:href (finders/filter-url {:month (h/next-month (:month param))})}
            "Next month"]]]}))

(def find (finders/finder
              {:query
               ;; Add custom logic for querying posts published in
               ;; the given month. See:
               ;; https://grishaev.me/en/datomic-query/
               {:month
                (fn [query month]
                    (-> query
                        (update :in conj '?start)
                        (update :in conj '?end)
                        (update :args conj (h/start-of-month month))
                        (update :args conj (h/end-of-month month))
                        (update :where conj
                                '[(> :post/published ?start)
                                  (< :post/published ?end)])))}
               :render-post blog-card
               :layout finder-layout}))

;; top-level namespace for your app
(ns my-project
    (:require [my.project.blog :as blog]
              [systems.bread.alpha.core :as bread]))

(def handler (-> (bread/app)
                 (bread/post-type :blog-post
                                  {:browse blog/find
                                   :read   blog/details
                                   :edit   blog/edit
                                   :add    blog/add
                                   :delete blog/delete})
                 (bread/app->handler)))

(handler {:uri "/posts"
          :query-string "month=2020-11"})
;; =>
[:html
 [:head
  [:meta {:charset "utf-8"}]
  [:title "Blog search"]]
 [:body
  [:header
   [:h1 "Posted in November 2020"]]
  [:main
   [:div.blog-cards
    [:article.blog-card
     [:h1 "Last post in November"]
     [:h3 "Nov 27"]
     [:div.excerpt "Lorem ipsum dolor sit amet."]]
    [:article.blog-card
     [:h1 "Another post in November]
     [:h3 "Nov 26"]
     [:div.excerpt "Impedit deleniti mollit tempor fuga ea anim quos."]]
    [:article.blog-card
     [:h1 "Yet another post from November"]
     [:h3 "Nov 18"]
     [:div.excerpt "Magna distinctio eu fugiat possimus mollitia."]]
    ...]
   [:section.pagination
    [:a {:href "?month=2020-10"} "Previous Month"]
    [:a {:href "?month=2020-11"} "Next Month"]]
  [:footer
   [:p "Main footer content"]]]]
```

### Resolvers

Resolvers in BreadCMS are conceptually similar to GraphQL/Pathom resolvers, but operate at a higher level of abstraction. They are responsible for taking a Ring request and resolving it to one or more Posts. As such, they can take arbitrary data on the request into account.

The standard, built-in resolvers use post slugs and parent/child post hierarchies as their criteria for resolving posts. For example, when handling a request for `/parent/child`, the standard post resolver queries for a post:

1. whose slug is `"child"`, and
2. whose parent is a post whose slug is `"parent"`

You can declare resolvers to trigger specifically for certain routes:

```clj
;; TODO what does this API actually look like?
(bread/route "/parent" {:route/key :parent
                        :route/resolver {:post/type :post.type/page
                                         :post/parent nil}})
```

In a custom routing scheme that declares a top-level `/parent/*` route (i.e. a wildcard route dispatched by slug underneath the umbrella `/parent` route),  Bread will recognize the fact that we are no longer looking for a parent page whose slug is `"parent"`, but are instead looking for posts of a certain type, or status, or whatever other criteria you want to define for your custom route.

Bread also has an API for defining your own resolvers that operate on arbitrary request data.