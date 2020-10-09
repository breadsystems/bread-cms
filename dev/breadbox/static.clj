(ns breadbox.static
  (:require
   [clojure.string :as string]
   [clojure.java.io :as io]
   [systems.bread.alpha.core :as bread]
   [systems.bread.alpha.datastore :as d])
  (:import
   [systems.bread.alpha.datastore BreadStore]))

(defn- write-post! [dir {:keys [slug content]}]
  (spit (str dir java.io.File/separator slug ".html") content))

(deftype FileSystemStore [opts]
  BreadStore
  (d/-type->posts [this type]
    ;; TODO
    [])
  (d/-slug->post [this slug]
    {:slug slug
     :content (some-> (str (:src opts) java.io.File/separator slug (:extension opts))
                      (io/resource)
                      slurp)})
  (d/-update-post! [this _ post]
    (write-post! (:dest opts) post))
  ;; -add-post! -delete-post!
  )

(comment
  (write-post! "dist"
               {:slug "hey"
                :content "Hey you, out there in the cold..."}))

(defn static-site-plugin [{:keys [src dest extension]}]
  ;; TODO
  ;; * compute sitemap
  ;; * compile each route in sitemap
  ;; * eventually: model dependency tree and re-compile in real time
  (fn [req]
    (let [datastore-opts {:src src :dest dest :extension (or extension ".md")}]
      (-> req
          (assoc :slug (string/replace (:url req) #"/" ""))
          (bread/add-effect (fn [{:keys [body slug] :as res}]
                              (d/update-post! res slug {:content body})))
          (bread/add-value-hook :hook/datastore (FileSystemStore. datastore-opts))))))
