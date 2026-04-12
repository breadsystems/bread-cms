(ns systems.bread.alpha.plugin.signup
  (:require
    [buddy.hashers :as hashers]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.auth :as auth])
  (:import
    [java.net URLEncoder]))

(defmethod bread/expand ::validate
  [{{:auth/keys [min-password-length max-password-length]
     :signup/keys [invite-only?]} :config
    {:keys [username password password-confirmation]} :params}
   {:keys [existing-username invitation]}]
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

(defmethod bread/dispatch ::signup=>
  [{:keys [params request-method] :as req}]
  (let [invitation-queries [(when (:code params)
                               {:expansion/name ::db/query
                                :expansion/description
                                "Check for valid invite code."
                                :expansion/key :invitation
                                :expansion/db (db/database req)
                                :expansion/args
                                ['{:find [(pull ?e [:thing/updated-at
                                                    :invitation/code
                                                    {:invitation/email [*]}]) .]
                                   :in [$ ?code]
                                   :where [[?e :invitation/code ?code]
                                           ;; TODO expire code
                                           (not [?e :invitation/redeemer])]}
                                 (sha-512 (:code params))]})]
        expansions [{:expansion/key :config
                     :expansion/name ::bread/value
                     :expansion/description "Signup config"
                     :expansion/value (::bread/config req)}]]
    (cond
      ;; Viewing signup page
      (= :get request-method)
      {:expansions (concat expansions invitation-queries)}

      ;; Submitting new username/password
      (= :post request-method)
      (let [hash-algo (bread/config req :auth/hash-algorithm)
            password-hash (hashers/derive (:password params) {:alg hash-algo})
            user {:user/username (:username params)
                  :user/password password-hash
                  :thing/created-at (t/now)}]
        {:expansions (concat expansions
                             invitation-queries
                             [{:expansion/key :existing-username
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

(defmethod bread/action ::protected-route?
  [{:as req :keys [uri]} _ [protected?]]
  (and protected? (not= (bread/config req :signup/signup-uri) uri)))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [invite-only?
            invitation-expiration-seconds
            signup-uri]
     :or {invite-only? false
          invitation-expiration-seconds (* 72 60 60)
          signup-uri "/_/signup"}}]
   {:hooks
    {::auth/protected-route?
     [{:action/name ::protected-route?
       :action/description "Exclude signup-uri from protected routes"}]
     ::i18n/global-strings
     [{:action/name ::i18n/merge-global-strings
       :action/description "Merge strings for signup into global strings."
       :strings (i18n/read-strings "signup.i18n.edn")}]}
    :config
    {:signup/invite-only? invite-only?
     :signup/invitation-expiration-seconds invitation-expiration-seconds
     :signup/signup-uri signup-uri}}))
