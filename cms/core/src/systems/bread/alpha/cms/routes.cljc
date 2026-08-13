(ns systems.bread.alpha.cms.routes
  (:require
    [clojure.string :as string]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [reitit.ring]
    [ring.util.response :as response]
    ;; Bread core.
    [systems.bread.alpha.component :as component]
    [systems.bread.alpha.cms.theme.crust :as crust]
    [systems.bread.alpha.cms.theme.rise :as rise]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.post :as post]
    [systems.bread.alpha.thing :as thing]
    [systems.bread.alpha.taxon :as taxon]
    ;; Bread plugins.
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.email :as email]
    [systems.bread.alpha.plugin.markdown :as markdown]
    [systems.bread.alpha.plugin.marx :as marx]
    [systems.bread.alpha.plugin.reitit] ;; Implements Router protocol
    [systems.bread.alpha.plugin.signup :as signup]
    [systems.bread.alpha.plugin.account :as account]
    [systems.bread.alpha.plugin.invitations :as invitations])
  (:import
    [java.util Date]))

;; In a GraalVM native image, io/resource returns resource: URLs for files
;; embedded in the image. Ring's resource-data multimethod only implements
;; :file and :jar, so teach it the :resource protocol. This is a no-op on the
;; JVM, where the resource: protocol never occurs.
;; TODO delete once https://github.com/ring-clojure/ring/pull/447 gets merged.
(defmethod response/resource-data :resource
  [^java.net.URL url]
  ;; GraalVM resource scheme. Directory resources serve a listing of their
  ;; contents as the stream, so exclude them like the :file method does.
  (when-not (string/ends-with? (.getPath url) "/")
    (let [resource (.openConnection url)
          len (.getContentLength resource)
          last-mod (.getLastModified resource)]
      {:content (.getInputStream resource)
       :content-length (if (<= 0 len) len)
       :last-modified (if-not (zero? last-mod) (Date. last-mod))})))

;; Need to define this outside the router for now, so that it can use an
;; explicit :path to match URI => filepath correctly. The long-term fix is:
;; https://github.com/breadsystems/bread-cms/issues/184
(def marx-handler
  (reitit.ring/create-resource-handler
    {:root "marx"
     :path "/marx"}))

(def crust-handler
  (reitit.ring/create-resource-handler
    {:root "crust"
     :path "/crust"}))

(def rise-handler
  (reitit.ring/create-resource-handler
    {:root "rise"
     :path "/rise"}))

