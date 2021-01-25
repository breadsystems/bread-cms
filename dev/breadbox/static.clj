(ns breadbox.static
  (:require
   [clojure.instant :as instant]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [markdown.core :as md]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as d]
   [systems.bread.alpha.template :as tpl])
  (:import
   [systems.bread.alpha.datastore BreadStore]))

(defn- write-post! [dir slug {:keys [content]}]
  (spit (str dir java.io.File/separator slug ".html") content))

(deftype FileSystemStore [opts]
  BreadStore
  (d/-type->posts [this type]
    ;; TODO
    [])
  (d/-slug->post [this slug]
    {:slug slug
     ;; TODO cljs impl?
     :content (some-> (str (:src opts) java.io.File/separator slug (:extension opts))
                      (io/resource)
                      slurp)})
  (d/-update-post! [this slug post]
    (write-post! (:dest opts) slug post))
  ;; -add-post! -delete-post!
  )

(comment
  (write-post! "dist"
               "hey"
               {:slug "hey"
                :content "Hey you, out there in the cold..."}))

(defn persist-post
  "Query for and set :post in the request context"
  [req]
  (let [datastore (d/datastore req)
        post (d/slug->post datastore :default (bread/hook req :slug))]
    (-> req
        ;; Produce a raw response from the post content
        (bread/response {:body (:content post)})
        ;; Persist the post data directly
        (bread/add-value-hook :post post))))

(defn generate-sitemap* [handler opts])

(defn static-site-plugin [{:keys [src dest extension renderer]}]
  ;; TODO
  ;; * compute sitemap
  ;; * compile each route in sitemap
  ;; * eventually: model dependency tree and re-compile in real time
  (fn [req]
    (let [datastore-opts {:src src
                          :dest dest
                          :extension (or extension ".md")}
          template (tpl/renderer->template (or renderer md/md-to-html-string))]
      (-> req
          (d/set-datastore (FileSystemStore. datastore-opts))
          ;; TODO get this from the router?
          (bread/add-value-hook :slug (string/replace (:url req) #"/" ""))
          (bread/add-hook :hook/dispatch persist-post)
          (bread/add-hook :hook/render template {:precedence 0})
          #_(bread/add-hook :hook/decorate (fn [{:keys [body] :as res}]
                                           (d/update-post! res (bread/hook res :slug) {:content body})
                                           res))))))

(comment
  (instant/read-instant-date "2020-10-25T20:59:36+00:00")
  (inst-ms (java.util.Date.)))

(defn- sitemap-entry-handler [handler]
  ;; TODO rerender lazily based on lastmod
  (fn [{:keys [loc _lastmod]}]
    (handler {:url loc})))

(defn generator [handler]
  (let [entry->response (sitemap-entry-handler handler)]
    (fn [sitemap]
      (map entry->response sitemap))))

(defn generate! [handler _opts]
  (let [gen (generator handler)]
    (gen [{:loc "/one" :lastmod #inst "2020-09-25T20:59:36+00:00"}
          {:loc "/two" :lastmod #inst "2020-10-08T20:59:36+00:00"}])))