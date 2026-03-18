(ns systems.bread.alpha.plugin.signup
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [crypto.random :as random]
    [one-time.core :as ot]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc] :as component]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.email :as email])
  (:import
    [java.net URLEncoder]))

(defmethod bread/expand ::validate
  [{{:auth/keys [min-password-length max-password-length]
     :signup/keys [invite-only?]} :config
    {:keys [username password password-confirmation]} :params}
   {:as data :keys [existing-username invitation]}]
  (let [username? (seq username)
        password? (seq password)
        password-fields-match? (= password password-confirmation)
        password-gte-min? (>= (count password) min-password-length)
        password-lte-max? (<= (count password) max-password-length)
        valid-password? (and password-fields-match?
                             password-gte-min?
                             password-lte-max?)
        username-available? (false? existing-username)
        invited? (or (not invite-only?) (boolean invitation))
        valid? (and username-available? valid-password? invited?)
        error (when-not valid?
                (cond
                  (or (not username?) (not password?)) :signup/all-fields-required
                  (not password-fields-match?) :auth/passwords-must-match
                  (not password-gte-min?)
                  [:auth/password-must-be-at-least min-password-length]
                  (not password-lte-max?)
                  [:auth/password-must-be-at-most max-password-length]))]
    [valid? error]))

(defmethod bread/effect ::enact-valid-signup
  [{:keys [conn user]} {:keys [invitation] [valid? _] :validation}]
  (when valid?
    (if invitation
      (let [email (when-let [email (:invitation/email invitation)]
                    (assoc email :email/confirmed-at (t/now)))
            user (if email
                   (assoc user :user/emails [email])
                   user)]
        {:effects [{:effect/name ::db/transact
                    :conn conn
                    :effect/description "Redeem invitation and create user."
                    :txs [{:invitation/code (:invitation/code invitation)
                           :invitation/redeemer user}]}]})
      {:effects [{:effect/name ::db/transact
                  :conn conn
                  :effect/description "Create user"
                  :txs [user]}]})))

(defmethod bread/action ::redirect
  [{:as res {[valid? _] :validation} ::bread/data} {:keys [to]} _]
  (if valid?
    (let [to (or to (bread/config res :auth/login-uri))]
      (-> res
          (assoc :status 302)
          (assoc-in [:headers "Location"] to)))
    res))

(defn- ->int [x]
  (try (Integer. x) (catch java.lang.NumberFormatException _ nil)))

(defmethod bread/expand ::validate-invitation
  [{{:auth/keys [max-invitations-count
                 max-invitations-window-minutes
                 max-total-invitations]} :config
    {:keys [id action email]} :params}
   {:as data
    :keys [existing-email user]
    {invitations :invitation/_invited-by} :user}]
  (let [action (keyword action)
        new-invite? (= :send action)
        resending? (= :resend action)
        revoking? (= :revoke action)
        valid-email? (when new-invite? (string/includes? email "@"))
        pending-ids (->> invitations
                         (filter (complement :invitation/redeemer))
                         (map :db/id)
                         set)
        id (->int id)
        pending? (contains? pending-ids id)
        invitation-invalid? (and (or resending? revoking?)
                                 (or (not id) (not pending?)))
        error-key (cond
                    (and new-invite? existing-email) :signup/email-exists
                    (and new-invite? (not valid-email?)) :email/invalid-email
                    invitation-invalid? :signup/invitation-invalid)
        valid? (not error-key)]
    [valid? error-key]))

(comment
  (def $effect
    {:effect/name :systems.bread.alpha.plugin.signup/invitation-email,
     :effect/description "Send an invitation email.",
     :code "asdfqwerty",
     :params
     {:email "test@tamayo.email",
      :action "send"}})

  (:code $effect)
  (:signup/signup-uri (:config $data))
  (:user $data)
  (:ring/scheme $data)
  (:ring/server-name $data)
  (:ring/server-port $data)
  (invitation-email-subject $data)
  (invitation-email-body
    (assoc $data
           :link
           (invitation-link (assoc $data :invitation/code (:code $effect)))))
  ,)

