(ns systems.bread.alpha.plugin.account
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [com.rpl.specter :as s]
    [clojure.java.io :as io]
    [clojure.string :as string]

    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.email :as email])
  (:import
    [java.text SimpleDateFormat]))

(defmethod bread/action ::account-uri? [{:as req :keys [uri]} _ [protected?]]
  (or protected? (= (bread/config req :account/account-uri) uri)))

;; TODO move to generic ui ns
(defn Option [labels selected-value value]
  [:option {:value value :selected (= selected-value value)}
   (get labels value)])

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

(defn- i18n-format [i18n k]
  (if (sequential? k) ;; TODO tongue
    (let [[k & args] k]
      (apply format (get i18n k) args))
    (get i18n k)))

(defmethod Section ::username [{:keys [user]} _]
  [:span.username (:user/username user)])

(defmethod Section ::account-link
  [{:keys [user i18n] {:account/keys [account-uri]} :config} _]
  [:a {:href account-uri :title (:account/account-details i18n)}
   (:user/username user)])

(defmethod Section ::heading [{:keys [i18n]} _]
  [:h3 (:account/account i18n)])

;; TODO move to generic UI ns...
(defmethod Section :flash [{:keys [session ring/flash i18n]} _]
  [:<>
   (when-let [success-key (:success-key flash)]
     [:.success [:p (i18n-format i18n success-key)]])
   (when-let [error-key (:error-key flash)]
     [:.error [:p (i18n-format i18n error-key)]])])

(defmethod Section ::name [{:keys [user i18n]} _]
  [:.field
   [:label {:for :name} (:account/name i18n)]
   [:input {:id :name :name :name :value (:user/name user)}]])

(defmethod Section ::pronouns [{:keys [user i18n]} _]
  [:.field
   [:label {:for :pronouns} (:account/pronouns i18n)]
   [:input {:id :pronouns
            :name :pronouns
            :value (:pronouns (:user/preferences user))
            :placeholder (:account/pronouns-example i18n)}]])

(defmethod Section ::lang [{:keys [i18n lang-names supported-langs user]} _]
  (when (> (count supported-langs) 1)
    [:.field
     [:label {:for :lang} (:account/preferred-language i18n)]
     [:select {:id :lang :name :lang}
      (map (fn [k]
             [:option {:selected (= k (:user/lang user)) :value k}
              (get lang-names k (name k))])
           (sort-by name (seq supported-langs)))]]))

(defmethod Section ::timezone [{:keys [config i18n user]} _]
  (let [options (:account/timezone-options config)
        ;; TODO proper localization...
        labels (map #(string/replace % "_" " ") options)
        tz (:timezone (:user/preferences user))]
    [:.field
     [:label {:for :timezone} (:account/timezone i18n)]
     [:select {:id :timezone :name :timezone}
      (map (partial Option (zipmap options labels) tz) options)]]))

(defmethod Section ::password [{:keys [i18n user config]} _]
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
             :maxlength (:auth/max-password-length config)}]]])

(defmethod Section :save [{:keys [i18n]} _]
  [:.field
   [:span.spacer]
   [:button {:type :submit :name :action :value "update"}
    (:account/save i18n)]])

(defmethod Section ::account-form [{:as data :keys [config]} _]
  [:form.flex.col {:method :post}
   (map (partial Section data) (:account/html.account.form config))])

