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
* Fully static rendering at write time (for simple use-cases)
* First-class support for translation/internationalization
* An extensible default schema supporting many "open-world" content patterns
* Co-located component queries for more advanced data models
* Completely hackable through an extremely simple plugin API

## Basic Usage

```clojure
(ns my-project
  (:require
    [reitit.core :as reitit]
    ;; include support for plugging in a Reitit Router
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.defaults :as defaults]))

;; Assume the database has the following global translation strings:
;; [{:string/key :hello :string/lang :en :string/value "Hello"}
;;  {:string/key :hello :string/lang :fr :string/value "Bonjour"}]

(defc hello [{:keys [i18n params]}]
  [:p (format "%s, %s!" (:greeting i18n) (:to params))])

(def router
  (reitit/router
    ["/:lang" ; :lang is the default i18n route param but can be configured
     ["/hello/:to" {:name :hello
                    :bread/dispatcher :component
                    :data [:params]
                    :component hello}]]))

(def handler
  (bread/load-handler (defaults/app {:router router})))

(handler {:uri "/en/hello/Breadsters"})
;; => [:p "Hello, Breadsters!"]

(handler {:uri "/fr/hello/Breadsters"})
;; => [:p "Bonjour, Breadsters!"]
```

### A simple blog engine

```clojure
(ns my.simple.blog
	(:require
    [reitit.core :as reitit]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.core :as bread]))

(defc main-layout [{:keys [main-content i18n lang]}]
  {:content-path [:main-content]}
  [:html {:lang lang}
   [:body
    [:h1 (:site-header i18n)]
    main-content]])

(defc article-component
  [{:keys [i18n lang]
   {:post/keys [slug fields]} :article}] ; :article corresponds to :key
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :article
   :extends main-layout}
  [:main.single
   [:article
    [:h2 (:title fields)]
    [:div.permalink [:a {:href (str "/" lang "/article/" slug)}]]
    (:content fields)]])

(defc article-listing-component
  [{:keys [i18n lang articles]}]
  {:query [:post/slug {:post/fields [:field/key :field/content]}]
   :key :articles
   :extends main-layout}
  [:main.listing
   (map (fn [{:post/keys [slug fields]}]
          [:article
           [:a {:href (str "/" lang "/article/" slug)}
            (:title fields)]])
        articles)])

(def router
  (reitit/router
    ["/:lang"
     ["/" {:bread/dispatcher :dispatcher.type/articles
           :bread/component article-listing-component}]
     ["/article/:slug" {:bread/dispatcher :dispatcher.type/article
                        :bread/component article-component}]]))

(def handler
  (bread/load-handler (defaults/app {:router router})))

(handler {:uri "/en/"})
;; => [:html {:lang "en"}
;;     [:body
;;      [:h1 "My Simple Blog"]
;;      [:main.listing
;;       [:article [:a {:href "/en/newer-article"} "English Article Title"]]
;;       [:article [:a {:href "/en/older-article"} "An Older Article"]]]]]

(handler {:uri "/en/article/newer-article"})
;; => [:html {:lang "en"}
;;     [:body
;;      [:h1 "My Simple Blog"]
;;      [:main.single
;;       [:article
;;        [:h2 "English Article Title"]
;;        [:div.content "Article content in English..."]]]]]
```
