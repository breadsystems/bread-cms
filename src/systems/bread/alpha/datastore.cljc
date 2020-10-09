(ns systems.bread.alpha.datastore
  (:require
   [systems.bread.alpha.core :as bread]))


(defmulti connect! :datastore/type)

(defmethod connect! :default [{:datastore/keys [type] :as config}]
  (let [msg (if (nil? type)
              "No :datastore/type specified in datastore config!"
              (str "Unknown :datastore/type `" type "`!"
                   " Did you forget to load a plugin?"))]
    (throw (ex-info msg {:config config
                         :bread.context :datastore/connect!}))))


(defprotocol KeyValueDatastore
  (get-key [store k])
  (set-key [store k v])
  (delete-key [store k]))

(extend-protocol KeyValueDatastore
  clojure.lang.PersistentArrayMap
  (get-key [m k]
    (get m k))
  (set-key [m k v]
    (assoc m k v))
  (delete-key [m k]
    (dissoc m k))
  
  clojure.lang.Atom
  (get-key [a k]
    (get (deref a) k))
  (set-key [a k v]
    (swap! a assoc k v))
  (delete-key [a k]
    (swap! a dissoc k)))


(defprotocol TemporalDatastore
  (as-of [store timepoint])
  (history [store])
  (pull [store struct lookup-ref])
  (q [store query])
  (db-with [store timepoint]))

(defprotocol TransactionalDatastoreConnection
  (db [conn])
  (transact [conn timepoint]))

(defprotocol BreadStore
  (-type->posts [store t] "get all posts of type t")
  (-slug->post [store slug] "get a post by its slug")
  (-update-post! [store ident post] "update a post")
  (-add-post! [store post] "add/create a post in the datastore")
  (-delete-post! [store ident] "delete a post from the datastore")
  (-field [store ident k] "get an arbitrary field from a post identified by ident"))

(comment

  ;; OK, let's think about queries.
  '[:find ?p ?title ?type ?slug ?content ?tags
    :in $ $slug
    :where
    [?p :post/slug $slug]
    [?p :post/title ?title]
    [?p :post/type ?type]
    [?p :post/content ?content]
    [?p :post/tags ?tag]]

  (pull {:datastore/type :datastore.type/datahike
         :datahike {}}
        [:db/id :post/title :post/type :post/slug :post/content
         {:post/tags [:db/id :tag/title :tag/slug]}]
        [:post/slug "some-page"])

  (defmacro defposttype [t body]
    `(do
       (defmethod slug->post ~t [req _t slug]
         ~body)))

  (defposttype :post.type/lesson
    (slug->post
     [req _t slug]
      (let [store (lesson/store req)]
        (try
          (-slug->post store slug)
          (catch lessons.exception.ApiError e
            (log/error e {:context "slug->post"
                          :post/type :post.type/lesson
                          :post/slug slug})
            nil)))))

  (defmethod slug->post [_t ])

  ;;  
  )

(defn req->store [req]
  (bread/hook req :hook/datastore))

(defmulti type->posts (fn [t _req]
                       t))

(defmethod type->posts :default [req t]
  (when-let [store (req->store req)]
    (-type->posts store t)))

(defmulti slug->post (fn [_app t _slug]
                       t))

(defmethod slug->post :default [req _ slug]
  (when-let [store (req->store req)]
    (-slug->post store slug)))

(defmulti update-post! (fn [_req _ident post]
                         (:post/type post)))

(defmethod update-post! :default [req ident post]
  (when-let [store (req->store req)]
    (-update-post! store ident post)))

(defmulti add-post! (fn [_req post]
                         (:post/type post)))

(defmethod add-post! :default [req post]
  (when-let [store (req->store req)]
    (-add-post! store post)))

(defmulti delete-post! (fn [_req post]
                         (:post/type post)))

(defmethod delete-post! :default [req ident]
  (when-let [store (req->store req)]
    (-delete-post! store ident)))


(defprotocol ValueStore
  (-vals [store] "Get all values within the key/value store"))

(extend-protocol ValueStore
  clojure.lang.PersistentArrayMap
  (-vals [m] (vals m))
  
  clojure.lang.Atom
  (-vals [a] (vals (deref a))))

(deftype KeyValueBreadStore [store]
  BreadStore
  (-type->posts [this t]
    (filter #(= t (:post/type %)) (-vals store)))
  (-slug->post [this slug]
    (get-key store slug))
  (-update-post! [this slug post]
    (if (= slug (:post/slug post))
      (set-key store slug post)
      (-> store
          (set-key (:post/slug post) post)
          (delete-key slug))))
  (-add-post! [this post]
    (set-key store (:post/slug post) post))
  (-delete-post! [this slug]
    (delete-key store slug)))

(defn key-value-store [store]
  (KeyValueBreadStore. store))

(defn store->plugin [store]
  (fn [app]
    (bread/add-value-hook app :hook/datastore store)))

(defn datastore [app]
  (bread/hook app :hook/datastore))

(defn set-datastore [app store]
  (bread/add-value-hook app :hook/datastore store))

(comment
  (let [store (key-value-store {"my-post" {:type :post :slug "my-post"}
                                "my-page" {:type :page :slug "my-page"}
                                "home" {:type :page :slug "home"}})]
    {'type->post (-type->posts store :page)
     'slug->post (-slug->post store "home")
     'update-post! (-update-post! store "home" {:type :page :slug "home" :content "new content"})
     'add-post! (-add-post! store {:type :page :slug "new-post"})
     'delete-post! (-delete-post! store "home")}))