(defn invitation-link [{:keys [config
                               invitation/code
                               ring/scheme
                               ring/server-name
                               ring/server-port]}]
  (format "%s://%s%s%s?code=%s"
          (name scheme) server-name (when server-port (str ":" server-port))
          (:signup/signup-uri config) (URLEncoder/encode code)))

(defn invitation-email-subject [{:keys [config i18n ring/server-name]}]
  (let [site-name (:site/name config server-name)]
    (i18n/t i18n [:signup/invitation-email-subject site-name])))

(defn invitation-email-body [{:keys [config i18n link ring/server-name user]}]
  (let [from-name (or (:user/name user) (:user/username user))
        site-name (:site/name config server-name)]
    (i18n/t i18n [:signup/invitation-email-body from-name site-name link])))

(defmethod bread/effect ::invitation-email
  [{:as effect :keys [code from to]}
   {:as data :keys [config hook]}]
  (prn 'EMAIL)
  (let [from (or from (:email/smtp-from-email config))
        link (invitation-link (assoc data :invitation/code code))
        message (hook ::invitation-message
                      {:from from
                       :to to
                       :subject (invitation-email-subject data)
                       :body (invitation-email-body (assoc data :link link))})]
    {:effects
     [{:effect/name ::email/send!
       :effect/description "Send invitation email."
       :message message}]}))

(defmethod bread/effect [::invite :send] send-invitation
  [{:keys [conn params]}
   {:as data
    :keys [config i18n existing-email user]
    [valid? error-key] :validation}]
  (if valid?
    (let [email (:email params)
          code (random/url-part 32)
          now (t/now)
          invitation-tx {:invitation/code code
                         :invitation/invited-by (:db/id user)
                         :invitation/email {:email/address email
                                            :thing/created-at now}
                         :thing/created-at now}
          email-effect (when valid?
                         {:effect/name ::invitation-email
                          :effect/description "Send an invitation email."
                          :code code
                          :to email})]
      (try
        (log/info "sending invitation email" {:email email
                                              :invitation/invited-by (:db/id user)})
        (db/transact conn [invitation-tx])
        {:effects [email-effect]
         :flash {:success-key :signup/invitation-sent}}
        (catch clojure.lang.ExceptionInfo e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))
    {:flash {:error-key error-key}}))

(defmethod bread/effect [::invite :resend] resend-invitation
  [{:keys [conn params]}
   {:as data
    :keys [config i18n user]
    [valid? error-key] :validation}]
  (if valid?
    (let [id (->int (:id params))
          code (random/url-part 32)
          now (t/now)
          invitation-tx {:db/id id
                         :invitation/code code
                         :thing/updated-at now}
          invitation (first (filter #(= id (:db/id %)) (:invitation/_invited-by user)))
          to (:email/address (:invitation/email invitation))
          email-effect {:effect/name ::invitation-email
                        :effect/description "Resend invitation with a new code."
                        :code code
                        :to to}]
      (try
        (log/info "resending invitation email" {:email to
                                                :invitation/invited-by (:db/id user)})
        (db/transact conn [invitation-tx])
        {:effects [email-effect]
         :flash {:success-key :signup/invitation-sent}}
        (catch clojure.lang.ExceptionInfo e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))
    {:flash {:error-key error-key}}))

(defmethod bread/effect [::invite :revoke] resend-invitation
  [{:keys [conn params]}
   {:as data
    :keys [config i18n user]
    [valid? error-key] :validation}]
  ;; TODO
  )

(defmethod bread/dispatch ::invitations=>
  [{:keys [::bread/dispatcher params request-method server-name]
    {:keys [user]} :session
    :as req}]
  "Invitations page in the account section"
  (let [post? (= :post request-method)
        action (when (seq (:action params)) (keyword (:action params)))
        pull (conj (:dispatcher/pull dispatcher) :user/name)
        query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}
        user-expansion {:expansion/key (:dispatcher/key dispatcher :user)
                        :expansion/name ::db/query
                        :expansion/description "Query for user emails."
                        :expansion/db (db/database req)
                        :expansion/args [query (:db/id user)]}
        email-expansion #_(if (= :send action))
        {:expansion/key :existing-email
         :expansion/name ::db/query
         :expansion/description "Query for conflicting emails."
         :expansion/db (db/database req)
         :expansion/args ['{:find [?e .]
                            :in [$ ?email]
                            :where [[?e :email/address ?email]]}
                          (:email params)]}
        #_
        {:expansion/key :pending-invitation
         :expansion/name ::db/query
         :expanction/description "Query for a pending invitation."
         :expansion/db (db/database req)
         :expansion/args
         ['{:find [(pull ?e [:db/id :invitation/email]) .]
            :in [$ ?from ?e]
            :where [[?e :invitation/invited-by ?from]
                    (not [?e :invitation/redeemer])]}
          (Integer. (:id params))]}
        validation {:expansion/key :validation
                    :expansion/name ::validate-invitation
                    :params params
                    :config (::bread/config req)}]
    (if post?
      {:expansions [user-expansion email-expansion validation]
       :effects
       [{:effect/name [::invite action]
         :effect/description "Email an invitation, pending validation."
         :effect/key action
         :params params
         :conn (db/connection req)}]
       :hooks
       {::bread/render
        [{:action/name ::ring/effect-redirect
          :action/description "Redirect to invitations page."
          :effect/key action
          :to (bread/config req :signup/invitations-uri)}]}}
      {:expansions [user-expansion]})))

(defmethod bread/dispatch ::signup=>
  [{:keys [params request-method] :as req}]
  (let [require-mfa? (bread/config req :auth/require-mfa?)
        invitation-query (when (:code params)
                           {:expansion/name ::db/query
                            :expansion/description
                            "Check for valid invite code."
                            :expansion/key :invitation
                            :expansion/db (db/database req)
                            :expansion/args
                            ['{:find [(pull ?e [:invitation/code
                                                {:invitation/email [*]}]) .]
                               :in [$ ?code]
                               :where [[?e :invitation/code ?code]
                                       (not [?e :invitation/redeemer])]}
                             (:code params)]})
        expansions [{:expansion/key :config
                     :expansion/name ::bread/value
                     :expansion/description "Signup config"
                     :expansion/value (::bread/config req)}]]
    (cond
      ;; Viewing signup page
      (= :get request-method)
      {:expansions (concat expansions [invitation-query])}

      ;; Submitting new username/password
      (= :post request-method)
      (let [hash-algo (bread/config req :auth/hash-algorithm)
            password-hash (hashers/derive (:password params) {:alg hash-algo})
            user {:user/username (:username params)
                  :user/password password-hash
                  :thing/created-at (t/now)}]
        {:expansions (concat expansions
                             [invitation-query
                              {:expansion/key :existing-username
                               :expansion/name ::db/query
                               :expansion/description
                               "Check for existing users by username."
                               :expansion/db (db/database req)
                               :expansion/args
                               ['{:find [?e .]
                                  :in [$ ?username]
                                  :where [[?e :user/username ?username]]}
                                (:username params)]}
                              {:expansion/key :validation
                               :expansion/name ::validate
                               :params params
                               :config (::bread/config req)}])
         :effects
         [{:effect/name ::enact-valid-signup
           :effect/key :new-user
           :effect/description "If the signup is valid, create the account."
           :user user
           :conn (db/connection req)}]
         :hooks
         {::bread/render
          [{:action/name ::redirect
            :action/description "Redirect to login"}]}}))))

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
      :db/doc "Secure ID for this invitation"
      :attr/sensitive? true
      :db/unique :db.unique/identity
      :db/valueType :db.type/string
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
            invite-only?
            invitation-expiration-seconds
            invitations-uri
            signup-uri]
     :or {invite-only? false
          invitation-expiration-seconds (* 72 60 60)
          invitations-uri "/~/invitations"
          signup-uri "/_/signup"}}]
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
       :strings (edn/read-string (slurp (io/resource "signup.i18n.edn")))}]}
    :config
    {:signup/invite-only? invite-only?
     :signup/invitation-expiration-seconds invitation-expiration-seconds
     :signup/signup-uri signup-uri
     :signup/invitations-uri invitations-uri}}))
