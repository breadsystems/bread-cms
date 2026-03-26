(ns systems.bread.alpha.cms.theme.rise
  (:require
    [clojure.string :as string]

    [systems.bread.alpha.cms.theme :as theme]
    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.account :as account]
    [systems.bread.alpha.plugin.email :as email]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.invitations :as invitations])
  (:import
    [java.text SimpleDateFormat]))

(defn- IntroSection [_]
  {:id :intro
   :title "Introduction"
   :content
   [:<>
    [:p
     "This is the pattern library for the RISE Bread theme."
     " This document serves two purposes:"]
    [:ol
     [:li "to illustrate the look and feel of the RISE theme"]
     [:li "to illustrate usage of the RISE components"]]
    ,]})

(defn- HowToSection  [_]
  {:id :how-to
   :title "How to use this document"
   :content
   [:<>
    [:p "Don't."]]})

(defn- TypographySection  [_]
  {:id :typography
   :title "Typography"
   :content
   [:<>
    [:p "RISE is designed to be used for functional UIs in web apps, as opposed
        to long-form or image-heavy content. The font-family is
        therefore a uniform sans-serif across the board, to maximize scannability.
        RISE uses a system font cascade:"]
    [:pre
     "--font-family: -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, Cantarell, Ubuntu, roboto, noto, helvetica, arial, sans-serif;
     "]
    [:p "This ensures that fonts load instantly, and helps keep things simple."]
    [:h1 "Heading one"]
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
  {:doc "`Page` is the foundation of the RISE theme. This is the component
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
      [:meta {:name :viewport :content "width=device-width, initial-scale=1"}]
      (hook ::theme/html.title
            [:title (theme/title title (:site/name config))]
            title)
      [:link {:rel :stylesheet :href "/rise/css/base.css"}]
      head
      ;; Support arbitrary markup in <head>
      (->> [:<>] (hook ::theme/html.head))]
     [:body
      content]]))

(defc SuccessMessage [{:keys [message]}]
  {:doc "A success message"
   :description
   "Used to indicate success of some action, typically a side-effect, such
   as an account update."
   :doc/preview? true
   :examples
   '[{:args ({:message "Update successful!"})}]}
  [:.success [:p message]])

(defc ErrorMessage [{:keys [message]}]
  {:doc "An error message"
   :description
   "Used to indicate an error completing some action, typically a side-effect, such
   as an account update."
   :doc/preview? true
   :examples
   '[{:args ({:message "Something bad happened!"})}]}
  [:.error [:p message]])

(defc Field [field-name & {field-type :type
                           :keys [id label value input-attrs label-attrs]}]
  (let [id (or id field-name)]
    [:.field
     [:label (merge label-attrs {:for id}) label]
     [:input (merge input-attrs {:name field-name
                                 :id id
                                 :type (or field-type :text)
                                 :value value})]]))

(defc Submit [label & {field-name :name :keys [value]}]
  [:.field
   [:span.spacer]
   [:button {:type :submit :name field-name :value value} label]])

(defc LoginPage
  [{:as data
    :keys [config hook i18n session dir totp ring/anti-forgery-token-field]
    :auth/keys [result]}]
  {:extends Page
   :doc
   "The standard Bread login page, designed to work with the `::auth/login=>`
   dispatcher. You typically won't need to call this component from your code,
   except to reference it from your route if implementing custom routing."}
  (let [{:keys [totp-key issuer]} totp
        user (or (:user session) (:auth/user session))
        step (:auth/step session)
        error? (false? (:valid result))]
    {:title (:auth/login i18n)
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
         (anti-forgery-token-field)
         (hook ::html.locked-heading [:h2 (:auth/account-locked i18n)])
         (hook ::html.locked-explanation [:p (:auth/too-many-attempts i18n)])]]

       (= :setup-two-factor step)
       (let [data-uri (auth/qr-datauri {:label issuer
                                        :user (:user/username user)
                                        :secret totp-key
                                        :image-type :PNG})]
         [:main
          [:form.flex.col {:name :setup-mfa :method :post}
           (anti-forgery-token-field)
           (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
           (hook ::html.scan-qr-instructions
                 [:p.instruct (:auth/please-scan-qr-code i18n)])
           [:div.center [:img {:src data-uri :width 125 :alt (:auth/qr-code i18n)}]]
           [:p.instruct (:auth/or-enter-key-manually i18n)]
           [:div.center [:h2.totp-key totp-key]]
           [:input {:type :hidden :name :totp-key :value totp-key}]
           [:hr]
           [:p.instruct (:auth/enter-totp-next i18n)]
           [:.field
            [:input {:id :two-factor-code :type :number :name :two-factor-code}]
            [:button {:type :submit :name :submit :value "verify"}
             (:auth/verify i18n)]]
           (when error?
             (hook ::html.invalid-code
                   (ErrorMessage {:message (:auth/invalid-totp i18n)})))]])

       (= :two-factor step)
       [:main
        [:form.flex.col {:name :bread-login :method :post}
         (anti-forgery-token-field)
         (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
         (hook ::html.enter-2fa-code
               [:p.instruct (:auth/enter-totp i18n)])
         [:.field.two-factor
          [:input {:id :two-factor-code :type :number :name :two-factor-code}]
          [:button {:type :submit :name :submit :value "verify"}
           (:auth/verify i18n)]]
         (when error?
           (hook ::html.invalid-code
                 (ErrorMessage {:message (:auth/invalid-totp i18n)})))]]

       :default
       [:main
        [:form.flex.col {:name :bread-login :method :post}
         (anti-forgery-token-field)
         (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
         (hook ::html.enter-username
               [:p.instruct (:auth/enter-username-password i18n)])
         (Field :username :label (:auth/username i18n))
         (Field :password :type :password :label (:auth/password i18n))
         (when error?
           (hook ::html.invalid-login
                 (ErrorMessage {:message (:auth/invalid-username-password i18n)})))
         (Submit (:auth/login i18n))]])}))

(defc ForgotPasswordPage
  [{:keys [config hook i18n ring/anti-forgery-token-field ring/request-method]}]
  {:extends Page
   :doc
   "The standard Bread forgot password page, designed to work with the
   `::auth/forgot-password=>` dispatcher.
   dispatcher. You typically won't need to call this component from your code,
   except to reference it from your route if implementing custom routing."
   :doc/preview? true
   :doc/default-data
   {:config {:site/name "Site name"}
    :hook (fn hook [_ x & _] x)
    :ring/anti-forgery-token-field (constantly nil)}
   :examples
   '[{:doc "Forgot password"
      :description "Initial form"
      :args ({:ring/request-method :get})}
     {:doc "Submitted"
      :description "Submitted form"
      :args ({:ring/request-method :post})}
     ,]}
  (let [post? (= :post request-method)]
    {:title (:auth/forgot-password i18n)
     :content
     (if post?
       [:main
        (hook ::html.forgot-heading [:h1 (:auth/forgot-password i18n)])
        (hook ::html.forgot-acknowledgement
              [:p.instruct (:auth/reset-email-sent i18n)])]
       [:main
        [:form.flex.col {:name :bread-login :method :post}
         (anti-forgery-token-field)
         (hook ::html.forgot-heading [:h1 (:auth/forgot-password i18n)])
         (hook ::html.enter-confirm-new-password
               [:p.instruct (:auth/enter-username i18n)])
         (Field :username
                :label (:auth/username i18n)
                :input-attrs {:maxlength (:auth/max-password-length config)})
         (Submit (:auth/reset-password i18n))]])}))

(defc ResetPasswordPage
  [{:as data
    :keys [config hook i18n session dir ring/anti-forgery-token-field]
    :auth/keys [result]}]
  {:extends Page
   :doc
   "The standard Bread password reset page, designed to work with the `::auth/reset=>`
   dispatcher. You typically won't need to call this component from your code,
   except to reference it from your route if implementing custom routing."
   :doc/default-data
   {:config {:site/name "Site name"}
    :hook (fn hook [_ x & _] x)
    :ring/anti-forgery-token-field (constantly nil)}
   :examples
   '[{:doc "Password reset"
      :preview? true
      :description
      "A valid code must be present in the query string."
      :args ({})}]}
  (let [user (or (:user session) (:auth/user session))
        error? (false? (:valid result))]
    {:title (:auth/reset-password i18n)
     :content
     (cond
       (:user/locked-at user)
       [:main
        [:form.flex.col
         (anti-forgery-token-field)
         (hook ::html.locked-heading [:h2 (:auth/account-locked i18n)])
         (hook ::html.locked-explanation [:p (:auth/too-many-attempts i18n)])]]

       ;; Forgot password

       ;; MFA

       :default
       [:main
        [:form.flex.col {:name :bread-login :method :post}
         (anti-forgery-token-field)
         (hook ::html.reset-heading [:h1 (:auth/reset-password i18n)])
         (hook ::html.enter-confirm-new-password
               [:p.instruct (:auth/enter-confirm-new-password i18n)])
         (when error?
           (hook ::html.invalid-password
                 (ErrorMessage {:message (:auth/invalid-password i18n)})))
         (Field :password
                :type :password
                :label (:auth/password i18n)
                :input-attrs {:maxlength (:auth/max-password-length config)})
         (Field :password-confirmation
                :type :password
                :label (:auth/password-confirmation i18n)
                :input-attrs {:maxlength (:auth/max-password-length config)})
         (Submit (:auth/reset i18n))]])}))

(defc AccountNav [{:as data :keys [config]}]
  {:doc
   "Top-level navigation for all user settings pages. Calls `component/Section`
   on each member of `(:account/html.account.header config)`. See also:
   `SettingsPage`.
   using the `account` plugin and extend the `component/Section` method
   to customize."
   :examples
   '[{:doc "Customizing the account nav"
      :description
      "You typically won't need to to call this component directly from your theme
      code. To customize the account nav, configure the `:html-account-header`
      option to the `account` plugin and implement the `component/Section` method
      for each custom value. See also:
      [Adding a custom user settings page](#Adding_a_custom_user_settings_page)."
      :args [{:config {:account/html.account.header [[:span "First section"]
                                                     [:span "Second section"]
                                                     "..."]}}]}]}
  (apply conj [:nav.row]
         (map (partial Section data) (:account/html.account.header config))))

