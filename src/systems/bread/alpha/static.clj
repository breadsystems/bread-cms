;; TODO extract static to its own standalone plugin lib
(ns systems.bread.alpha.static
  (:require
    [clojure.instant :as instant]
    [clojure.string :as string]
    [clojure.java.io :as io]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.datastore :as d]
    [systems.bread.alpha.templates :as tpl])
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
    {:slug    slug
     ;; TODO cljs impl?
     :content (some-> (str (:src opts) java.io.File/separator slug (:extension opts))
                      (io/resource)
                      slurp)})
  (d/-update-post! [this slug post]
    (write-post! (:dest opts) slug post))
  ;; -add-post! -delete-post!
  )

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

(defn static-site-plugin [{:keys [src dest extension renderer]}]
  ;; TODO
  ;; * compute sitemap
  ;; * compile each route in sitemap
  ;; * eventually: model dependency tree and re-compile in real time
  (fn [req]
    (try
      (let [datastore-opts {:src       src
                            :dest      dest
                            :extension (or extension ".md")}]
        (-> req
            (d/set-datastore (FileSystemStore. datastore-opts)) ;; safe
            ;; TODO get this from the router?
            (bread/add-value-hook :slug (string/replace (:uri req) #"/" ""))
            (bread/add-hook :hook/dispatch persist-post)    ;; safe
            (bread/add-hook :hook/render (tpl/renderer->template renderer) {:precedence 0}) ;; safe
            ;; TODO move this out to a later step
            #_(bread/add-hook :hook/decorate (fn [{:keys [body] :as res}]
                                             (d/update-post! res (bread/hook res :slug) {:content body})
                                             res))))
      (catch java.lang.NullPointerException e
        (prn e)
        req))))


(comment
  (for [s ["two" "three" "four"]]
    (let [f (str "dev/pages/" s ".md")
          content (str "content for the post called `" s "`")]
      (spit f content)
      (slurp f)))

  (defn gather-pages [dir]
    (doall (file-seq (io/file dir))))

  (def extension ".md")
  (map (fn [file]
         (let [filename (.getName file)
               regex (re-pattern (str extension "$"))
               slug (string/replace filename regex "")]
             {:name    filename
              :slug    slug
              :route   (str "/" slug)
              :dir     (.getParent file)
              :path    (.getPath file)
              :lastmod (.lastModified file)}))
          (filter #(not (.isDirectory %)) (gather-pages "dev/pages")))

  (def *current-sitemap
    [{:loc "/one" :lastmod #inst "2020-09-25T20:59:36+00:00"}
     {:loc "/two" :lastmod #inst "2020-10-08T20:59:36+00:00"}])

  (defn route->sitemap-entry [sitemap]
    (into {} (map (fn [m] [(:loc m) m]) sitemap)))

  (route->sitemap-entry *current-sitemap)

  )

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