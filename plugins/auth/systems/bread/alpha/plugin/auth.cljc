(ns systems.bread.alpha.plugin.auth
  (:require
    [buddy.hashers :as hashers]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [com.rpl.specter :as s]
    [crypto.random :as random]
    [one-time.core :as ot]
    [one-time.uri :as oturi]
    [one-time.qrgen :as qr]
    [ring.middleware.session.store :as ss :refer [SessionStore]]

    [systems.bread.alpha.component :as component :refer [defc]]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.ring :as ring])
  (:import
    [java.lang IllegalArgumentException]
    [java.net URLEncoder]
    [java.text SimpleDateFormat]
    [java.util Base64 Date]))

(deftype DatalogSessionStore [conn]
  SessionStore
  (ss/delete-session [_ sk]
    (db/transact conn [[:db/retract [:session/id sk] :session/id]
                       [:db/retract [:session/id sk] :session/data]])
    sk)
  (ss/read-session [_ sk]
    (when sk
      (let [{id :db/id data :session/data}
            (db/q @conn
                  '{:find [(pull ?e [:db/id :session/data]) .]
                    :in [$ ?sk]
                    :where [[?e :session/id ?sk]]}
                  sk)]
        (when id (-> data edn/read-string (assoc :db/id id))))))
  (ss/write-session [this sk {:keys [user] :as data}]
    (let [exists? (and sk (ss/read-session this sk))
          sk (if exists? sk (random/base64 512))
          date-key (if exists? :thing/created-at :thing/updated-at)
          session {:session/id sk
                   :session/data (pr-str data)
                   date-key (Date.)}
          tx {:db/id (:db/id user)
              :user/sessions [session]}]
      (db/transact conn [tx])
      sk)))

(defn session-store [conn]
  (DatalogSessionStore. conn))

(comment
  (random/base64 512)
  (URLEncoder/encode "/")
  (URLEncoder/encode "/destination")
  (URLEncoder/encode "/destination?param=1")
  (URLEncoder/encode "/destination?param=1&b=2")
  (def $secret (ot/generate-secret-key))
  (map (partial ot/get-totp-token $secret) [{:time-step 30} {:time-step 19} {:time-step 60}])
  (ot/is-valid-totp-token? 812211 $secret)
  (ot/is-valid-totp-token? (ot/get-totp-token $secret) $secret))

