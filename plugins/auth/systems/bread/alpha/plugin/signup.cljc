(ns systems.bread.alpha.plugin.signup
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
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
    :keys [config error hook i18n invitation rtl? dir params]
    [valid? error-key] :validation}]
  {}
  [:html {:lang (:field/lang data) :dir dir}
   [:head
    [:meta {:content-type "utf-8"}]
    (hook ::html.title [:title (str (:signup/signup i18n) " | Bread")])
    (->> (auth/LoginStyle data) (hook ::auth/html.stylesheet) (hook ::html.signup.stylesheet))
    (->> [:<>] (hook ::auth/html.head) (hook ::html.signup.head))]
   [:body
    (cond
      (and (:signup/invite-only? config) (not (:code params)))
      [:main
       [:form.flex-col
        (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
        [:p (:signup/site-invite-only i18n)]]]

      (and (:signup/invite-only? config) (not invitation))
      [:main
       [:form.flex-col
        (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
        [:p (:signup/invitation-invalid i18n)]]]

      :default
      [:main
       [:form.flex-col {:name :bread-signup :method :post}
        (hook ::html.signup-heading [:h1 (:signup/signup i18n)])
        (hook ::html.enter-username
              [:p.instruct (:signup/please-choose-username-password i18n)])
        [:div.field
         [:label {:for :user} (:auth/username i18n)]
         [:input {:id :user :type :text :name :username :value (:username params)}]]
        [:div.field
         [:label {:for :password} (:auth/password i18n)]
         [:input {:id :password
                  :type :password
                  :name :password
                  :maxlength (:auth/max-password-length config)}]]
        [:div.field
         [:label {:for :password-confirmation} (:auth/password-confirmation i18n)]
         [:input {:id :password-confirmation
                  :type :password
                  :name :password-confirmation
                  :maxlength (:auth/max-password-length config)}]]
        (when error-key
          (hook ::html.invalid-signup
                [:div.error [:p (if (sequential? error-key) ;; TODO tongue?
                                  (let [[k & args] error-key]
                                    (apply format (get i18n k) args))
                                  (get i18n error-key))]]))
        [:div.field
         [:span.spacer]
         [:button {:type :submit} (:signup/create-account i18n)]]]])]])

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
      {:effects [{:effect/name ::db/transact
                  :conn conn
                  :effect/description "Redeem invitation and create user."
                  :txs [{:invitation/code (:invitation/code invitation)
                         :invitation/redeemer user}]}]}
      {:effects [{:effect/name ::db/transact
                  :conn conn
                  :effect/description "Create user"
                  :txs [user]}]})))

(defmethod bread/action ::redirect
  [{:as res {[valid? _] :validation} ::bread/data} _ _]
  (if valid?
    (-> res
        (assoc :status 302)
        (assoc-in [:headers "Location"] (bread/config res :auth/login-uri)))
    res))

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
                            ['{:find [(pull ?e [:invitation/code]) .]
                               :in [$ ?code]
                               :where [[?e :invitation/code ?code]
                                       (not [?e :invitation/redeemer])]}
                             (->uuid (:code params))]})
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
                  :thing/created-at (Date.)}]
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
            invite-only? invitation-expiration-seconds signup-uri]
     :or {invite-only? false
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
       :strings (edn/read-string (slurp (io/resource "signup.i18n.edn")))}]}
    :config
    {:signup/invite-only? invite-only?
     :signup/invitation-expiration-seconds invitation-expiration-seconds
     :signup/signup-uri signup-uri}}))
