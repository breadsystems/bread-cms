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

(def app (-> (bread/app)
             (bread/post-type :blog-post
                              {:browse blog/find
                               :read   blog/details
                               :edit   blog/edit
                               :add    blog/add
                               :delete blog/delete})))

(handler {:uri "/posts"
          :query-string "month=2020-11"})
;; => [:html
;;     [:head
;;      [:meta {:charset "utf-8"}]
;;      [:title "Blog search"]]
;;     [:body
;;      [:header
;;       [:h1 "Posted in November 2020"]]
;;      [:main
;;       [:div.blog-cards
;;        [:article.blog-card
;;         [:h1 "Last post in November"]
;;         [:h3 "Nov 27"]
;;         [:div.excerpt "Lorem ipsum dolor sit amet."]]
;;        [:article.blog-card
;;         [:h1 "Another post in November]
;;         [:h3 "Nov 26"]
;;         [:div.excerpt "Impedit deleniti mollit tempor fuga ea anim quos."]]
;;        [:article.blog-card
;;         [:h1 "Yet another post from November"]
;;         [:h3 "Nov 18"]
;;         [:div.excerpt "Magna distinctio eu fugiat possimus mollitia."]]
;;        ...]
;;       [:section.pagination
;;        [:a {:href "?month=2020-10"} "Previous Month"]
;;        [:a {:href "?month=2020-11"} "Next Month"]]
;;      [:footer
;;       [:p "Main footer content"]]]]
```

