(ns systems.bread.alpha.cms.theme.crust
  (:require
    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.plugin.account :as account]
    [systems.bread.alpha.plugin.auth :as auth]))

(def IntroSection
  {:id :intro
   :title "Introduction"
   :content
   [:<>
    [:p
     "This is the pattern library for the CRUST Bread theme."
     " This document serves two purposes:"]
    [:ol
     [:li "to illustrate the look and feel of the CRUST theme"]
     [:li "to illustrate usage of the CRUST components"]]
    [:p "CRUST is designed to be used for functional UIs in web apps, as opposed
        to long-form or image-heavy content. The font-family is
        therefore a uniform sans-serif across the board, to maximize scannability.
        CRUST uses a system font cascade:"]
    [:pre
     "--font-family: -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, Cantarell, Ubuntu, roboto, noto, helvetica, arial, sans-serif;
     "]]})

(def HowToSection
  {:id :how-to
   :title "How to use this document"
   :content
   [:<>
    [:p "Don't."]]})

(def TypographySection
  {:id :typography
   :title "Typography"
   :content
   [:<>
    [:h2 "Heading two"]
    [:h3 "Heading three"]
    [:h4 "Heading four"]
    [:h5 "Heading five"]
    [:h6 "Heading six"]
    [:p "Paragraph text with a "
     [:a {:href "#"} "link"]
     ". Here's what a "
     [:a {:href "#" :data-visited true} "visited link"]
     " will look like and here's what a "
     [:a {:href "#" :data-hover true} "hovered link"]
     " will look like. Here is some "
     [:strong "bold text"] " and some " [:i "italicized text."]]
    ,]})

(defc Page [{:keys [dir config content hook field/lang]}]
  {:doc "`Page` is the foundation of the CRUST theme. This is the component
        you should use to serve most user-facing web pages in your application.
        "
   :doc/default-data
   {:content [:div "Page content"]
    :config {:site/name "Site name"}
    :hook (fn hook [_ x & _] x)}
   :examples
   '[{:doc "Language and text direction"
      ;; TODO support markdown in docs
      :description
      "Specify document language and text direction with `:field/lang` and `:dir`,
      resp. Typically the `i18n` core plugin takes care of this for you, including
      detecting text direction based on language."
      :args ({:dir :rtl
              :field/lang :ar
              :content [:p "محتوى الصفحة"]})}
     {:doc "Document title"
      :description
      "By default, `(:site/name config)` is used for the document title. This
      is set up automatically if you pass `{:site {:name your-site-name}}` to
      the `defaults` plugin.
      "
      :args ({:config {:site/name "Title in config"}})}
     {:doc "Overriding document title"
      :description
      "Set `(:title content)` to have it prepended to the globally configured
      site name, separated with `\" | \"`. If you need further customization,
      use the `::theme/html.title` hook.
      "
      :args ({:config {:site/name "Title in config"}
              :content {:title "Title override!" :content [:div "Page content"]}})}
     ,]}
  (let [{:keys [content head title]}
        (if (vector? content) {:content content} content)]
    [:html {:lang lang :dir dir}
     [:head
      [:meta {:content-type :utf-8}]
      (hook ::theme/html.title
            [:title (theme/title title (:site/name config))]
            title)
      [:link {:rel :stylesheet :href "/crust/css/base.css"}]
      head
      ;; Support arbitrary markup in <head>
      (->> [:<>] (hook ::theme/html.head))]
     [:body
      content]]))

(defc LoginPage
  [{:as data
    :keys [config hook i18n session dir totp]
    :auth/keys [result]}]
  {:extends Page
   :doc
   "The standard Bread login page, designed to work with the `::auth/login=>`
   dispatcher. You typically won't need to call this component from within
   other components."}
  (let [{:keys [totp-key issuer]} totp
        user (or (:user session) (:auth/user session))
        step (:auth/step session)
        error? (false? (:valid result))]
    {:title "Login"
     :head [:<> [:style
                 "
                 .totp-key {
                   font-family: monospace;
                   letter-spacing: 5;
                 }
                 "]]
     :content
     (cond
       (:user/locked-at user)
       [:main
        [:form.flex.col
         (hook ::html.locked-heading [:h2 (:auth/account-locked i18n)])
         (hook ::html.locked-explanation [:p (:auth/too-many-attempts i18n)])]]

       (= :setup-two-factor step)
       (let [data-uri (auth/qr-datauri {:label issuer
                                        :user (:user/username user)
                                        :secret totp-key
                                        :image-type :PNG})]
         [:main
          [:form.flex.col {:name :setup-mfa :method :post}
           (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
           (hook ::html.scan-qr-instructions
                 [:p.instruct (:auth/please-scan-qr-code i18n)])
           [:div.center [:img {:src data-uri :width 125 :alt (:auth/qr-code i18n)}]]
           [:p.instruct (:auth/or-enter-key-manually i18n)]
           [:div.center [:h2.totp-key totp-key]]
           [:input {:type :hidden :name :totp-key :value totp-key}]
           [:hr]
           [:p.instruct (:auth/enter-totp-next i18n)]
           [:div.field
            [:input {:id :two-factor-code :type :number :name :two-factor-code}]
            [:button {:type :submit :name :submit :value "verify"}
             (:auth/verify i18n)]]
           (when error?
             (hook ::html.invalid-code
                   [:div.error
                    [:p (:auth/invalid-totp i18n)]]))]])

       (= :two-factor step)
       [:main
        [:form.flex.col {:name :bread-login :method :post}
         (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
         (hook ::html.enter-2fa-code
               [:p.instruct (:auth/enter-totp i18n)])
         [:div.field.two-factor
          [:input {:id :two-factor-code :type :number :name :two-factor-code}]
          [:button {:type :submit :name :submit :value "verify"}
           (:auth/verify i18n)]]
         (when error?
           (hook ::html.invalid-code
                 [:div.error
                  [:p (:auth/invalid-totp i18n)]]))]]

       :default
       [:main
        [:form.flex.col {:name :bread-login :method :post}
         (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
         (hook ::html.enter-username
               [:p.instruct (:auth/enter-username-password i18n)])
         [:div.field
          [:label {:for :user} (:auth/username i18n)]
          [:input {:id :user :type :text :name :username}]]
         [:div.field
          [:label {:for :password} (:auth/password i18n)]
          [:input {:id :password :type :password :name :password}]]
         (when error?
           (hook ::html.invalid-login
                 [:div.error [:p (:auth/invalid-username-password i18n)]]))
         [:div.field
          [:span.spacer]
          [:button {:type :submit} (:auth/login i18n)]]]])}))

(defc AccountPage
  [{:as data :keys [config hook dir user]}]
  {:extends Page
   :query '[:db/id
            :thing/created-at
            :user/username
            :user/name
            :user/lang
            :user/preferences
            {:user/roles [:role/key {:role/abilities [:ability/key]}]}
            {:invitation/_redeemer [{:invitation/invited-by [:db/id :user/username]}]}
            {:user/sessions [:db/id :session/data :thing/created-at :thing/updated-at]}]}
  {:title (:user/username user)
   :head [:<> [:style
               "
               .user-session {
                 display: flex;
                 flex-flow: row wrap;
                 justify-content: space-between;
                 align-items: start;

                 padding: 1em;
                 border: 2px dashed var(--color-stroke-tertiary);
               }
               "]]
   :content
   [:<>
    [:nav.row
     (map (partial Section data) (:account/html.account.header config))]
    [:main
     (map (partial Section data) (:account/html.account.sections config))]]})

(defc LogoutForm [{:keys [config i18n]}]
  {:doc "Standard logout form for the account page."}
  [:form.logout-form {:method :post :action (:auth/login-uri config)}
   [:button {:type :submit :name :submit :value "logout"}
    (:auth/logout i18n)]])

(defmethod Section ::account/logout-form [data _]
  (LogoutForm data))

(defn- i18n-format [i18n k]
  (if (sequential? k) ;; TODO tongue
    (let [[k & args] k]
      (apply format (get i18n k) args))
    (get i18n k)))

(defmethod Section :flash [{:keys [session ring/flash i18n]} _]
  [:<>
   (when-let [success-key (:success-key flash)]
     [:.success [:p (i18n-format i18n success-key)]])
   (when-let [error-key (:error-key flash)]
     [:.error [:p (i18n-format i18n error-key)]])])

(defmethod Section :save [{:keys [i18n]} _]
  [:.field
   [:span.spacer]
   [:button {:type :submit :name :action :value "update"}
    (:account/save i18n)]])

(def CustomizingSection
  {:id :customizing
   :title "Customizing CRUST"
   :content
   [:<>
    [:p
     "CRUST is designed to be extensible via CSS variables, AKA custom properties.
     By overriding these, you can get a lot of variation from the core theme.
     Of course, you can extend it further by serving your own custom CSS."]
    [:p
     "This technique is powerful, but if your needs are more complex, look into
     creating your own custom theme with its own pattern library."]]})

(defc PatternLibrary [_]
  {:extends Page}
  (let [patterns [IntroSection
                  HowToSection
                  TypographySection
                  Page
                  LoginPage
                  CustomizingSection]]
    {:title "CRUST"
     :head [:<>
            [:script {:src "/crust/js/patterns.js"}]
            [:link {:rel :stylesheet :href "/crust/css/patterns.css"}]]
     :content
     [:<>
      [:div#theme-toggle-container
       [:button#toggle-theme {:style {:position :relative}} "light/dark"]]
      [:main.gap-spacious
       (theme/TableOfContents {:patterns patterns})
       (map theme/Pattern patterns)]]}))
