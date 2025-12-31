(ns systems.bread.alpha.plugin.email
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [postal.core :as postal]
    [taoensso.timbre :as log]

    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.thing :as thing]))

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

(defmethod bread/effect ::bread/email! send-email!
  [effect {{:as config :email/keys [dry-run? smtp-from-email]} :config}]
  (let [send? (and (not dry-run?) (not (:dry-run? effect)))
        postal-config (config->postal config)
        email {:from (or (:from effect) smtp-from-email)
               :to (:to effect)
               :subject (:subject effect)
               :body (:body effect)}]
    (log/info (if send? "sending email" "simlulating email") (summarize email))
    (when send?
      (postal/send-message postal-config email))))

(defmethod Section ::settings-link
  [{:keys [i18n] {:email/keys [settings-uri]} :config} _]
  [:a {:href settings-uri :title (:email/email-settings i18n)}
   ;; TODO i18n
   (:email i18n "Email")])

(defmethod Section ::heading [{:keys [i18n]} _]
  [:h3 (:email/email i18n "Email")])

(defmethod Section ::emails [{:keys [config i18n user]} _]
  (let [{:email/keys [allow-delete-primary?]} config
        ;; TODO sort
        emails (:user/emails user)]
    [:<>
     (if (seq emails)
       [:.flex.col
        (map (fn [{:keys [email/address
                          email/confirmed-at
                          email/primary?
                          thing/created-at
                          db/id]}]
               [:form.flex.col.tight {:method :post}
                [:input {:type :hidden :name :email :value address}]
                [:input {:type :hidden :name :id :value id}]
                [:.field.flex.row
                 [:label address]]
                (cond
                  primary?
                  [:.flex.row
                   [:span
                    (:email/confirmed i18n)
                    ;; TODO date locale/formatting
                    " " confirmed-at]
                   [:span (:email/primary i18n)]
                   (when allow-delete-primary?
                     [:button {:type :submit :name :action :value :delete}
                      (:email/delete i18n)])]

                  confirmed-at
                  [:.flex.row
                   [:span (:email/confirmed i18n)
                    ;; TODO date locale/formatting
                    " " confirmed-at]
                   [:button {:type :submit :name :action :value :make-primary}
                    (:email/make-primary i18n)]
                   [:button {:type :submit :name :action :value :delete}
                    (:email/delete i18n)]]

                  :pending
                  [:.flex.row
                   [:span (:email/confirmation-pending i18n)]
                   [:button {:type :submit :name :action :value :resend-confirmation}
                    (:email/resend-confirmation i18n)]]
                  )])
             emails)]
       [:h4 (:email/no-emails i18n)])]))

(defmethod Section ::add-email [{:keys [config i18n user]} _]
  (let [emails (:user/emails user)
        any-pending? (seq (filter (complement :email/confirmed-at) emails))
        allow-multiple-pending? (:email/allow-multiple-pending? config)]
    (if (or (not any-pending?) allow-multiple-pending?)
      [:<>
       [:h3 {:for :add-email}
        (:email/add-email i18n)]
       [:form.flex.row {:method :post}
        [:input {:type :hidden :name :action :value :add-email}]
        [:input {:id :add-email :type :email :name :email}]
        [:span.spacer]
        [:button {:type :submit}
         (:email/add i18n)]]]
      [:p.instruct (:email/to-add-email-confirm-pending i18n "To add another...")])))

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

(defmethod bread/dispatch ::settings=>
  [{:as req
    :keys [::bread/dispatcher params request-method]
    {:keys [user]} :session}]
  (let [post? (= :post request-method)
        action (when (seq (:action params)) (keyword (:action params)))
        pull (:dispatcher/pull dispatcher)
        query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}
        expansion {:expansion/key :user
                   :expansion/name ::db/query
                   :expansion/description "Query for user emails."
                   :expansion/db (db/database req)
                   :expansion/args [query (:db/id user)]}]
    (if post?
      {:expansions [expansion]
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
      {:expansions [expansion]})))

#_
(defmethod bread/dispatch ::confirm=>
  [{:as req {:keys [code email]} :params}]
  (let [code (thing/->uuid code)
        txs [{:email/code code}]] (db/txs->effect req txs)))

(defn plugin [{:keys [smtp-from-email
                      smtp-host
                      smtp-port
                      smtp-username
                      smtp-password
                      smtp-tls?
                      settings-uri
                      allow-delete-primary?
                      allow-multiple-pending?
                      html-email-sections]
               :or {smtp-port 587
                    settings-uri "/~/email"
                    html-email-sections [::heading
                                         :flash
                                         ::emails
                                         ::add-email]}}]
  {:hooks
   {::i18n/global-strings
    [{:action/name ::i18n/merge-global-strings
      :action/description "Merge strings for email page into global i18n strings."
      :strings (edn/read-string (slurp (io/resource "email.i18n.edn")))}]}
   :config
   {:email/allow-delete-primary? allow-delete-primary?
    :email/allow-multiple-pending? allow-multiple-pending?
    :email/smtp-from-email smtp-from-email
    :email/smtp-host smtp-host
    :email/smtp-port (Integer. smtp-port)
    :email/smtp-username smtp-username
    :email/smtp-password smtp-password
    :email/smtp-tls? (boolean smtp-tls?)
    :email/settings-uri settings-uri
    :email/html.email.sections html-email-sections}})