(defmethod Section ::sessions [{:keys [i18n session user]} _]
  (let [date-fmt (SimpleDateFormat. (:account/date-format-default i18n "d LLL"))]
    [:section.flex.col
     [:h3 (:account/your-sessions i18n)]
     [:.flex.col
      (map (fn [{:as user-session
                 {:keys [user-agent remote-addr]} :session/data
                 :thing/keys [created-at updated-at]}]
             (if (= (:db/id session) (:db/id user-session))
               [:div.user-session.current
                [:div
                 (when user-agent
                   [:div (ua->browser user-agent) " | " (ua->os user-agent)])
                 (when remote-addr
                   [:div remote-addr])
                 [:div "Logged in at " (.format date-fmt created-at)]
                 (when updated-at
                   ;; TODO i18n
                   [:div "Last active at " (.format date-fmt updated-at)])]
                [:div [:span.instruct "This session"]]]
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

(defc AccountPage
  [{:as data :keys [config hook dir user]}]
  {:query '[:db/id
            :thing/created-at
            :user/username
            :user/name
            :user/lang
            :user/preferences
            {:user/roles [:role/key {:role/abilities [:ability/key]}]}
            {:invitation/_redeemer [{:invitation/invited-by [:db/id :user/username]}]}
            {:user/sessions [:db/id :session/data :thing/created-at :thing/updated-at]}]}
  [:html {:lang (:field/lang data) :dir dir}
   [:head
    [:meta {:content-type :utf-8}]
    (hook ::html.account.title [:title (:user/username user) " | " (:site/name config)])
    ;; TODO theme/Style
    (->> (auth/LoginStyle data) (hook ::html.stylesheet) (hook ::html.account.stylesheet))
    (->> [:<>] (hook ::html.head) (hook ::html.account.head))]
   [:body
    [:nav.flex.row
     (map (partial Section data) (:account/html.account.header config))]
    [:main.flex.col
     (map (partial Section data) (:account/html.account.sections config))]]])

(defmethod bread/expand ::user [_ {:keys [user]}]
  ;; TODO infer from query/schema...
  (when user
    (as-> user $
      (update $ :user/preferences edn/read-string)
      (s/transform [:user/sessions s/ALL :session/data] edn/read-string $))))

(defmulti account-action (fn [{:keys [params]}] (keyword (:action params))))

(defmethod account-action :delete-session [{:keys [params]}]
  (when-let [id (try (Integer. (:dbid params)) (catch Throwable _ nil))]
    [[:db/retract id :session/id]
     [:db/retract id :session/data]
     [:db/retract id :thing/created-at]
     [:db/retract id :thing/updated-at]]))

(defn validate-password-fields
  [{:auth/keys [min-password-length max-password-length]}
   {:keys [password password-confirmation]}]
  "Returns an error code as a keyword if the :password and/or :password-confirmation
  params are invalid."
  (cond
    (empty? password) :auth/password-required
    (not= password password-confirmation) :auth/passwords-must-match
    (< (count password) min-password-length)
    [:auth/password-must-be-at-least min-password-length]
    (> (count password) max-password-length)
    [:auth/password-must-be-at-most max-password-length]))

(defn- hook-preference [req [k v]]
  [k (bread/hook req [::preference k] v)])

(defmethod account-action :update [{:as req :keys [params session] ::bread/keys [config]}]
  (let [{:keys [password password-confirmation]} params
        update-password? (seq password)
        error-key (when update-password? (validate-password-fields config params))
        hash-algo (when update-password? (:auth/hash-algorithm config))
        preferences (dissoc params :action :name :lang :password :password-confirmation)]
    (when error-key (throw (ex-info "Invalid password" {:error-key error-key})))
    [(cond-> {:db/id (:db/id (:user session)) :user/name (:name params)}
       (:lang params) (assoc :user/lang (keyword (:lang params)))
       update-password? (assoc :user/password
                               (hashers/derive password {:alg hash-algo}))
       (seq preferences)  (assoc :user/preferences (->> preferences
                                                        (map (partial hook-preference req))
                                                        (into {})
                                                        pr-str)))]))

(defmethod bread/dispatch ::account=>
  [{:as req :keys [params request-method session] ::bread/keys [config dispatcher]}]
  (if (= :post request-method)
    ;; Account update.
    (let [action (keyword (:action params))
          account-update? (= :update action)
          [txs error-key] (try
                            [(account-action req) nil]
                            (catch clojure.lang.ExceptionInfo e
                              [nil (-> e ex-data :error-key)]))
          success-key (cond
                        account-update? :account-updated)]
      (if txs
        {:effects
         [(db/txs->effect req txs :effect/description "Update account details")]
         :hooks
         {::bread/expand
          [{:action/name ::ring/redirect
            :to (bread/config req :account/account-uri)
            :flash (when account-update? {:success-key :account/account-updated})
            :action/description
            "Redirect to account page after taking an account action"}]}}
        {:hooks
         {::bread/expand
          [{:action/name ::ring/redirect
            :to (bread/config req :account/account-uri)
            :flash (when account-update? {:error-key error-key})
            :action/description
            "Redirect to account page after an error"}]}}))
    ;; Rendering the account page.
    (let [id (:db/id (:user session))
          pull (:dispatcher/pull dispatcher)]
      {:expansions
       [{:expansion/key :user
         :expansion/name ::db/query
         :expansion/description "Query for all user account data"
         :expansion/db (db/database req)
         :expansion/args [{:find [(list 'pull '?e pull) '.]
                           :in '[$ ?e]}
                          id]}
        {:expansion/key :user
         :expansion/name ::user
         :expansion/description "Expand user data"}
        {;; TODO => i18n
         :expansion/key :supported-langs
         :expansion/name ::bread/value
         :expansion/value (i18n/supported-langs req)
         :expansion/description "Supported languages"}
        {;; TODO => i18n
         :expansion/key :lang-names
         :expansion/name ::bread/value
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
                                         :spacer
                                         auth/LogoutForm]
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
      :strings (edn/read-string (slurp (io/resource "account.i18n.edn")))}]}
   :config
   {:account/account-uri account-uri
    :account/timezone-options timezone-options
    :account/html.account.header html-account-header
    :account/html.account.sections html-account-sections
    :account/html.account.form html-account-form}})