(defn default-routes
  ([] (default-routes {}))
  ([{:keys [root protected-prefix public-prefix]
     :or {root "/"
          protected-prefix "~"
          public-prefix "_"}}]
   [root
    ["" {:dispatcher/type ::i18n/lang=>}]
    [public-prefix
     ["/forgot"
      {:name :forgot-password
       :dispatcher/type ::auth/forgot-password=>
       :dispatcher/component #'rise/ForgotPasswordPage}]
     ["/reset"
      {:name :reset-password
       :dispatcher/type ::auth/reset-password=>
       :dispatcher/component #'rise/ResetPasswordPage}]
     ["/confirm-email"
      {:name :confirm-email
       :dispatcher/type ::email/confirm=>
       :dispatcher/component #'rise/ConfirmPage}]
     ["/patterns"
      ["/rise"
       {:name :patterns.rise
        :dispatcher/type ::component/standalone=>
        :dispatcher/component #'rise/PatternLibrary}]]
     ["/signup"
      {:name :signup
       :dispatcher/type ::signup/signup=>
       :dispatcher/component #'rise/SignupPage
       :dispatcher/not-found-component #'rise/SignupPage}]]
    [protected-prefix
     ["/login"
      {:name :login
       :dispatcher/type ::auth/login=>
       :dispatcher/component #'rise/LoginPage}]
     ["/account"
      {:name :account
       :dispatcher/type ::account/account=>
       :dispatcher/component #'rise/AccountPage}]
     ["/email"
      {:name :email
       :dispatcher/type ::email/settings=>
       :dispatcher/component #'rise/EmailPage}]
     ["/invitations"
      {:name :invitations
       :dispatcher/type ::invitations/invitations=>
       :dispatcher/component #'rise/InvitationsPage}]
     ["/edit"
      {:name :edit
       :dispatcher/type ::marx/edit=>}]
     ["/marx"
      ["/media"
       {:name :media
        :dispatcher/type ::marx/media.library=>
        :dispatcher/component #'marx/MediaLibrary}]]]
    ["assets/*"
     (reitit.ring/create-resource-handler
       {})]
    ;; TODO publish to assets?
    ["marx/*" marx-handler]
    ["crust/*" crust-handler]
    ["rise/*" rise-handler]
    ["{field/lang}"
     [""
      {:name :home
       :dispatcher/type ::post/page=>
       :dispatcher/component #'crust/HomePage}]
     ["/i/{db/id}"
      {:name :id
       :dispatcher/type ::thing/by-id=>
       :dispatcher/component #'crust/InteriorPage}]
     ["/tag/{thing/slug}"
      {:name :tag
       :dispatcher/type ::taxon/tag=>
       :dispatcher/component #'crust/Tag
       :post/type :page}]
     ["/m/{slug}"
      {:name :markdown-page
       :dispatcher/type ::markdown/page=>
       :dispatcher/component #'crust/MarkdownPage}]
     ["/*slugs"
      {:name :page
       :dispatcher/type ::post/page=>
       :dispatcher/component #'crust/InteriorPage}]]]))

(def router
  (reitit/router (default-routes) {:conflicts nil}))

(comment

  ;; Playing with resources/files...
  (io/resource "public/assets/hi.txt")
  (io/resource "marx/js/marx.js")
  ($resources {:uri "/marx/js/marx.js" :request-method :get :scheme :http})
  (def $resource-handler
    (reitit.ring/create-resource-handler
      {:root "marx"
       :path "/marx"}))
  ($resource-handler {:uri "/marx/js/marx.js" :request-method :get :scheme :http})
  (def $file-handler (reitit.ring/create-file-handler
                       {:root "resources/marx"
                        :path "/marx"}))
  ($file-handler {:uri "/marx/js/marx.js"})



  ;; COMPONENT ROUTING

  (require '[systems.bread.alpha.component :as c :refer [defc]]
           '[systems.bread.alpha.route :as route])

  ;; A "sluggable" thing, with ancestry
  (def grandchild
    {:thing/slug "c"
     :thing/_children [{:thing/slug "b"
                        :thing/_children [{:thing/slug "a"}]}]})

  (reitit/match-by-path router "/en/a")
  (reitit/match-by-path router "/en/a/b/c")
  (-> router
      (reitit/match-by-path "/en/tag/two")
      :data :name)
  (reitit/match->path
    (reitit/match-by-path router "/en/a/b/c")
    {:field/lang :en :slugs "a/b/c"})
  (reitit/match->path
    (reitit/match-by-name router :page {:field/lang :en :slugs "x"}))

  (bread/routes router)
  (bread/route-params router $req)

  ;; route/uri infers params and then just calls bread/path under the hood...
  (bread/path router :page {:field/lang :en :slugs "a/b/c"})
  (route/uri (->app $req) :page (merge {:field/lang :en} grandchild))
  (route/uri (->app $req) :page! (merge {:field/lang :en} grandchild))

  (route/ancestry grandchild)
  (bread/infer-param :slugs grandchild)
  (bread/routes (route/router (->app $req)))

  (route/uri (->app $req) :page (merge {:field/lang :en} grandchild))
  (route/uri (->app $req) :page {:field/lang :en})
  (route/uri (->app $req) :page nil)
  (route/uri (->app $req) :page {})

  ,)
