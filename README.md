# BreadCMS

<p style="font-weight:700;color:#800">WORK IN PROGRESS. Code is pre-alpha status. A lot of this does not work yet.</p>

<blockquote style="text-align:center">After bread has been secured, leisure is the supreme aim.
<p>— Pyotr Kropotkin</p>
</blockquote>

Liberate your content!

## Goals

Bread aims to be three things:

1. A lightweight core library for constructing a totally custom CMS
2. A small set of plugins that compose to create a CMS with sensible defaults
3. A binary (jar and native) that ships with the basic Bread CMS install

Bread's high-level feature set:

* Next-level user experience for editing content. *Design-aware editing. No separate backend UI.*
* Collaborative editing
* Offline first
* Fully static rendering at write time
* First-class support for translation/internationalization
* An extensible default schema supporting many "open-world" content patterns
* Co-located component queries for more advanced data models
* Completely hackable through an extremely simple plugin API

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
;; => [:h1 "Hello, Breadsters!"]
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
     [:h1 "Another post in November"]
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

Resolvers in BreadCMS are conceptually similar to GraphQL/Pathom resolvers, but operate at a higher level of abstraction. They are responsible for taking a Ring request and resolving it to a **query** for one or more objects, such as posts. Resolvers are typically specified at the routing layer.

#### Default resolver

The simplest way to use a resolver is not to specify one at all:

```clj
(bread/route "/my-route")
```

This uses the default resolver, which makes a number of assumptions:

1. you want to query for a single post (as opposed to multiple posts, or for some other content type, such as a taxon)
2. you want to query by slug
3. you want to query only for published posts (unless the user is logged in and can view drafts)
4. you want to query for a page with no parent: that is, if the user wanted a page nested under `/parent-route` they would have navigated to `/parent-route/my-route`.

The default post resolver uses post slugs and parent/child post hierarchies as its criteria for resolving posts. For example, when handling a request for `/parent/child`, the standard post resolver queries for a post:

1. whose slug is `"child"`, and
2. whose parent is a post whose slug is `"parent"`

#### Custom resolvers via maps

```clj
(bread/route "/my-route" {:route/resolver {:post/slug "my-page"}})
```

This simplistic resolver tells Bread to query for a single post with the slug `"my-page"`. It is attached to the custom app route `/my-route`. In effect, this route tells Bread to serve the content for the specific page `my-page` at said route. This can be useful if you know your Information Architecture (IA) ahead of time and you want to save some content entry grunt work.

In a more custom routing scheme, you might declare a matching route dispatched by slug underneath an umbrella parent route, i.e. in the form `/parent/:slug`:

```clj
(bread/route "/article/:slug"
             {:route/resolver {:resolver/params :slug
                               :post/type :post.type/article
                               :post/parent false}})
```

Bread will recognize the fact that we are no longer looking for a parent page whose slug is `"parent"`, but are instead looking for posts of a certain type, in this case `:post.type/article`, with no parent article (hence the `false`).

Importantly, **this will still merge in the default post query params**, resulting in a query like:

```clj
{:post/slug "slug-from-the-requested-route"
 :post/type :post.type/article
 :post/parent false
 :post/status :post.status/published
 :query/type :query.type/singular}
```

Note that in addition to the slug, post type, and (lack of) parentage, which we asked for specifically, Bread also assumed we wanted to query only for a single, published post. If you disagree with this opinion, it's easy enough to say so, and Bread will happily oblige:

```clj
(bread/route "/article/:slug"
             {:route/resolver {:resolver/defaults false ;; <-- this is new
                               :resolver/params :slug
                               :post/type :post.type/article
                               :post/parent false}})
```

#### Custom resolvers via functions

Bread also has an API for defining your own resolvers that operate on arbitrary request data. Just pass a function:

```clj
(bread/route "/article/:slug"
             {:route/resolver (fn [req]
                                {:resolver/defaults false
                                 ;; Get the slug from the request.
                                 :post/slug (:slug (:params req))
                                 :post/type :post.type/article
                                 :post/parent false})})
```

The resulting query is exactly the same as the previous map-based example. Note that here, you still have to tell Bread explicitly that you don't want it to merge in its opinionated defaults. Of course, if you do want the defaults (published posts only, singular post query), simply omit the `:resolver/defaults` param as you normally would.
