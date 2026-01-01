(ns systems.bread.alpha.plugin.email
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [crypto.random :as random]
    [postal.core :as postal]
    [taoensso.timbre :as log]

    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.thing :as thing])
  (:import
    [java.util Calendar]
    [java.net URLEncoder]))

(comment
  (doto (Calendar/getInstance)
    (.setTime (t/now))
    (.add Calendar/MINUTE -60))
  (compare (minutes-ago (t/now) 120) (minutes-ago (t/now) 1))
  (minutes-ago (t/now) 120))

(defn- minutes-ago [now minutes]
  (.getTime (doto (Calendar/getInstance)
              (.setTime now)
              (.add Calendar/MINUTE (- minutes)))))

(defn- summarize [email]
  (update email :body #(str "[" (-> % .getBytes count) " bytes]")))

(defn config->postal [{:email/keys [smtp-host
                                    smtp-port
                                    smtp-username
                                    smtp-password
                                    smtp-tls?]}]
  {:host smtp-host
   :port smtp-port
   :user smtp-username
   :pass smtp-password
   :tls smtp-tls?})

(defprotocol Mailer
  :extend-via-metadata true
  (send! [this email]))

(deftype PostalMailer [postal-config]
  Mailer
  (send! [this email]
    (postal/send-message postal-config email)))

(defmethod bread/effect ::send! send-smtp!
  [effect {{:as config :email/keys [dry-run? mailer smtp-from-email]} :config}]
  (let [send? (and (not dry-run?) (not (:dry-run? effect)))
        email {:from (or (:from effect) smtp-from-email)
               :to (:to effect)
               :subject (:subject effect)
               :body (:body effect)}]
    (if send?
      (try
        (log/info "sending email" (summarize email))
        (send! mailer email)
        (catch Throwable e
          (log/error (ex-info "Error sending email" {:mailer mailer :email email} e))))
      (log/info "simulating email" (summarize email)))))

(defmethod Section ::settings-link
  [{:keys [i18n] {:email/keys [settings-uri]} :config} _]
  [:a {:href settings-uri :title (:email/email-settings i18n)}
   ;; TODO i18n
   (:email i18n "Email")])

(defmethod Section ::heading [{:keys [i18n]} _]
  [:h3 (:email/email i18n "Email")])

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

(defmethod Section ::emails [{:keys [config i18n user]} _]
  (let [{:email/keys [allow-delete-primary?]} config
        emails (sort compare-emails (:user/emails user))]
    [:<>
     (if (seq emails)
       [:.flex.col {:role :list}
        (map (fn [{:keys [email/address
                          email/confirmed-at
                          email/primary?
                          thing/created-at
                          db/id]}]
               [:form.flex.row {:method :post :role :listitem}
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

(defmethod Section ::add-email [{:keys [config i18n user]} _]
  (let [emails (:user/emails user)
        any-pending? (seq (filter (complement :email/confirmed-at) emails))
        allow-multiple-pending? (:email/allow-multiple-pending? config)]
    (if (or (not any-pending?) allow-multiple-pending?)
      [:<>
       [:h3 {:for :add-email}
        (:email/add-email i18n)]
       [:form.flex.row {:method :post}
        [:input {:id :add-email :type :email :name :email :placeholder "me@example.email"}]
        [:button {:type :submit :name :action :value :add}
         (:email/add i18n)]]]
      [:p.instruct (:email/to-add-email-confirm-pending i18n)])))

(defc EmailPage
  [{:as data :keys [config dir hook i18n user]}]
  {:query '[:db/id :user/username {:user/emails [* :thing/created-at]}]}
  ;; TODO UI lib
  [:html {:lang {:field/lang data} :dir dir}
   [:head
    [:meta {:content-type :utf-8}]
    (->> (auth/LoginStyle data) (hook ::html.stylesheet) (hook ::html.email.stylesheet))
    (hook ::html.email.title [:title (:email/email i18n "Email")])]
   [:body
    [:nav.flex.row
     (map (partial Section data) (:account/html.account.header config))]
    [:main.flex.col
     (map (partial Section data) (:email/html.email.sections config))]]])

(defc ConfirmPage
  [{:keys [pending-email i18n ring/params ring/uri]}]
  {:query '[:db/id :email/address]
   :key :pending-email}
  (let [{:email/keys [address code]} pending-email]
    ;; TODO styles
    [:form {:method :post :action uri}
     [:input {:type :hidden :name :email :value address}]
     [:input {:type :hidden :name :code :value code}]
     [:button {:type :submit}
      (:email/confirm-email i18n)]]))

(defn- ensure-own-email-id [user id]
  (let [own-id? (contains? (set (map :db/id (:user/emails user))) id)]
    (when-not own-id?
      (doto (ex-info "Prohibited :db/id" {:params id}) (log/error) (throw)))))

(defmethod bread/effect [::update :make-primary]
  [{:keys [conn params]} {:keys [user]}]
  (let [emails (:user/emails user)
        id (Integer. (:id params))
        _ (ensure-own-email-id user id)
        current-id (->> emails (filter :email/primary?) first :db/id)]
    (try
      (db/transact conn [{:db/id current-id :email/primary? false}
                         {:db/id id :email/primary? true}])
      {:flash {:success-key :email/updated-primary}}
      (catch clojure.lang.ExceptionInfo e
        (log/error e)
        {:flash {:error-key :email/unexpected-error}}))))

(defmethod bread/effect ::send-confirmation! send-confirmation!
  [{:as effect :keys [from to code]}
   {:as data :keys [config i18n ring/scheme ring/server-name ring/server-port]}]
  (let [from (or from (:email/smtp-from-email config))
        link-uri (format "%s://%s%s%s?code=%s&email=%s"
                         (name scheme) server-name (when server-port (str ":" server-port))
                         "/_/confirm-email" (URLEncoder/encode code) (URLEncoder/encode to))
        subject (format (:email/confirmation-email-subject i18n) server-name)
        body (format (:email/confirmation-email-body i18n) link-uri)]
    (log/info "generated email confirmation link" link-uri)
    {:effects
     [{:effect/name ::send!
       :from from
       :to to
       :subject subject
       :body body}]}))

(defmethod bread/effect [::update :resend-confirmation] resend-confirmation
  [{:keys [conn params]} {:keys [config user]}]
  (let [emails (:user/emails user)
        ;; Check that the email belongs to the user and that it's still
        ;; actually pending confirmation.
        email (->> emails
                   (filter (fn [{:email/keys [address confirmed-at]}]
                             (and (= (:email params) address)
                                  (not confirmed-at))))
                   first)]
    (when email
      {:effects
       [{:effect/name ::send-confirmation!
         :effect/description "Prepare to resend confirmation email."
         ;; TODO :send-effect-key to override ::send!
         :from (:email/smtp-from-email config)
         :to (:email/address email)
         :code (:email/code email)}]
       :flash {:success-key :email/confirmation-resent}})))

(defmethod bread/effect [::update :delete]
  [{:keys [conn params]} {:keys [user]}]
  (let [emails (:user/emails user)
        id (Integer. (:id params))]
    (ensure-own-email-id user id)
    (try
      (db/transact conn [[:db/retractEntity id]])
      {:flash {:success-key :email/deleted}}
      (catch clojure.lang.ExceptionInfo e
        (log/error e)
        {:flash {:error-key :email/unexpected-error}}))))

(defmethod bread/effect [::update :add] add-email
  [{:keys [conn params]} {:keys [config existing-email user]}]
  (if (seq existing-email)
    {:flash {:error-key :email/email-in-use}}
    (let [email (:email params)
          user-id (:db/id user)
          code (random/url-part 32)
          now (t/now)]
      (try
        (log/info "adding email" {:email email :user-id user-id})
        (db/transact conn [{:db/id (:db/id user)
                            :user/emails [{:email/address email
                                           :email/code code
                                           :thing/updated-at now
                                           :thing/created-at now}]}])
        {:effects
         [{:effect/name ::send-confirmation!
           :effect/description "Prepare confirmation email."
           ;; TODO :send-effect-key to override ::send!
           :from (:email/smtp-from-email config)
           :to (:email params)
           :code code}]
         :flash {:success-key :email/email-added-please-confirm}}
        (catch clojure.lang.ExceptionInfo e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))))

(defn validate-action [action {params :params {:keys [user]} :session}]
  (case action
    :add
    (when-not (.contains (:email params) "@")
      :email/invalid-email)
    nil))

(defmethod bread/dispatch ::settings=>
  [{:as req
    :keys [::bread/dispatcher params request-method]
    {:keys [user]} :session}]
  (let [post? (= :post request-method)
        action (when (seq (:action params)) (keyword (:action params)))
        error-key (when post? (validate-action action req))
        pull (:dispatcher/pull dispatcher)
        query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}
        expansion {:expansion/key :user
                   :expansion/name ::db/query
                   :expansion/description "Query for user emails."
                   :expansion/db (db/database req)
                   :expansion/args [query (:db/id user)]}]
    (cond
      (and post? error-key)
      {:expansions
       [{:expansion/name ::bread/value
         :expansion/key :error-key
         :expansion/value error-key}]}

      post?
      {:expansions
       [expansion
        (when (= :add action)
          {:expansion/key :existing-email
           :expansion/name ::db/query
           :expansion/description "Query for conflicting emails."
           :expansion/db (db/database req)
           :expansion/args
           ['{:find [?e]
              :in [$ ?email]
              :where [[?e :email/address ?email]]}
            (:email params)]})]
       :hooks
       {::bread/render
        [{:action/name ::ring/effect-redirect
          :effect/key action
          :to (bread/config req :email/settings-uri)
          :action/description
          "Redirect to email settings page after an update action."}]}
       :effects
       [(when action
          {:effect/name [::update action]
           :effect/key action
           :effect/description "Process email update action."
           :conn (db/connection req)
           :params params})]}

      ;; Show settings page.
      :default
      {:expansions [expansion]})))

(defmethod bread/effect ::confirm! confirm!
  [{:keys [conn]} {:keys [pending-email user]}]
  (when pending-email
    (log/info "confirming email" {:email (:email/address pending-email)
                                  :user-id (:db/id user)})
    (let [now (t/now)
          first? (= 1 (count (:user/emails user)))
          txs [{:db/id (:db/id pending-email)
                :email/primary? first?
                :email/confirmed-at now
                :thing/updated-at now}]]
      (try
        (db/transact conn txs)
        {:flash {:success-key :email/email-confirmed}}
        (catch Throwable e
          (log/error e)
          {:flash {:error-key :email/unexpected-error}})))))

(defmethod bread/expand ::validate-recency
  [{:keys [max-pending-minutes]} {:keys [pending-email]}]
  (let [min-updated (minutes-ago (t/now) max-pending-minutes)
        valid? (when pending-email
                 (.after (:thing/updated-at pending-email) min-updated))]
    (when valid? pending-email)))

(defmethod bread/dispatch ::confirm=>
  [{:as req :keys [request-method] {:keys [code email]} :params}]
  (let [post? (= :post request-method)
        expansions
        [{:expansion/name ::db/query
          :expansion/key :pending-email
          :expansion/description "Query for matching email and code."
          :expansion/db (db/database req)
          :expansion/args ['{:find [(pull ?e [:db/id
                                              :email/code
                                              :email/address
                                              :thing/updated-at]) .]
                             :in [$ ?code ?email]
                             :where [[?e :email/code ?code]
                                     [?e :email/address ?email]
                                     ;; Only query for unconfirmed emails.
                                     (not-join [?e] [?e :email/confirmed-at])]}
                           code email]}
         {:expansion/name ::validate-recency
          :expansion/key :pending-email
          :expansion/description "Validate the confirmation link's age."
          :max-pending-minutes (bread/config req :email/max-pending-minutes)}]]
    {:expansions expansions
     :effects
     [(when post?
        {:effect/name ::confirm!
         :effect/key :confirm
         :effect/description "Confirm account email."
         :conn (db/connection req)})]
     :hooks
     {::bread/render
      [(when post?
         {:action/name ::ring/effect-redirect
          :effect/key :confirm
          :effect/description "Redirect after confirming"
          :to (bread/config req :email/settings-uri)})]}}))

(defn plugin [{:keys [dry-run?
                      smtp-from-email
                      smtp-host
                      smtp-port
                      smtp-username
                      smtp-password
                      smtp-tls?
                      settings-uri
                      confirm-uri
                      max-pending-minutes
                      allow-delete-primary?
                      allow-multiple-pending?
                      html-email-sections
                      mailer]
               :or {smtp-port 587
                    settings-uri "/~/email"
                    confirm-uri "/_/confirm-email"
                    max-pending-minutes (* 72 60)
                    html-email-sections [::heading
                                         :flash
                                         ::emails
                                         ::add-email]}}]
  (let [config {:email/dry-run? dry-run?
                :email/allow-delete-primary? allow-delete-primary?
                :email/allow-multiple-pending? allow-multiple-pending?
                :email/smtp-from-email smtp-from-email
                :email/smtp-host smtp-host
                :email/smtp-port (Integer. smtp-port)
                :email/smtp-username smtp-username
                :email/smtp-password smtp-password
                :email/smtp-tls? (boolean smtp-tls?)
                :email/settings-uri settings-uri
                :email/confirm-uri confirm-uri
                :email/max-pending-minutes max-pending-minutes
                :email/html.email.sections html-email-sections}
        mailer (or mailer (PostalMailer. (config->postal config)))]
    {:hooks
     {::i18n/global-strings
      [{:action/name ::i18n/merge-global-strings
        :action/description "Merge strings for email page into global i18n strings."
        :strings (edn/read-string (slurp (io/resource "email.i18n.edn")))}]}
     :config (assoc config :email/mailer mailer)}))