(defc LoginStyle [{:keys [hook]}]
  {}
  (hook
    ::html.style
    [:<>
     [:style
      "
      :root {
        --body-max-width: 70ch;
        --border-width: 2px;
        --color-text-body: hsl(300, 80%, 95%);
        --color-text-emphasis: hsl(300.7, 95.3%, 83.1%);
        --color-stroke-emphasis: hsl(258.6, 100%, 74.7%);
        --color-stroke-secondary: hsl(300, 75%, 12.5%);
        --color-stroke-tertiary: hsl(300, 17.8%, 17.6%);
        --color-text-error: hsl(326, 68.3%, 62.9%);
        --color-stroke-error: hsl(314.9, 52.7%, 46.5%);
        --color-text-secondary: hsl(300, 21.9%, 70.4%);
        --color-bg: hsl(264, 41.7%, 4.7%);
      }
      @media (prefers-color-scheme: light) {
        :root {
          --color-text-body: hsl(263.9, 79%, 24.3%);
          --color-text-emphasis: hsl(280.9, 52.6%, 42.2%);
          --color-stroke-emphasis: hsl(290, 33.3%, 49.4%);
          --color-stroke-secondary: hsl(300, 75%, 12.5%);
          --color-text-error: hsl(309.4, 73.8%, 37.5%);
          --color-stroke-error: hsl(300.4, 69.2%, 40.8%);
          --color-text-secondary: hsl(280.3, 42.7%, 36.3%);
          --color-stroke-tertiary: hsl(300, 17.8%, 17.6%);
          --color-bg: hsl(300, 12.8%, 92.4%);
        }
      }
      body {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, Cantarell, Ubuntu, roboto, noto, helvetica, arial, sans-serif;
        line-height: 1.5;

        color: var(--color-text-body);
        background: var(--color-bg);
      }
      header {
        display: flex;
        flex-flow: row nowrap;
        justify-content: space-between;
        align-items: center;

        padding: 1em;
        border-bottom: 2px dashed var(--color-stroke-emphasis);
      }
      main {
        width: var(--body-max-width);
        max-width: 96%;
        margin: 5em auto;
      }
      h1, h2, h3, h4, h5, h6 {
        color: var(--color-text-emphasis);
      }
      h1, h2, p {
        margin: 0;
      }
      form {
        margin: 0;
      }
      main, .flex-col {
        display: flex;
        flex-flow: column nowrap;
        gap: 1.5em;
      }
      .field {
        display: flex;
        flex-flow: row nowrap;
        gap: 3ch;
        justify-content: space-between;
        align-items: center;
      }
      .field label {
        flex: 1;
      }
      .field :is(input, select) {
        flex: 2;
      }
      .instruct {
        color: var(--color-text-secondary);
      }
      .emphasis {
        font-weight: 700;
        color: var(--color-text-emphasis);
        border: var(--border-width) dashed var(--color-stroke-emphasis);
        padding: 12px;
      }
      .error {
        font-weight: 700;
        color: var(--color-text-error);
        border: var(--border-width) dashed var(--color-stroke-error);
        padding: 12px;
      }
      label {
        font-weight: 700;
      }
      select {
        cursor: pointer;
      }
      input, select {
        padding: 12px;
        border: var(--border-width) solid var(--color-text-body);
      }
      button, input, select {
        color: var(--color-text-body);
        background: var(--color-bg);
        border: var(--border-width) solid var(--color-text-body);
        border-radius: 0;
      }
      button {
        padding: 10px 12px;
        cursor: pointer;
        font-weight: 700;
        font-size: 1rem;
      }
      :is(button, input, select):focus {
        outline: var(--border-width) solid var(--color-stroke-emphasis);
        border-color: transparent;
      }
      button:hover {
        border-color: transparent;
        outline: var(--border-width) dashed var(--color-stroke-emphasis);
        color: var(--color-text-emphasis);
      }
      .center {
        display: flex;
        justify-content: center;
      }
      hr {
        width: 100%;
        border: 2px solid var(--color-stroke-secondary);
      }
      .totp-key {
        font-family: monospace;
        letter-spacing: 5;
      }

      .user-session {
        display: flex;
        flex-flow: row wrap;
        justify-content: space-between;
        align-items: start;

        margin: 0;
        padding: 1em;
        border: 2px dashed var(--color-stroke-tertiary);
      }
      "]]))

(defn qr-datauri [data]
  (when-let [stream (try (qr/totp-stream data)
                         (catch Throwable _ nil))]
    (def $stream stream)
    (->> stream
         (.toByteArray)
         (.encodeToString (Base64/getEncoder))
         (str "data:image/png;base64,"))))

(defc LoginPage
  [{:as data
    :keys [hook i18n session rtl? dir totp]
    :auth/keys [result]}]
  {}
  (let [{:keys [totp-key issuer]} totp
        user (or (:user session) (:auth/user session))
        step (:auth/step session)
        error? (false? (:valid result))]
    [:html {:lang (:field/lang data)
            :dir dir}
     [:head
      [:meta {:content-type :utf-8}]
      (hook ::html.title [:title (str (:auth/login i18n) " | Bread")])
      (LoginStyle data)]
     [:body
      (cond
        (:user/locked-at user)
        [:main
         [:form.flex-col
          (hook ::html.locked-heading [:h2 (:auth/account-locked i18n)])
          (hook ::html.locked-explanation [:p (:auth/too-many-attempts i18n)])]]

        (= :setup-two-factor step)
        (let [data-uri (qr-datauri {:label issuer
                                    :user (:user/username user)
                                    :secret totp-key
                                    :image-type :PNG})]
          [:main
           [:form.flex-col {:name :setup-mfa :method :post}
            (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
            (hook ::html.scan-qr-instructions
                  [:p.instruct (:auth/please-scan-qr-code i18n)])
            [:div.center [:img {:src data-uri :width 125 :alt (:auth/qr-code i18n)}]]
            [:p.instruct (:auth/or-enter-key-manually i18n)]
            [:div.center [:h2.totp-key totp-key]]
            [:input {:type :hidden :name :totp-key :value totp-key}]
            [:hr]
            [:p.instruct (:auth/enter-totp-next i18n)]
            [:div.field.two-factor
             [:input {:id :two-factor-code :type :number :name :two-factor-code}]
             [:button {:type :submit :name :submit :value "verify"}
              (:auth/verify i18n)]]
            (when error?
              (hook ::html.invalid-code
                    [:div.error
                     [:p (:auth/invalid-totp i18n)]]))]])

        (= :two-factor step)
        [:main
         [:form.flex-col {:name :bread-login :method :post}
          (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
          (hook ::html.enter-2fa-code
                [:p.instruct (:auth/enter-totp i18n)])
          [:div.field.two-factor
           [:input {:id :two-factor-code :type :number :name :two-factor-code}]
           [:button {:type :submit :name :submit :value "verify"}
            (:auth/verify i18n)]]
          (when error?
            (hook ::html.invalid-code
                  [:div.error
                   [:p (:auth/invalid-totp i18n)]]))]]

        :default
        [:main
         [:form.flex-col {:name :bread-login :method :post}
          (hook ::html.login-heading [:h1 (:auth/login-to-bread i18n)])
          (hook ::html.enter-username
                [:p.instruct (:auth/enter-username-password i18n)])
          [:div.field
           [:label {:for :user} (:auth/username i18n)]
           [:input {:id :user :type :text :name :username}]]
          [:div.field
           [:label {:for :password} (:auth/password i18n)]
           [:input {:id :password :type :password :name :password}]]
          (when error?
            (hook ::html.invalid-login
                  [:div.error [:p (:auth/invalid-username-password i18n)]]))
          [:div.field
           [:span.spacer]
           [:button {:type :submit} (:auth/login i18n)]]]])]]))

(defn- ua->browser [ua]
  (let [normalized (string/lower-case ua)]
    (cond
      (re-find #"firefox" normalized) "Firefox"
      (re-find #"chrome" normalized) "Google Chrome"
      (re-find #"safari" normalized) "Safari"
      :default "Unknown browser")))

(defn- ua->os [ua]
  (let [normalized (string/lower-case ua)]
    (cond
      (re-find #"linux" normalized) "Linux"
      (re-find #"macintosh" normalized) "Mac"
      (re-find #"windows" normalized) "Windows"
      :default "Unknown OS")))

(defn- i18n-format [i18n k]
  (if (sequential? k) ;; TODO tongue
    (let [[k & args] k]
      (apply format (get i18n k) args))
    (get i18n k)))

(defc AccountPage
  [{:as data
    :keys [config flash hook i18n lang-names rtl? dir session supported-langs]
    {:as user :user/keys [sessions roles]} :user}]
  {:query '[:db/id
            :thing/created-at
            :user/username
            {:user/email [*]}
            :user/name
            :user/lang
            :user/preferences
            {:user/roles [:role/key {:role/abilities [:ability/key]}]}
            {:invitation/_redeemer [{:invitation/invited-by [:db/id :user/username]}]}
            {:user/sessions [:db/id :session/data :thing/created-at :thing/updated-at]}]}
  (let [date-fmt (SimpleDateFormat. (:auth/date-format-default i18n "d LLL"))]
    [:html {:lang (:field/lang data) :dir dir}
     [:head
      [:meta {:content-type :utf-8}]
      (hook ::html.title [:title (str (:user/username user) " | Bread")])
      (LoginStyle data)
      (hook ::html.account-page-head [:<>])]
     [:body
      [:header
       [:span (:user/username user)]
       [:form.logout-form {:method :post :action (:auth/login-uri config)}
        [:button {:type :submit :name :submit :value "logout"}
         (:auth/logout i18n)]]]
      [:main
       [:form.flex-col {:method :post}
        (hook ::html.account-details-heading [:h3 (:auth/account-details i18n)])
        (when-let [success-key (:success-key flash)]
          (hook ::html.account-flash [:.emphasis [:p (i18n-format i18n success-key)]]))
        (when-let [error-key (:error-key flash)]
          (hook ::html.account-error [:.error [:p (i18n-format i18n error-key)]]))
        [:.field
         [:label {:for :name} (:auth/name i18n)]
         [:input {:id :name :name :name :value (:user/name user)}]]
        (when (> (count supported-langs) 1)
          [:.field
           [:label {:for :lang} (:auth/preferred-language i18n)]
           [:select {:id :lang :name :lang}
            (map (fn [k]
                   [:option {:selected (= k (:user/lang user)) :value k}
                    (get lang-names k (name k))])
                 (sort-by name (seq supported-langs)))]])
        [:p.instruct (:auth/leave-passwords-blank i18n)]
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
                  :maxlength (:auth/max-password-length config)}]]
        [:.field
         [:span.spacer]
         [:button {:type :submit :name :action :value "update"}
          (:auth/save i18n)]]]
       [:section.flex-col
        (hook ::html.account-sessions-heading [:h3 (:auth/your-sessions i18n)])
        [:.flex-col
         (map (fn [{:as user-session
                    {:keys [user-agent remote-addr]} :session/data
                    :thing/keys [created-at updated-at]}]
                (if (= (:db/id session) (:db/id user-session))
                  [:div.user-session.current
                   [:div
                    [:div (ua->browser user-agent)]
                    [:div (ua->os user-agent)]
                    [:div (.format date-fmt created-at)]
                    (when updated-at
                      [:div "Last active at " (.format date-fmt updated-at)])]
                   [:div [:span.instruct "This session"]]]
                  [:form.user-session {:method :post}
                   [:input {:type :hidden :name :dbid :value (:db/id user-session)}]
                   [:div
                    [:div (ua->browser user-agent)]
                    [:div (ua->os user-agent)]
                    [:div (.format date-fmt created-at)]
                    (when updated-at
                      [:div "Last active at " (.format date-fmt updated-at)])]
                   [:div
                    [:button {:type :submit :name :action :value "delete-session"}
                     (:auth/logout i18n)]]]))
              sessions)]]]]]))

(defmethod bread/action ::require-auth
  [{:keys [headers session query-string uri] :as req} _ _]
  (let [login-uri (bread/config req :auth/login-uri)
        protected? (bread/hook req ::protected-route? (not= login-uri uri))
        anonymous? (empty? (:user session))
        next-param (name (bread/config req :auth/next-param))
        next-uri (URLEncoder/encode (if (seq query-string)
                                      (str uri "?" query-string)
                                      uri))
        redirect-uri (str login-uri "?" next-param "=" next-uri)]
    (if (and protected? anonymous?)
      (assoc req
             :status 302
             :headers (assoc headers "Location" redirect-uri))
      req)))

(defmethod bread/action ::set-session
  [{::bread/keys [data] :keys [params query-string session] :as res}
   {:keys [max-failed-login-count require-mfa?]} _]
  (let [{{:keys [valid user]} :auth/result} data
        current-step (:auth/step session)
        login-step? (nil? current-step)
        setting-up-two-factor? (= :setup-two-factor (:auth/step session))
        two-factor-step? (= :two-factor current-step)
        two-factor-enabled? (or require-mfa? (:user/totp-key user))
        next-step (if (and (not= :two-factor current-step) two-factor-enabled?)
                    :two-factor
                    :logged-in)
        two-factor-next? (and valid (= :two-factor next-step))
        logged-in? (and valid (or setting-up-two-factor?
                                  (and two-factor-step? two-factor-enabled?)
                                  (and login-step? (not two-factor-enabled?))))
        session (cond
                  (and valid setting-up-two-factor?)
                  {:user user :auth/step :logged-in}
                  (and require-mfa? (not (:user/totp-key user)))
                  (assoc session :auth/user user :auth/step :setup-two-factor)
                  two-factor-next?
                  (assoc session :auth/user user :auth/step next-step)
                  logged-in? (-> session
                                 (assoc :user user :auth/step next-step)
                                 (dissoc :auth/user))
                  user (assoc session :auth/user user))
        next-param (bread/config res :auth/next-param)
        next-uri (get params next-param)
        login-uri (bread/config res :auth/login-uri)
        account-uri (bread/config res :auth/account-uri)
        ;; Don't redirect to destination prematurely!
        redirect-to (cond
                      (and next-uri logged-in?) next-uri
                      next-uri (str login-uri "?"
                                    (name next-param) "="
                                    (URLEncoder/encode next-uri))
                      logged-in? account-uri
                      :else login-uri)]
    (cond-> (-> res
                (assoc :session session)
                (assoc-in [::bread/data :session] session))
      valid (assoc :status 302)
      valid (assoc-in [:headers "Location"] redirect-to)
      (not valid) (assoc :status 401))))

(comment
  (def $now #inst "2025-01-01T00:00:00")
  (def $locked-at #inst "2025-01-01T00:30:00")
  (/ (inst-ms $now) 1000.0)
  (/ (inst-ms $locked-at) 1000.0)
  (account-locked? $now $locked-at 3600))

(defn- account-locked? [now locked-at seconds]
  (let [now-seconds (/ (inst-ms now) 1000.0)
        locked-at-seconds (/ (inst-ms locked-at) 1000.0)
        unlock-at-seconds (+ locked-at-seconds seconds)]
    (> unlock-at-seconds now-seconds)))

(defmethod bread/expand ::authenticate
  [{:keys [plaintext-password lock-seconds]} {user :auth/result}]
  (let [encrypted (or (:user/password user) "")
        user (when user (dissoc user :user/password))]
    (cond
      (not user) {:valid false :user nil}

      ;; Don't bother authenticating if the account is locked.
      (and (:user/locked-at user)
           (account-locked? (t/now) (:user/locked-at user) lock-seconds))
      {:valid false :locked? true :user user}

      :default
      (let [result (try
                     (hashers/verify plaintext-password encrypted)
                     (catch clojure.lang.ExceptionInfo e
                       {:valid false}))]
        (assoc result :user user)))))

(defmethod bread/expand ::authenticate-two-factor
  [{:keys [generous? lock-seconds two-factor-code]} {user :auth/result}]
  (let [;; Don't store password data in session
        user (dissoc user :user/password)
        locked? (and (:user/locked-at user)
                     (account-locked? (t/now) (:user/locked-at user) lock-seconds))]
    (if locked?
      {:valid false :locked? true :user user}
      (let [code (try
                   (Integer. two-factor-code)
                   (catch java.lang.NumberFormatException _ 0))
            valid (or (ot/is-valid-totp-token? code (:user/totp-key user))
                      (ot/is-valid-totp-token? code (:user/totp-key user)
                                               {:time-step-offset -1}))]
        {:valid valid :user user}))))

(defmethod bread/action ::logout [res _ _]
  (-> res
      (assoc :session nil :status 302)
      (assoc-in [::bread/data :session] nil)
      (assoc-in [:headers "Location"] (bread/config res :auth/login-uri))))

(defmethod bread/action ::matches-protected-prefix?
  [{:keys [uri]} {:keys [protected-prefixes]} [protected?]]
  (and protected? (reduce (fn [_ prefix]
                            (when (string/starts-with? uri prefix)
                              (reduced true)))
                          false protected-prefixes)))

(defmethod bread/effect ::log-attempt
  [{:keys [conn max-failed-login-count lock-seconds]}
   {{:keys [user valid] :as result} :auth/result}]
  (let [;; Use either identifier
        transaction (if (:db/id user)
                      {:db/id (:db/id user)}
                      {:user/username (:user/username user)})]
    (cond
      (not user) nil

      ;; User still needs to verify MFA, so don't reset the count yet.
      (and valid (= :two-factor (:auth/step result)))
      nil

      ;; User successfully logged in; reset count.
      valid
      (db/transact conn [(assoc transaction :user/failed-login-count 0)])

      (and (:user/locked-at user)
           (account-locked? (t/now) (:user/locked-at user) lock-seconds))
      nil

      (>= (:user/failed-login-count user) max-failed-login-count)
      (db/transact conn [(assoc transaction
                                ;; Lock account, but reset attempts.
                                :user/locked-at (t/now)
                                :user/failed-login-count 0)])

      :default
      (let [incremented (inc (:user/failed-login-count user))]
        (db/transact conn [(assoc transaction
                                  :user/failed-login-count incremented)])))))

(defmethod bread/action ::=>account
  [{:as req :keys [session]} {:keys [flash]} _]
  (if (:user session)
    (assoc req
           :status 302
           :headers {"Location" (bread/config req :auth/account-uri)}
           :flash flash)
    req))

(defmethod bread/dispatch ::login=>
  [{:keys [params request-method session] :as req}]
  (let [{:auth/keys [step]} session
        require-mfa? (bread/config req :auth/require-mfa?)
        max-failed-login-count (bread/config req :auth/max-failed-login-count)
        lock-seconds (bread/config req :auth/lock-seconds)
        get? (= :get request-method)
        post? (= :post request-method)
        logout? (= "logout" (:submit params))
        setup-two-factor? (= :setup-two-factor step)
        two-factor? (= :two-factor step)
        redirect-to (get params (bread/config req :auth/next-param))
        username (if two-factor?
                   (:user/username (:auth/user session))
                   (:username params))
        user-keys (cond-> [:db/id
                           :user/username
                           :user/totp-key
                           :user/locked-at
                           :user/failed-login-count]
                    (not two-factor?) (concat [:user/password]))
        user-expansion
        {:expansion/name ::db/query
         :expansion/key :auth/result
         :expansion/description "Find a user with the given username"
         :expansion/db (db/database req)
         :expansion/args
         [{:find [(list 'pull '?e user-keys) '.]
           :in '[$ ?username]
           :where '[[?e :user/username ?username]]}
          username]}]
    (cond
      ;; Logout - destroy session
      (and post? logout?)
      {:hooks
       {::bread/response
        [{:action/name ::logout
          :action/description "Unset :session in Ring response."}]}}

      ;; MFA
      (and post? two-factor?)
      {:expansions
       [user-expansion
        {:expansion/name ::authenticate-two-factor
         :expansion/key :auth/result
         :two-factor-code (:two-factor-code params)
         :lock-seconds lock-seconds
         :generous? (bread/config req :auth/generous-totp-window?)}]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this login attempt, locking account after too many."
         ;; Get :user from data, since it may not be in session data yet.
         :max-failed-login-count max-failed-login-count
         :lock-seconds lock-seconds
         :conn (db/connection req)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response"
          :require-mfa? require-mfa?
          :max-failed-login-count max-failed-login-count}]}}

      (and get? setup-two-factor?)
      {:expansions
       [{:expansion/key :totp
         :expansion/name ::bread/value
         :expansion/value {:totp-key (ot/generate-secret-key)
                           :issuer (or (bread/config req :auth/mfa-issuer)
                                       (:server-name req))}
         :expansion/description "Generate a TOTP key for MFA setup"}]}

      (and post? setup-two-factor?)
      (let [totp-key (:totp-key params)
            code (try
                   (Integer. (:two-factor-code params))
                   (catch java.lang.NumberFormatException _ 0))
            valid? (ot/is-valid-totp-token? code totp-key)
            user (cond-> (:auth/user session)
                   valid? (assoc :user/totp-key totp-key))
            tx {:user/username (:user/username user)
                :user/totp-key totp-key
                :thing/updated-at (Date.)}
            session (if valid? session {:auth/user user :auth/step :two-factor})
            totp-expansion
            (when-not valid?
              {:expansion/key :totp
               :expansion/name ::bread/value
               :expansion/value {:totp-key (:totp-key params)
                                 :issuer (or (bread/config req :auth/mfa-issuer)
                                             (:server-name req))}})]
        {:expansions
         [totp-expansion
          {:expansion/key :auth/result
           :expansion/name ::bread/value
           :expansion/value {:valid valid? :user user}}
          {:expansion/key :session
           :expansion/name ::bread/value
           :expansion/value session
           :expansion/description "Place session in data"}]
         :effects
         [(when valid? {:effect/name ::db/transact
                        :txs [tx]
                        :conn (db/connection req)
                        :effect/description "Persist TOTP key"})]
         :hooks
         {::bread/expand
          [{:action/name ::set-session
            :action/description "Set :session in Ring response."
            :require-mfa? require-mfa?
            :max-failed-login-count max-failed-login-count}]}})

      ;; Login
      post?
      {:expansions
       [user-expansion
        {:expansion/name ::authenticate
         :expansion/key :auth/result
         :require-mfa? require-mfa?
         :lock-seconds lock-seconds
         :plaintext-password (:password params)}]
       :effects
       [{:effect/name ::log-attempt
         :effect/description
         "Record this login attempt, locking account after too many."
         ;; Get :user from data, since it may not be in session data yet.
         :max-failed-login-count max-failed-login-count
         :lock-seconds lock-seconds
         :conn (db/connection req)}]
       :hooks
       {::bread/expand
        [{:action/name ::set-session
          :action/description "Set :session in Ring response."
          :require-mfa? require-mfa?
          :max-failed-login-count max-failed-login-count}]}}

      :default
      {:hooks
       {::bread/expand
        [{:action/name ::=>account
          :action/description "Redirect to account page after login"}]}})))

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

(defmethod account-action :update [{:as req :keys [params session] ::bread/keys [config]}]
  (let [{:keys [password password-confirmation]} params
        update-password? (seq password)
        error-key (when update-password? (validate-password-fields config params))
        hash-algo (when update-password? (:auth/hash-algorithm config))]
    (when error-key (throw (ex-info "Invalid password" {:error-key error-key})))
    [(cond-> {:db/id (:db/id (:user session)) :user/name (:name params)}
       (:lang params) (assoc :user/lang (keyword (:lang params)))
       update-password? (assoc :user/password
                               (hashers/derive password {:alg hash-algo})))]))

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
            :to (bread/config req :auth/account-uri)
            :flash (when account-update? {:success-key :auth/account-updated})
            :action/description
            "Redirect to account page after taking an account action"}]}}
        {:hooks
         {::bread/expand
          [{:action/name ::ring/redirect
            :to (bread/config req :auth/account-uri)
            :flash (when account-update? {:error-key error-key})
            :action/description
            "Redirect to account page after an error"}]}}))
    ;; Rendering the account page.
    (let [id (:db/id (:user session))
          pull (:dispatcher/pull dispatcher)]
      {:expansions
       [{:expansion/key :config
         :expansion/name ::bread/value
         :expansion/description "App config"
         :expansion/value (::bread/config req)}
        {:expansion/key :user
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

(def
  ^{:doc "Schema for authentication."}
  schema
  (with-meta
    [{:db/id "migration.authentication"
      :migration/key :bread.migration/authentication
      :migration/description "User credentials and security mechanisms"}
     {:db/ident :user/username
      :attr/label "Username"
      :db/doc "Username they use to login"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :db/unique :db.unique/identity
      :attr/migration "migration.authentication"}
     {:db/ident :user/password
      :attr/label "Password"
      :db/doc "User account password hash"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :user/totp-key
      :attr/label "TOTP key"
      :db/doc "User's secret key for the Time-based One-Time Password algorithm"
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :user/locked-at
      :attr/label "Account Locked-at Time"
      :db/doc "When the user's account was locked for security purposes (if at all)"
      :db/valueType :db.type/instant
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :user/failed-login-count
      :attr/label "Failed Login Count"
      :db/doc "Number of consecutive unsuccessful attempts"
      :db/valueType :db.type/number
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}

     ;; Sessions
     {:db/ident :user/sessions
      :attr/label "User sessions"
      :db/doc "All of a user's sessions"
      :db/valueType :db.type/ref
      :db/cardinality :db.cardinality/many
      :attr/migration "migration.authentication"}
     {:db/ident :session/id
      :attr/label "Session ID"
      :db/doc "Session identifier."
      :db/valueType :db.type/string
      :db/unique :db.unique/identity
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}
     {:db/ident :session/data
      :attr/label "Session Data"
      :db/doc "Arbitrary session data."
      :db/valueType :db.type/string
      :db/cardinality :db.cardinality/one
      :attr/migration "migration.authentication"}]

    {:type :bread/migration
     :migration/dependencies #{:bread.migration/migrations
                               :bread.migration/users}}))

(defn plugin
  ([]
   (plugin {}))
  ([{:keys [hash-algorithm max-failed-login-count lock-seconds next-param
            account-uri login-uri protected-prefixes require-mfa? mfa-issuer
            min-password-length max-password-length generous-totp-window?]
     :or {min-password-length 12
          max-password-length 72
          hash-algorithm :bcrypt+blake2b-512
          max-failed-login-count 5
          lock-seconds 3600
          next-param :next
          login-uri "/login"
          account-uri "/account"
          generous-totp-window? true}}]
   {:hooks
    {::db/migrations
     [{:action/name ::db/add-schema-migration
       :action/description
       "Add schema for authentication to the sequence of migrations to be run."
       :schema-txs schema}]
     ;; NOTE: we hook into ::bread/expand to require auth because
     ;; if we do it before that, the :headers may get wiped out.
     ::bread/expand
     [{:action/name ::require-auth
       :action/description
       "Require login for privileged routes (all routes by default)."}]
     ::protected-route?
     [(when (seq protected-prefixes)
        {:action/name ::matches-protected-prefix?
         :action/descripion
         "A collection of route prefixes requiring an auth session."
         :protected-prefixes protected-prefixes})]
     ::i18n/global-strings
     [{:action/name ::i18n/merge-global-strings
       :action/description "Merge strings for auth into global i18n strings."
       :strings (edn/read-string (slurp (io/resource "auth.i18n.edn")))}]}
    :config
    {:auth/require-mfa? require-mfa?
     :auth/mfa-issuer mfa-issuer
     :auth/generous-totp-window? generous-totp-window?
     :auth/hash-algorithm hash-algorithm
     :auth/max-failed-login-count max-failed-login-count
     :auth/min-password-length min-password-length
     :auth/max-password-length max-password-length
     :auth/lock-seconds lock-seconds
     :auth/next-param next-param
     :auth/login-uri login-uri
     :auth/account-uri account-uri}}))
