(ns systems.bread.alpha.plugin.invitations
  (:require
    [clojure.string :as string]
    [crypto.random :as random]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.email :as email]
    [systems.bread.alpha.ring :as ring])
  (:import
    [java.net URLEncoder]))

(defn- ->int [x]
  (try (Integer. x) (catch java.lang.NumberFormatException _ nil)))

(defmethod bread/expand ::validate-invitation
  [{{:invitations/keys [max-window-count
                        max-window-minutes
                        max-total]} :config
    {:keys [id action email]} :params}
   {:keys [existing-email]
    {invitations :invitation/_invited-by} :user}]
  (let [action (keyword action)
        total-reached? (when max-total
                         (>= (count invitations) max-total))
        window-cutoff (when max-window-minutes (t/minutes-ago max-window-minutes))
        recent-count (count (filter (fn [{:keys [thing/updated-at]}]
                                      (when updated-at
                                        (.after updated-at window-cutoff)))
                                    invitations))
        new-invite? (= :send action)
        resending? (= :resend action)
        revoking? (= :revoke action)
        valid-email? (when new-invite? (string/includes? email "@"))
        pending-ids (->> invitations
                         (filter (complement :invitation/redeemer))
                         (map :db/id)
                         set)
        id (->int id)
        ;; To avoid an extra query, we just loop through the user's pending
        ;; invitations to check whether the given id is one the user is
        ;; authorized to revoke.
        pending? (contains? pending-ids id)
        invitation-invalid? (and (or resending? revoking?)
                                 (or (not id) (not pending?)))
        rate-limited? (and recent-count max-window-count
                           (or new-invite? resending?)
                           (>= recent-count max-window-count))
        error-key (cond
                    (and new-invite? existing-email) :signup/email-exists
                    (and new-invite? (not valid-email?)) :email/invalid-email
                    (and new-invite? total-reached?) :total-reached
                    rate-limited? :invitations/sending-too-many
                    invitation-invalid? :invitations/invitation-invalid)
        valid? (not error-key)]
    [valid? error-key]))

(comment
  (def $effect
    {:effect/name ::email
     :effect/description "Send an invitation email."
     :code "asdfqwerty"
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
    (i18n/t i18n [:invitations/invitation-email-subject site-name])))

(defn invitation-email-body [{:keys [config i18n link ring/server-name user]}]
  (let [from-name (or (:user/name user) (:user/username user))
        site-name (:site/name config server-name)]
    (i18n/t i18n [:invitations/invitation-email-body from-name site-name link])))

(defmethod bread/effect ::invitation-email
  [{:keys [code from to]} {:as data :keys [config hook]}]
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
  [{:keys [conn params]} {:keys [user] [valid? error-key] :validation}]
  (if valid?
    (let [email (:email params)
          code (random/url-part 32)
          now (t/now)
          invitation-tx {:invitation/code code
                         :invitation/invited-by (:db/id user)
                         :invitation/email {:email/address email
                                            :thing/created-at now
                                            :thing/updated-at now}
                         :thing/created-at now
                         :thing/updated-at now}
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
         :flash {:success-key :invitations/invitation-sent}}
        (catch clojure.lang.ExceptionInfo e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))
    {:flash {:error-key error-key}}))

(defmethod bread/effect [::invite :resend] resend-invitation
  [{:keys [conn params]} {:keys [user] [valid? error-key] :validation}]
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
         :flash {:success-key :invitations/invitation-resent}}
        (catch clojure.lang.ExceptionInfo e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))
    {:flash {:error-key error-key}}))

(defmethod bread/effect [::invite :revoke] revoke-invitation
  [{:keys [conn params]} {:keys [user] [valid? error-key] :validation}]
  (if valid?
    (let [id (->int (:id params))
          invitation (first (filter #(= id (:db/id %)) (:invitation/_invited-by user)))
          email-id (:db/id (:invitation/email invitation))]
      (try
        (db/transact conn [[:db/retractEntity id]
                           [:db/retractEntity email-id]])
        {:flash {:success-key :invitations/invitation-revoked}}
        (catch clojure.lang.ExceptionInfo e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))
    {:flash {:error-key error-key}}))

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
        email-expansion {:expansion/key :existing-email
                         :expansion/name ::db/query
                         :expansion/description "Query for conflicting emails."
                         :expansion/db (db/database req)
                         :expansion/args ['{:find [?e .]
                                            :in [$ ?email]
                                            :where [[?e :email/address ?email]]}
                                          (:email params)]}
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
          :to (bread/config req :invitations/invitations-uri)}]}}
      {:expansions [user-expansion]})))

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

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [invitations-uri
            max-window-count
            max-window-minutes
            max-total]
     :or {invitations-uri "/~/invitations"
          max-window-count 10
          max-window-minutes 5}}]
   {:hooks
    {::db/migrations
     [{:action/name ::db/add-schema-migration
       :action/description
       "Add schema for invitations to the sequence of migrations to be run."
       :schema-txs schema}]
     ::i18n/global-strings
     [{:action/name ::i18n/merge-global-strings
       :action/description "Merge strings for signup into global strings."
       :strings (i18n/read-strings "invitations.i18n.edn")}]}
    :config
    #:invitations{:invitations-uri invitations-uri
                  :max-window-count max-window-count
                  :max-window-minutes max-window-minutes
                  :max-total max-total}}))
