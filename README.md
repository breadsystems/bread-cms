# BreadCMS

<p align="center">
  <img alt="Build status" src="https://github.com/breadsystems/bread-cms/actions/workflows/test.yml/badge.svg" />
  <a href="https://clojars.org/systems.bread/bread-core"><img alt="Clojars Project" src="https://img.shields.io/clojars/v/systems.bread/bread-core.svg" /></a>
</p>

<p align="center">
  <i>After bread has been secured, leisure is the supreme aim.</i><br>
  — Pyotr Kropotkin
</p>

<p align="center">
  <strong>WORK IN PROGRESS. Code is approaching "stable alpha" status. A lot of this still does not work yet.</strong>
</p>

## Goals

Bread aims to be three things:

1. A lightweight core library for constructing a totally custom CMS
2. A small set of plugins that compose to create a CMS with sensible defaults
3. A binary (jar and native) that ships with the basic Bread CMS install

Bread's (planned) high-level feature set:

* Next-level user experience for editing content. *Design-aware editing. No separate backend UI.*
* First-class support for translation/internationalization
* First-class support for [Datahike](https://datahike.io/) and Datalog queries
* Collaborative editing
* Offline first
* Fully static rendering at write time (for simple use-cases)
* An extensible default schema supporting many "open-world" content patterns
* Co-located component queries for more advanced data models
* Completely hackable through an extremely simple plugin API

## Non-goals

* **Performance.** Bread is a relatively lightweight CMS, and should perform reasonably well on modern hardware. However, little attention has been given to benchmarking, optimization, etc. We are still in the design stage and thus are focusing on correctness and API ergonomics.
* **Minimalism by default.** A Content Management System must, by definition, have a fairly large surface area to be broadly useful. While some CMSs reach for a Spartan minimalism out of the box, Bread takes a balanced approach. Its core library is a handful of namespaces that define a very generic app lifecyle, small but very powerful. However, Bread's default install is considerably bigger, with utilities for various common use-cases; it is "batteries included." If you want real minimalism, you can get it by throwing away the defaults and composing your own bespoke CMS.
* **Integration with ___ framework.** Following from the above non-goal, the default install for Bread comes with many opinions about UX, UI, db schema, routing, etc. While these defaults are _simple_ to override, in aggregate they are not _easy_ to compose with another system's differing opinions. Take out the "batteries" and start with the core library if you want tight integration with your favorite Clojure framework.

## Basic usage

```clojure
(ns my-project
  (:require
    [reitit.core :as reitit]
    ;; include support for plugging in a Reitit Router
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.defaults :as defaults]))

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
  (bread/load-handler (defaults/app {:router router
                                     :db {:db/type :datahike
                                          :store {; datahike config
                                                  :backend :mem
                                                  :id "hello-bread"}}})))

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
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.plugin.reitit]
    [systems.bread.alpha.plugin.defaults :as defaults]))

(defc main-layout [{:keys [main-content i18n lang]}]
  {:content-path [:main-content]} ; how extending components pass their content
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
  [{:keys [i18n lang articles]}] ; :articles corresponds to :key
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
  (bread/load-handler (defaults/app {:router router
                                     :db {:db/type :datahike
                                          :store {; datahike config
                                                  :backend :mem
                                                  :id "bread-blog-db"}}})))

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
