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
  [{:keys [min-password-length max-password-length invite-only?]
    {:keys [username password password-confirmation]} :params
    :or {username "" password "" password-confirmation ""}}
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
                  (not username-available?) :signup/username-exists
                  (not password-fields-match?) :auth/passwords-must-match
                  (not password-gte-min?)
                  [:auth/password-must-be-at-least min-password-length]
                  (not password-lte-max?)
                  [:auth/password-must-be-at-most max-password-length]))]
    ;; TODO support custom validations...
    [valid? error]))

(defmethod bread/expand ::check-invitation-age
  [{:keys [invitation-expiration-seconds]} {:keys [invitation]}]
  (let [invited-at (:thing/updated-at invitation)
        invitation-valid?
        (or (zero? invitation-expiration-seconds)
            (and invited-at (.after invited-at (t/seconds-ago
                                                 invitation-expiration-seconds))))]
    (when invitation-valid? invitation)))

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

(defmethod bread/action ::render
  [{:as res {[valid? _] :validation} ::bread/data} {:keys [to]} _]
  (if valid?
    (let [to (or to (bread/config res :auth/login-uri))]
      (-> res
          (assoc :status 302)
          (assoc-in [:headers "Location"] to)))
    (assoc res :status 400)))

(comment
  (sha-512 "a7d190e5-d7f4-4b92-a751-3c36add92610")
  (sha-512 ":a7d190e5-d7f4-4b92-a751-3c36add92610")
  (sha-512 (str (System/getenv "AUTH_SECRET_KEY")
                ":a7d190e5-d7f4-4b92-a751-3c36add92610"))
  ,)

(defmethod bread/dispatch ::signup=>
  [{:keys [params request-method] :as req}]
  (let [secret-key (bread/config req :auth/secret-key)
        invitation-queries [(when (:code params)
                               {:expansion/name ::db/query
                                :expansion/description
                                "Query invitation by code."
                                :expansion/key :invitation
                                :expansion/db (auth/database req)
                                :expansion/args
                                ['{:find [(pull ?e [:thing/updated-at
                                                    :invitation/code
                                                    {:invitation/email [*]}]) .]
                                   :in [$ ?code]
                                   :where [[?e :invitation/code ?code]
                                           (not [?e :invitation/redeemer])]}
                                 (sha-512 (str secret-key ":" (:code params)))]})
                            {:expansion/name ::check-invitation-age
                             :expansion/description
                             "Ensure invitation is sufficiently recent."
                             :expansion/key :invitation
                             :invitation-expiration-seconds
                             (bread/config req :signup/invitation-expiration-seconds)}]]
    (cond
      ;; Viewing signup page
      (= :get request-method)
      {:expansions invitation-queries}

      ;; Submitting new username/password
      (= :post request-method)
      (let [hash-algo (bread/config req :auth/hash-algorithm)
            password-hash (hashers/derive (:password params) {:alg hash-algo})
            user {:user/username (:username params)
                  :user/password password-hash
                  :thing/created-at (t/now)}]
        {:expansions
         (concat invitation-queries
                 [{:expansion/key :existing-username
                   :expansion/name ::db/query
                   :expansion/description
                   "Check for existing users by username."
                   :expansion/db (auth/database req)
                   :expansion/args
                   ['{:find [?e .]
                      :in [$ ?username]
                      :where [[?e :user/username ?username]]}
                    (:username params)]}
                  {:expansion/key :validation
                   :expansion/name ::validate
                   :expansion/description "Validate this signup request."
                   :params params
                   :min-password-length (bread/config req :auth/min-password-length)
                   :max-password-length (bread/config req :auth/max-password-length)
                   :invite-only? (bread/config req :signup/invite-only?)}])
         :effects
         [{:effect/name ::enact-valid-signup
           :effect/key :new-user
           :effect/description "If the signup is valid, create the account."
           :user user
           :conn (db/connection req)}]
         :hooks
         {::bread/render
          [{:action/name ::render
            :action/description "Render signup page or redirect to login"}]}}))))

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
