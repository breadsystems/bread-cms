(ns systems.bread.alpha.plugin.signup
  (:require
    [buddy.hashers :as hashers]
    [one-time.core :as ot]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc]]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.auth :as auth])
  (:import
    [java.util Date UUID]))

(defn- ->uuid [x]
  (if (string? x)
    (try (UUID/fromString x) (catch IllegalArgumentException _ nil))
    x))

(defc SignupPage
  [{:as data
    :keys [error hook i18n invitation session rtl? dir params]
    :signup/keys [config effect]}]
  {}
  (let [step (:signup/step session)
        signup-step? (nil? step)
        mfa-step? (= :multi-factor step)
        {:keys [invite-only? require-mfa?]} config]
    [:html {:lang (:field/lang data) :dir dir}
     [:head
      [:meta {:content-type "utf-8"}]
      (hook ::html.title [:title (str (:signup/signup i18n) " | Bread")])
      (auth/LoginStyle data)]
     [:body
      [:pre (pr-str config)]
      [:pre (pr-str session)]
      [:pre (pr-str params)]
      (cond
        (and signup-step? invite-only? (not (:code params)))
        [:main
         [:p "This site is invite-only."]]

        (and signup-step? invite-only? (not invitation))
        [:main
         [:p "This invitation link is either invalid or has been redeemed."]]

        (and signup-step? invite-only?)
        [:main
         [:p (:code params)]
         [:pre (pr-str invitation)]
         [:form {:name :bread-signup :method :post}
          (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
          (hook ::html.enter-username
                [:p.instruct "Please choose a username and password."])
          [:div.field
           [:label {:for :user} (:auth/username i18n)]
           [:input {:id :user :type :text :name :username :value (:username params)}]]
          [:div.field
           [:label {:for :password} (:auth/password i18n)]
           [:input {:id :password
                    :type :password
                    :name :password
                    :maxlength (:max-password-length config)}]]
          [:div.field
           [:label {:for :password-confirmation} (:auth/password-confirmation i18n)]
           [:input {:id :password-confirmation
                    :type :password
                    :name :password-confirmation
                    :maxlength (:max-password-length config)}]]
          (when error
            (hook ::html.invalid-signup
                  [:div.error [:p error]]))
          [:div
           [:button {:type :submit} "Create my account"]]]]

        ;; Open signup
        signup-step?
        [:main
         [:pre (pr-str @effect)]
         [:p "open"]]

        mfa-step?
        [:main
         [:form {:name :bread-signup :method :post}
          (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
          (hook ::html.setup-mfa
                [:p.instruct "Multi-factor authentication is required. Please scan the QR code in your authenticator app, and enter the code below."])
          [:div "QR CODE HERE"]
          [:div.field
           [:label {:for :password} (:auth/password i18n)]
           [:input {:id :password
                    :type :password
                    :name :password
                    :maxlength (:max-password-length config)}]]
          (when error
            (hook ::html.invalid-login
                  [:div.error [:p error]]))
          [:div
           [:button {:type :submit} "Create my account"]]]]

        ;;
        )]]))

(defmethod bread/action ::validate
  [{:as res
    :keys [headers session]
    {:keys [code username password password-confirmation]} :params} _ _]
  (let [{:signup/keys [step]} session
        signup-step? (nil? step)
        signup-uri (bread/config res :signup/signup-uri)
        min-length (bread/config res :signup/min-password-length)
        max-length (bread/config res :signup/max-password-length)
        password-fields-match? (= password password-confirmation)
        password? (seq password)
        password-meets-min? (>= (count password) min-length)
        password-meets-max? (<= (count password) max-length)
        valid-password? (bread/hook res ::valid-password?
                                    (and password-fields-match?
                                         password-meets-min?
                                         password-meets-max?))
        existing-user (get-in res [::bread/data :signup/existing-username])
        username-available? (false? existing-user)
        ;; TODO
        valid-code? true
        valid? (and username-available? valid-password? valid-code?)
        error (when-not valid?
                (cond
                  ;; TODO i18n
                  (not password?)
                  "Password is required."
                  (not password-fields-match?)
                  "Password fields must match."
                  (not password-meets-min?)
                  (format "Password must be at least %d characters long."
                          min-length)
                  (not password-meets-max?)
                  (format "Password must be at most %d characters long."
                          max-length)))
        require-mfa? (bread/config res :auth/require-mfa?)]
    (cond
      (and signup-step? valid? require-mfa?)
      (-> res
          (assoc :status 302
                 :headers (assoc headers "Location" signup-uri)
                 :session {:signup/step :multi-factor})
          (assoc-in [::bread/data :valid?] true))
      :invalid
      (-> res
          (assoc :status 400)
          (assoc-in [::bread/data :valid?] false)
          (assoc-in [::bread/data :error] error)))))

(comment

  (let [params (:params $effect)
        algo :bcrypt+blake2b-512
        now (Date.)
        tx {:thing/created-at now
            :thing/updated-at now
            :user/username (:username params)
            :user/password (hashers/derive (:password params) {:alg algo})}]
    [tx])

  (db/transact (:db $effect) [{:thing/created-at (Date.)
                               :user/username (:username )}])

  ;;
  )

(defmethod bread/effect ::enact-valid-signup
  [{:keys [conn user]} {:keys [valid? invitation]}]
  (when valid?
    (if invitation
      (let [code (:invitation/code invitation)]
        (db/transact conn [{:invitation/code code
                            :invitation/redeemer user}]))
      (db/transact conn [user]))))

(defmethod bread/dispatch ::signup=>
  [{:keys [params request-method session] :as req}]
  (let [{:signup/keys [step]} session
        invite-only? (bread/config req :signup/invite-only?)
        require-mfa? (bread/config req :auth/require-mfa?)
        post? (= :post request-method)
        get? (= :get request-method)
        signup-step? (nil? step)
        mfa-step? (= :multi-factor step)
        config {:invite-only? (bread/config req :signup/invite-only?)
                :require-mfa? (bread/config req :auth/require-mfa?)
                :min-password-length (bread/config req :signup/min-password-length)
                :max-password-length (bread/config req :signup/max-password-length)}
        invitation-query (when (:code params)
                           {:expansion/name ::db/query
                            :expansion/description
                            "Check for valid invite code."
                            :expansion/key :invitation
                            :expansion/db (db/database req)
                            :expansion/args
                            ['{:find [(pull ?e [:invitation/code]) .]
                               :in [$ ?code]
                               :where [[?e :invitation/code ?code]
                                       (not [?e :invitation/redeemer])]}
                             (->uuid (:code params))]})
        expansions [{:expansion/name ::bread/value
                     :expansion/description "Signup config"
                     :expansion/key :signup/config
                     :expansion/value config}]]
    (cond
      ;; Viewing signup page
      (and get? signup-step?)
      {:expansions (concat expansions [invitation-query])}

      ;; Submitting new username/password
      (and post? signup-step?)
      (let [hash-algo (bread/config req :auth/hash-algorithm)
            password-hash (hashers/derive (:password params) {:alg hash-algo})
            totp-key (when require-mfa? (ot/generate-secret-key))
            user (cond-> {:user/username (:username params)
                          :user/password password-hash
                          :thing/created-at (Date.)}
                   require-mfa? (assoc :user/totp-key totp-key))]
        {:expansions (concat expansions
                             [invitation-query
                              {:expansion/name ::db/query
                               :expansion/description
                               "Check for existing users by username."
                               :expansion/key :signup/existing-username
                               :expansion/db (db/database req)
                               :expansion/args
                               ['{:find [?e .]
                                  :in [$ ?username]
                                  :where [[?e :user/username ?username]]}
                                (:username params)]}])
         :effects
         [{:effect/name ::enact-valid-signup
           :effect/key :signup/effect
           :effect/description "If the signup is valid, create the account."
           :user user
           :conn (db/connection req)}]
         :hooks
         {::bread/expand
          [{:action/name ::validate
            :action/description "Set :session in Ring response"
            :config config}]}})

      ;; MFA required, rendering QR code
      (and get? mfa-step?)
      {;; render QR code
       ;; save TOTP key in session?
       :expansions (concat expansions [])}

      ;; MFA required, saving TOTP key
      (and post? mfa-step?)
      {:expansions (concat expansions [])
       ;; validate TOTP
       ;; save TOTP key
       ;; reset session
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response"
          :config config}]}}

      )))