(defc SettingsPage [{:as data :keys [content]}]
  {:extends Page
   :doc
   "Reusable account settings page that includes `AccountNav` automatically.
   To add custom user settings pages, extend this component. For adding links
   to any custom pages within the account nav itself, see
   [Customizing the account nav](#Customizing_the_account_nav)."
   :examples
   '[{:doc "Adding a custom user settings page"
      :description
      "`SettingsPage` extends `Page`, so all the same options for `content`,
      `title`, etc. apply."
      :args ({:content [:div "My custom settings page content"]
              :config {:account/html.account.header
                       [[:a {:href "/my-custom-route"}]]}})}
     ,]}
  (let [{:as content settings-content :content}
        (if (vector? content) {:content content} content)]
    (assoc content :content
           [:<>
            (AccountNav data)
            settings-content])))

(defc AccountPage
  [{:as data :keys [config hook user]}]
  {:extends SettingsPage
   :doc
   "The main account settings page, the default redirect target after logging in.
   Contains settings for name, pronouns, timezone, etc. You typically won't have
   to call this component except to reference it from your route if implementing
   custom routing."
   :query '[:db/id
            :thing/created-at
            :user/username
            :user/name
            :user/lang
            :user/preferences
            {:user/roles [:role/key {:role/abilities [:ability/key]}]}
            {:invitation/_redeemer [{:invitation/invited-by [:db/id :user/username]}]}
            {:user/sessions [:db/id :session/data :thing/created-at :thing/updated-at]}]
   :doc/default-data {:user {:user/username "username"}
                      :config {:account/html.account.sections
                               [::account/account-form
                                [:section "Login sessions section..."]]}
                      :hook (fn hook [_ x & _] x)}
   :examples
   '[{:doc "Customizing account page sections"
      :description
      "`AccountPage` renders each item in `(:account/html.account.sections config)`
      as a section on this page. To customize this, pass `:html-account-section`
      to the `account` plugin and implement the `component/Section` method for
      each custom value."
      :args ({:config {:account/html.account.sections
                       [[:section "First section"]
                        [:section "Second section"]]}})}
     {:doc "Customizing the account settings form"
      :description
      "To customize the fields that appear in the main settings form, override
      the `:html-account-form` option to the `account` plugin and implement the
      `component/Section` method for each custom value. In this example, we
      include only the default `::account/name` and `::account/pronouns` options
      and a custom option called `:my-custom-field`. NOTE: The `::account/account=>`
      dispatcher treats any keys that are not part of Bread's default user
      schema as user preferences, automatically handling serialization and
      deserialization."
      :args ({:config {:account/html.account.form
                       [::account/name
                        ::account/pronouns
                        [:field
                         [:label "My custom field"]
                         [:input {:name :my-custom-field
                                  :value "value in user preferences"}]]]}})}]
   :doc/post-render (fn [content]
                      (assoc content :head [:style "...page-specific styles..."]))}
  {:title (:user/username user)
   :head (->> [:<> [:style
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
              (hook ::account/html.head))
   :content
   (apply conj [:main]
          (map (partial Section data) (:account/html.account.sections config)))})

(defc Option [labels selected-value value]
  [:option {:value value :selected (= selected-value value)}
   (get labels value)])

(defmethod Section ::account/username [{:keys [user]} _]
  [:span.username (:user/username user)])

(defmethod Section ::account/account-link
  [{:keys [user i18n] {:account/keys [account-uri]} :config} _]
  [:a {:href account-uri :title (:account/account-details i18n)}
   (:user/username user)])

(defmethod Section ::account/heading [{:keys [i18n]} _]
  [:h3 (:account/account i18n)])

(defmethod Section ::account/name [{:keys [user i18n]} _]
  [:.field
   [:label {:for :name} (:account/name i18n)]
   [:input {:id :name :name :name :value (:user/name user)}]])

(defmethod Section ::account/pronouns [{:keys [user i18n]} _]
  [:.field
   [:label {:for :pronouns} (:account/pronouns i18n)]
   [:input {:id :pronouns
            :name :pronouns
            :value (:pronouns (:user/preferences user))
            :placeholder (:account/pronouns-example i18n)}]])

(defmethod Section ::account/lang [{:keys [i18n lang-names supported-langs user]} _]
  (when (> (count supported-langs) 1)
    [:.field
     [:label {:for :lang} (:account/preferred-language i18n)]
     [:select {:id :lang :name :lang}
      (map (fn [k]
             [:option {:selected (= k (:user/lang user)) :value k}
              (get lang-names k (name k))])
           (sort-by name (seq supported-langs)))]]))

(defmethod Section ::account/timezone [{:keys [config i18n user]} _]
  (let [options (:account/timezone-options config)
        ;; TODO proper localization...
        labels (map #(string/replace % "_" " ") options)
        tz (:timezone (:user/preferences user))]
    [:.field
     [:label {:for :timezone} (:account/timezone i18n)]
     [:select {:id :timezone :name :timezone}
      (map (partial Option (zipmap options labels) tz) options)]]))

(defn- ua->browser [ua]
  (when ua
    (let [normalized (string/lower-case ua)]
    (cond
      (re-find #"firefox" normalized) "Firefox"
      (re-find #"chrome" normalized) "Google Chrome"
      (re-find #"safari" normalized) "Safari"
      :default "Unknown browser"))))

(defn- ua->os [ua]
  (when ua
    (let [normalized (string/lower-case ua)]
      (cond
        (re-find #"linux" normalized) "Linux"
        (re-find #"macintosh" normalized) "Mac"
        (re-find #"windows" normalized) "Windows"
        :default "Unknown OS"))))

(defmethod Section ::account/sessions [{:keys [i18n session user]} _]
  (let [date-fmt (SimpleDateFormat. (:account/date-format-default i18n))]
    [:section
     [:h3 (:account/your-sessions i18n)]
     [:.flex.col
      (map (fn [{:as user-session
                 {:keys [user-agent remote-addr]} :session/data
                 :thing/keys [created-at updated-at]}]
             (if (= (:db/id session) (:db/id user-session))
               ;; Current session.
               [:div.user-session
                [:div
                 (when user-agent
                   [:div (ua->browser user-agent) " | " (ua->os user-agent)])
                 (when remote-addr
                   [:div remote-addr])
                 [:div (i18n/t i18n [:account/logged-in-at (.format date-fmt created-at)])]
                 (when updated-at
                   [:div (i18n/t i18n [:account/last-active-at (.format date-fmt updated-at)])])]
                [:div [:span.instruct (:account/this-session i18n)]]]
               ;; Sessions on other devices.
               [:form.user-session {:method :post}
                [:input {:type :hidden :name :dbid :value (:db/id user-session)}]
                [:div
                 (when user-agent
                   [:div (ua->browser user-agent) " | " (ua->os user-agent)])
                 (when remote-addr
                   [:div remote-addr])
                 [:div "Logged in at " (.format date-fmt created-at)]
                 (when updated-at
                   [:div "Last active at " (.format date-fmt updated-at)])]
                [:div
                 [:button {:type :submit :name :action :value "delete-session"}
                  (:auth/logout i18n)]]]))
           (:user/sessions user))]]))

(defmethod Section ::email/settings-link
  [{:keys [i18n] {:email/keys [settings-uri]} :config} _]
  [:a {:href settings-uri :title (:email/email-settings i18n)}
   (:email/email i18n)])

(defmethod Section ::email/heading [{:keys [i18n]} _]
  [:h2 (:email/email-heading i18n)])

(defn- compare-emails [a b]
  (cond
    ;; Always list primary first...
    (:email/primary? a) -1
    (:email/primary? b) 1
    ;; ...then confirmed...
    (and (:email/confirmed-at a) (:email/confirmed-at b))
    (compare (:email/confirmed-at a) (:email/confirmed-at b))
    ;; ...and finally unconfirmed.
    :else (compare (:email/created-at a) (:email/created-at b))))

(defmethod Section ::email/emails
  [{:keys [config i18n user ring/anti-forgery-token-field]} _]
  (let [{:email/keys [allow-delete-primary?]} config
        emails (sort compare-emails (:user/emails user))]
    [:<>
     (if (seq emails)
       [:.flex.col {:role :list}
        (map (fn [{:keys [email/address
                          email/confirmed-at
                          email/primary?
                          db/id]}]
               [:form.flex.row {:method :post :role :listitem}
                (anti-forgery-token-field)
                [:input {:type :hidden :name :email :value address}]
                [:input {:type :hidden :name :id :value id}]
                (cond
                  primary?
                  [:<>
                   [:.flex.col.tight
                    [:label address]
                    [:small (:email/primary i18n)]
                    [:small
                     (:email/confirmed i18n)
                     ;; TODO date locale/formatting
                     " " confirmed-at]]
                   [:span.spacer]
                   (when allow-delete-primary?
                     [:button {:type :submit :name :action :value :delete}
                      (:email/delete i18n)])]

                  confirmed-at
                  [:<>
                   [:.flex.col.tight
                    [:label address]
                    [:small (:email/confirmed i18n)
                     ;; TODO date locale/formatting
                     " " confirmed-at]]
                   [:span.spacer]
                   [:button {:type :submit :name :action :value :make-primary}
                    (:email/make-primary i18n)]
                   [:button {:type :submit :name :action :value :delete}
                    (:email/delete i18n)]]

                  :pending
                  [:<>
                   [:.flex.col.tight
                    [:label address]
                    [:small (:email/confirmation-pending i18n)]]
                   [:span.spacer]
                   [:button {:type :submit :name :action :value :resend-confirmation}
                    (:email/resend-confirmation i18n)]
                   [:button {:type :submit :name :action :value :delete}
                    (:email/delete i18n)]])])
             emails)]
       [:p.instruct (:email/no-emails i18n)])]))

(defmethod Section ::email/add-email
  [{:keys [config i18n user ring/anti-forgery-token-field]} _]
  (let [emails (:user/emails user)
        any-pending? (seq (filter (complement :email/confirmed-at) emails))
        allow-multiple-pending? (:email/allow-multiple-pending? config)]
    (if (or (not any-pending?) allow-multiple-pending?)
      [:<>
       [:h3 {:for :add-email}
        (:email/add-email i18n)]
       [:form.flex.row {:method :post}
        (anti-forgery-token-field)
        [:input {:id :add-email :type :email :name :email :placeholder "me@example.email"}]
        [:button {:type :submit :name :action :value :add}
         (:email/add i18n)]]]
      [:p.instruct (:email/to-add-email-confirm-pending i18n)])))

(defc EmailPage
  [{:as data :keys [config i18n]}]
  {:extends SettingsPage
   :key :user
   :query '[:db/id :user/username {:user/emails [* :thing/created-at]}]}
  {:title (:email/email i18n)
   :content
   [:main
    (map (partial Section data) (:email/html.email.sections config))]})

(defmethod Section ::invitations/invitations-link
  [{:keys [config i18n]} _]
  [:a {:href (:invitations/invitations-uri config)}
   (:invitations/invitations i18n "Invitations")])

(defmethod Section ::invitations/invitations-heading [{:keys [i18n]} _]
  [:h2 (:invitations/invitations i18n "Invitations")])

(defmethod Section ::invitations/invite-form
  [{:keys [config i18n ring/params ring/anti-forgery-token-field user]} _]
  [:form.flex.col {:method :post :action (:invitations/invitations-uri config)}
   (anti-forgery-token-field)
   (let [max-total (:invitations/max-total config)
         left (when max-total (- max-total (count (:invitation/_invited-by user))))
         any-left? (or (not max-total) (not (zero? left)))]
     [:<>
      (when any-left?
        [:h3 (:invitations/invite i18n)])
      (when max-total
        [:.instruct (if any-left?
                      (i18n/t i18n [:invitations/total-left left])
                      (:invitations/total-reached i18n))])
      (when (or (not max-total) (not (zero? left)))
        [:.field
         [:label {:for :email} (:email/email i18n)]
         [:input {:id :email :name :email :type :email :value (:email params)}]
         (Submit (:invitations/invite i18n "Invite") :name :action :value "send")])])])

(defn- compare-invitations [a b]
  (let [redeemer-a (:invitation/redeemer a)
        redeemer-b (:invitation/redeemer b)]
    (cond
      ;; Always list pending first (reverse chronological)...
      (and redeemer-a (not redeemer-b)) 1
      (and redeemer-b (not redeemer-a)) -1
      (and (not redeemer-a) (not redeemer-b))
      (compare (:thing/updated-at b) (:thing/updated-at a))
      ;; ...and then redeemed.
      :else (compare (:email/created-at a) (:email/created-at b)))))

(defmethod Section ::invitations/invitations-list
  [{:keys [config i18n user ring/anti-forgery-token-field]} _]
  (let [invitations (sort compare-invitations (:invitation/_invited-by user))]
    [:.flex.col
     [:h3 (:invitations/your-invitations i18n)]
     (if (seq invitations)
       (map (fn SentInvitation [{{:email/keys [address]} :invitation/email
                                 :keys [db/id
                                        invitation/code
                                        invitation/redeemer
                                        thing/updated-at]}]
              (if redeemer
                [:.flex.row
                 [:label address]
                 [:small (i18n/t i18n [:invitations/accepted-at updated-at])]]
                [:form {:method :post :action (:invitations/invitations-uri config)}
                 (anti-forgery-token-field)
                 [:.field.flex.row {:data-code code}
                  [:input {:type :hidden :name :id :value id}]
                  [:.flex.col.tight
                   [:label address]
                   [:small (i18n/t i18n [:invitations/sent-at updated-at])]]
                  [:span.spacer]
                  [:button {:type :submit :name :action :value :resend}
                   (:invitations/resend i18n)]
                  [:button {:type :submit :name :action :value :revoke}
                   (:invitations/revoke i18n)]]]))
            invitations)
       [:p.instruct (:invitations/no-invitations-body i18n)])]))

(defc InvitationsPage
  [{:as data :keys [i18n ring/anti-forgery-token-field]}]
  {:extends SettingsPage
   :key :user
   :query '[:db/id
            :user/username
            {:invitation/_invited-by [* :thing/created-at
                                      {:invitation/email [*]}]}]}
  {:title (:invitations/invitations i18n)
   :content
   [:main.flex.col
    ;; TODO
    (map (partial Section data) [::invitations/invitations-heading
                                 :flash
                                 ::invitations/invite-form
                                 ::invitations/invitations-list])]})

(defc LogoutForm [{:keys [config i18n ring/anti-forgery-token-field]}]
  {:doc "Standard logout form for the account page."}
  [:form.logout-form {:method :post :action (:auth/login-uri config)}
   (anti-forgery-token-field)
   [:button {:type :submit :name :submit :value "logout"}
    (:auth/logout i18n)]])

(defmethod Section ::account/password [{:keys [i18n hook config]} _]
  [:<>
   [:p.instruct (:account/leave-passwords-blank i18n)]
   [:.field
    [:label {:for :password} (:auth/password i18n)]
    [:input {:id :password
             :type :password
             :name :password
             :maxlength (:auth/max-password-length config)}]]
   [:.field
    [:label {:for :password-confirmation} (:auth/password-confirmation i18n)]
    [:input {:id :password-confirmation
             :type :password
             :name :password-confirmation
             :maxlength (:auth/max-password-length config)}]]
   (hook ::html.password-guidelines
             [:p.instruct
              (i18n/t i18n [:auth/password-must-be-between
                            (:auth/min-password-length config)
                            (:auth/max-password-length config)])])])

(defmethod Section ::account/account-form
  [{:as data :keys [config ring/anti-forgery-token-field]} _]
  (apply conj [:form.flex.col {:method :post}]
         (when anti-forgery-token-field
           (anti-forgery-token-field))
         (map (partial Section data) (:account/html.account.form config))))

(defmethod Section ::account/logout-form [data _]
  (LogoutForm data))

(defc ConfirmPage
  [{:keys [pending-email i18n ring/uri]}]
  {:extends Page
   :query '[:db/id :email/address]
   :key :pending-email}
  (let [{:email/keys [address code]} pending-email]
    {:title (:email/confirm-email i18n)
     :content
     [:main.gap-large
      [:h2 (:email/please-confirm i18n)]
      [:p address]
      [:form {:method :post :action uri}
       [:input {:type :hidden :name :email :value address}]
       [:input {:type :hidden :name :code :value code}]
       [:button {:type :submit}
        (:email/confirm-email i18n)]]]}))

(defc SignupPage
  [{:as data
    :keys [config hook i18n invitation ring/params ring/anti-forgery-token-field]
    [_valid? error-key] :validation}]
  {:extends Page
   :key :invitation}
  {:title (:signup/signup i18n)
   :content
   (cond
     (and (:signup/invite-only? config) (not (:code params)))
     [:main
      [:form.flex.col
       (anti-forgery-token-field)
       (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
       [:p (:signup/site-invite-only i18n)]]]

     (and (:signup/invite-only? config) (not invitation))
     [:main
      [:form.flex.col
       (anti-forgery-token-field)
       (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
       [:p (:signup/invitation-invalid i18n)]]]

     :default
     [:main
      [:form.flex.col {:name :bread-signup :method :post}
       (anti-forgery-token-field)
       (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
       (hook ::html.enter-username
             [:p.instruct (:signup/please-choose-username-password i18n)])
       (when error-key
         (hook ::html.invalid-signup
               (ErrorMessage {:message (i18n/t i18n error-key)})))
       (Field :username :label (:auth/username i18n) :value (:username params))
       (Field :password
              :type :password
              :label (:auth/password i18n)
              :input-attrs {:maxlength (:auth/max-password-length config)})
       (Field :password-confirmation
              :type :password
              :label (:auth/password-confirmation i18n)
              :input-attrs {:maxlength (:auth/max-password-length config)})
       (hook ::html.password-guidelines
             [:p.instruct
              (i18n/t i18n [:auth/password-must-be-between
                            (:auth/min-password-length config)
                            (:auth/max-password-length config)])])
       (Submit (:signup/create-account i18n))]])})

(defmethod Section :flash [{:keys [session ring/flash i18n]} _]
  [:<>
   (when-let [success-key (:success-key flash)]
     (SuccessMessage {:message (i18n/t i18n success-key)}))
   (when-let [error-key (:error-key flash)]
     (ErrorMessage {:message (i18n/t i18n error-key)}))])

(defmethod Section :save [{:keys [i18n]} _]
  (Submit (:account/save i18n) :name :action :value "update-details"))

(defn- CustomizingSection [_]
  {:id :customizing
   :title "Customizing RISE"
   :content
   [:<>
    [:p
     "RISE is designed to be extensible via CSS variables, AKA custom properties.
     By overriding these, you can get a lot of variation from the core theme.
     Of course, you can extend it further by serving your own custom CSS."]
    [:p
     "This technique is powerful, but if your needs are more complex, look into
     creating your own custom theme with its own pattern library."]]})

(defc PatternLibrary [{:as data :keys [hook i18n]}]
  {:extends Page}
  (let [patterns [(IntroSection data)
                  (HowToSection data)
                  (TypographySection data)
                  SuccessMessage
                  ErrorMessage
                  Page
                  LoginPage
                  ForgotPasswordPage
                  ResetPasswordPage
                  AccountNav
                  SettingsPage
                  AccountPage
                  (CustomizingSection data)]]
    {:title "RISE"
     :head (hook ::theme/html.pattern-library.head
                 [:<>
                  [:link {:rel :stylesheet :href "/rise/patterns/highlight/styles/atom-one-dark.min.css"}]
                  [:script {:src "/rise/patterns/highlight/highlight.min.js"}]
                  [:script {:src "/rise/patterns/patterns.js"}]
                  [:link {:rel :stylesheet :href "/rise/patterns/patterns.css"}]])
     :content
     [:<>
      [:div#theme-toggle-container
       [:button#toggle-theme {:style {:position :relative}} "light/dark"]]
      (theme/TableOfContents {:patterns patterns})
      [:main.gap-spacious
       (map (partial theme/Pattern {:i18n i18n}) patterns)]]}))
