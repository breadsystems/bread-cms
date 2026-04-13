(ns systems.bread.alpha.plugin.account
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [com.rpl.specter :as s]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.invitations :as invitations]
    [systems.bread.alpha.plugin.email :as email]))

(defmethod bread/action ::account-uri? [{:as req :keys [uri]} _ [protected?]]
  (or protected? (= (bread/config req :account/account-uri) uri)))

(defmethod bread/expand ::user [_ {:keys [user]}]
  ;; TODO infer from query/schema...
  (when user
    (as-> user $
      (update $ :user/preferences edn/read-string)
      (s/transform [:user/sessions s/ALL :session/data] edn/read-string $))))

(defmulti effects (fn [{:keys [params]}] (keyword (:action params))))

(defmethod effects :default [_]
  ;; TODO standardize generic error-key?
  (throw (ex-info "Invalid action" {:error-key :account/invalid-action})))

(defmethod effects :delete-session [{:as req :keys [params]}]
  (when (try (Integer. (:dbid params)) (catch Throwable _ nil))
    [{:effect/name [::update :delete-session]
      :effect/description "Update account state"
      :effect/key :delete-session
      :success-key :account/session-deleted
      :params params
      :conn (db/connection req)}]))

(defn validate-password-fields
  [{:auth/keys [min-password-length max-password-length]}
   {:keys [password password-confirmation]}]
  "Returns an error code as a keyword if the :password and/or :password-confirmation
  params are invalid."
  (cond
    ;; If the user submitted only the password confirmation, assume they intended
    ;; to update password but forgot to fill out both fields.
    (and (seq password-confirmation) (empty? password)) :auth/passwords-must-match
    (empty? password) :auth/password-required
    (not= password password-confirmation) :auth/passwords-must-match
    (< (count password) min-password-length)
    [:auth/password-must-be-at-least min-password-length]
    (> (count password) max-password-length)
    [:auth/password-must-be-at-most max-password-length]))

(defn- hook-preference [req [k v]]
  [k (bread/hook req [::preference k] v)])

(defmethod effects :update-details
  [{:as req :keys [params session] ::bread/keys [config]}]
  (let [{:keys [password password-confirmation]} params
        update-password? (or (seq password) (seq password-confirmation))
        error-key (when update-password? (validate-password-fields config params))
        hash-algo (when update-password? (:auth/hash-algorithm config))
        preferences (dissoc params :action :name :lang :password :password-confirmation)]
    (when error-key (throw (ex-info "Invalid password" {:error-key error-key})))
    [{:effect/name ::db/transact
      :effect/description "Update account details"
      :effect/key :update-details
      :success-key :account/account-updated
      :conn (db/connection req)
      :txs
      [(cond-> {:db/id (:db/id (:user session)) :user/name (:name params)}
         (:lang params) (assoc :user/lang (keyword (:lang params)))
         update-password? (assoc :user/password
                                 (hashers/derive password {:alg hash-algo}))
         (seq preferences)  (assoc :user/preferences (->> preferences
                                                          (map (partial hook-preference req))
                                                          (into {})
                                                          pr-str)))]}]))

(defmethod bread/effect [::update :delete-session]
  [{k :effect/key :keys [conn params success-key]} {user :user}]
  (let [session-id (try (Integer. (:dbid params)) (catch Throwable _ nil))
        valid-ids (set (map :db/id (:user/sessions user)))
        valid? (contains? valid-ids session-id)]
    (when valid?
      {:effects
       [{:effect/name ::db/transact
         :effect/description "Delete a user session."
         :effect/key k
         :success-key success-key
         :conn conn
         :txs [[:db/retractEntity session-id]]}]})))

(defmethod bread/dispatch ::account=>
  [{:as req :keys [params request-method session] ::bread/keys [dispatcher]}]
  (let [id (:db/id (:user session))
        pull (:dispatcher/pull dispatcher)
        user-pull {:find [(list 'pull '?e pull) '.] :in '[$ ?e]}
        query-user {:expansion/key :user
                    :expansion/name ::db/query
                    :expansion/description "Query for all user account data"
                    :expansion/db (db/database req)
                    :expansion/args [user-pull id]}
        expand-user {:expansion/key :user
                     :expansion/name ::user
                     :expansion/description "Expand user data"}]
    (if (= :post request-method)
      ;; Account update.
      (let [[effects error-key] (try
                                  [(effects req) nil]
                                  (catch clojure.lang.ExceptionInfo e
                                    [nil (-> e ex-data :error-key)]))]
        (if error-key
          {:hooks
           {::bread/render
            [{:action/name ::ring/redirect
              :to (bread/config req :account/account-uri)
              :flash (when error-key {:error-key error-key})
              :action/description
              "Redirect to account page after an error"}]}}
          {:expansions [query-user expand-user]
           :effects effects
           :hooks
           {::bread/render
            [{:action/name ::ring/effect-redirect
              :to (bread/config req :account/account-uri)
              :effect/key (keyword (:action params))
              :action/description
              "Redirect to account page after taking an account action"}]}}))
      ;; Rendering the account page.
      {:expansions
       [query-user
        expand-user
        {;; TODO => i18n
         :expansion/key :supported-langs
         :expansion/name ::bread/value
         :expansion/value (i18n/supported-langs req)
         :expansion/description "Supported languages"}
        {;; TODO => i18n
         :expansion/key :lang-names
         :expansion/name ::bread/value
         :expansion/description "Language names for display"
         :expansion/value (bread/config req :i18n/lang-names)}]})))

(defn plugin [{:keys [account-uri html-account-header html-account-form
                      html-account-sections timezone-options]
               :or {account-uri "/account"
                    ;; TODO MURCA
                    timezone-options ["America/Los_Angeles"
                                      "America/Denver"
                                      "America/Chicago"
                                      "America/New_York"]
                    html-account-header [::account-link
                                         ::email/settings-link
                                         ::invitations/invitations-link
                                         :spacer
                                         ::logout-form]
                    html-account-form [::heading
                                       :flash
                                       ::name
                                       ::pronouns
                                       ::lang
                                       ::timezone
                                       ::password
                                       :save]
                    html-account-sections [::account-form
                                           ::sessions
                                           #_ ;; TODO
                                           ::roles]}}]
  {:hooks
   {::auth/logged-in-uri
    [{:action/name ::bread/value
      :action/value account-uri
      :action/description "Redirect to account page after login."}]
    ::auth/protected-route?
    [{:action/name ::account-uri?
      :action/description "Whether request is for a protected account page."}]
    ::i18n/global-strings
    [;; TODO timezone strs...?
     {:action/name ::i18n/merge-global-strings
      :action/description "Merge strings for account page into global i18n strings."
      :strings (i18n/read-strings "account.i18n.edn")}]}
   :config
   #:account{:account-uri account-uri
             :timezone-options timezone-options
             :html.account.header html-account-header
             :html.account.sections html-account-sections
             :html.account.form html-account-form}})