(def
  ^{:doc "Schema for invitations"}
  schema
  (with-meta
    [{:db/id "migration.invitations"
      :migration/key :bread.migration/invitations
      :migration/description "Migration for invitations to sign up"}
     {:db/ident :invitation/email
      :attr/label "Invitation email"
      :db/doc "Email this invitation was sent to, if any"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.invitation"}
     {:db/ident :invitation/redeemer
      :attr/label "Redeeming user"
      :db/doc "User who redeemed this invitation, if any"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.invitation"}
     {:db/ident :invitation/code
      :attr/label "Invitation code"
      :db/doc "Secure UUID for this invitation"
      ;:attr/sensitive? true
      :db/unique :db.unique/identity
      :db/valueType :db.type/uuid
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.invitation"}
     {:db/ident :invitation/invited-by
      :attr/label "Invited by"
      :db/doc "User who created this invitation"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.invitation"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/things
                               :bread.migration/users
                               :bread.migration/authentication}}))

(defmethod bread/action ::protected-route?
  [{:as req :keys [uri]} _ [protected?]]
  (and protected? (not= (bread/config req :signup/signup-uri) uri)))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [;; TODO email as a normal hook
            invite-only? min-password-length max-password-length
            invitation-expiration-seconds signup-uri require-mfa?
            mfa-issuer]
     :or {invite-only? false
          require-mfa? false
          min-password-length 12
          max-password-length 72
          invitation-expiration-seconds (* 72 60 60)
          signup-uri "/signup"}}]
   {:hooks
    {::db/migrations
     [{:action/name ::db/add-schema-migration
       :action/description
       "Add schema for invitations to the sequence of migrations to be run."
       :schema-txs schema}]
     ::auth/protected-route?
     [{:action/name ::protected-route?
       :action/description "Exclude signup-uri from protected routes"}]
     ::i18n/global-strings
     [{:action/name ::i18n/merge-global-strings
       :action/description "Merge strings for signup into global strings."
       :strings {:en #:signup{:signup "Signup"}}}]}
    :config
    {:signup/invite-only? invite-only?
     :signup/min-password-length min-password-length
     :signup/max-password-length max-password-length
     :signup/invitation-expiration-seconds invitation-expiration-seconds
     :signup/signup-uri signup-uri
     :signup/mfa-issuer mfa-issuer}}))
