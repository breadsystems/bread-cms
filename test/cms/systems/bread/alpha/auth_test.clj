(ns systems.bread.alpha.auth-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are is testing]]
    [clojure.walk :as walk]
    [crypto.random :as random]
    [one-time.core :as ot]
    [ring.middleware.session.store :as ss]
    [taoensso.timbre :as timbre]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.test-helpers :refer [naive-router
                                              plugins->loaded
                                              use-db]])
  (:import
    [java.util Date]))

(timbre/merge-config! {:min-level :warn})

(def SECRET "keep it secret, keep it safe")

(def angela
  {:user/username "angela"
   :user/failed-login-count 0})

(def bobby
  {:user/username "bobby"
   :user/failed-login-count 0})

(def crenshaw
  {:user/username "crenshaw"
   :user/failed-login-count 0})

(def douglass
  {:user/username "douglass"
   :user/failed-login-count 0
   :user/totp-key SECRET})

(def config
  {:db/type :datahike
   :db/config {:store {:id "authdb" :backend :mem}}
   :db/recreate? true
   :db/force? true
   :db/migrations schema/initial
   :db/initial-txns
   [(assoc angela :user/password (hashers/derive "abolition4lyfe"))
    (assoc bobby :user/password (hashers/derive "pantherz"))
    ;; NOTE: you wouldn't normally mix and match hashing algorithms like this.
    ;; This is just a way to test configuring :auth/hash-algorithm.
    (assoc crenshaw :user/password (hashers/derive "intersectionz"
                                                   {:alg :argon2id}))
    (assoc douglass :user/password (hashers/derive "liber4tion"))]})

(use-db :each config)

(defmethod bread/action ::route
  [req _ _]
  (assoc req ::bread/dispatcher {:dispatcher/type ::auth/login=>}))

(defmethod bread/action ::body
  [res {:keys [body]} _]
  (if (:body res) res (assoc res :body body)))

(defmethod bread/action ::debug-expand
  [res _ _]
  (when (= :special (bread/config res :auth/next-param))
    (prn 'body 'EXPAND (:body res)))
  res)

(defn- config->plugins [{:as auth-config :keys [test/body]}]
  (conj
    (defaults/plugins {:db config
                       :routes false})
    (route/plugin {:router (naive-router)})
    {:hooks
     {::bread/route
      [{:action/name ::route
        :action/description "Hard-code the dispatcher."}]
      ::bread/expand
      [(when body
         {:action/name ::body
          :action/description
          "Write a sensitive :body that should get overwritten by auth redirect."
          :body body})]}}
    (auth/plugin auth-config)))

(defn- ->auth-data [{:keys [::bread/data headers status session body]}]
  {::bread/data (select-keys data [:session :auth/result :totp])
   :session session
   :headers headers
   :status status
   :body body})

(deftest
  ^:bread/auth
  test-authentication-flow
  (are
    [expected auth-config req]
    (= expected (let [handler (-> auth-config config->plugins plugins->loaded bread/handler)
                      data (-> req handler ->auth-data)]
                  (if (get-in data [::bread/data :auth/result])
                    (walk/postwalk #(if (map? %) (dissoc % :db/id) %) data)
                    data)))

    ;; Requesting any page anonymously.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/login?next=%2F"}
     :session nil
     ::bread/data {:session nil}
     :body "/login?next=%2F"}
    nil
    {:request-method :get
     :uri "/"}

    ;; Requesting any page anonymously with a custom login page.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/custom?next=%2F"}
     :session nil
     ::bread/data {:session nil}
     :body "/custom?next=%2F"}
    {:login-uri "/custom"
     :test/body [:p "SENSITIVE"]}
    {:request-method :get
     :uri "/"}

    ;; Requesting anonymously with protected route prefixes configured.
    ;; Non-protected URI is ignored.
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body [:p "response body"]}
    {:protected-prefixes #{"/protected"}
     :test/body [:p "response body"]}
    {:request-method :get
     :uri "/not-matching"}

    ;; Requesting home page anonymously with a custom login page and
    ;; protected route prefixes configured. Non-protected URI is ignored.
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body [:p "response body"]}
    {:protected-prefixes #{"/protected"}
     :login-uri "/custom"
     :test/body  [:p "response body"]}
    {:request-method :get
     :uri "/"}

    ;; Requesting anonymously with protected route prefixes configured.
    ;; Protected URIs should get redirected.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/custom?next=%2Fprotected"}
     :session nil
     ::bread/data {:session nil}
     :body "/custom?next=%2Fprotected"}
    {:protected-prefixes #{"/protected"}
     :login-uri "/custom"
     :test/body  [:p "response body"]}
    {:request-method :get
     :uri "/protected"}

    ;; Custom login URI under the /protected route tree gets exempted
    ;; (otherwise no one could ever login).
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body [:p "login page"]}
    {:protected-prefixes #{"/protected"}
     :login-uri "/protected/login"
     :test/body [:p "login page"]}
    {:request-method :get
     :uri "/protected/login"}

    ;; Custom reset URI under the /protected route tree gets exempted
    ;; (otherwise no one could ever reset their password).
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body [:p "reset page"]}
    {:protected-prefixes #{"/protected"}
     :reset-password-uri "/protected/reset"
     :test/body [:p "reset page"]}
    {:request-method :get
     :uri "/protected/reset"}

    ;; Requesting anonymously with protected route prefixes AND a custom
    ;; next-params configured. Protected URIs should get redirected with the
    ;; proper query string.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/custom?special=%2Fprotected"}
     :session nil
     ::bread/data {:session nil}
     :body "/custom?special=%2Fprotected"}
    {:protected-prefixes #{"/protected"}
     :login-uri "/custom"
     :next-param :special
     :test/body [:p "protected page"]}
    {:request-method :get
     :uri "/protected"}

    ;; Requesting anonymously with protected route prefixes AND a custom
    ;; next-params configured. Protected URIs should get redirected with the
    ;; proper query string, also including the destination query string.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/custom?special=%2Fprotected%3Fasdf%3Dqwerty%26a%3D123"}
     :session nil
     ::bread/data {:session nil}
     :body "/custom?special=%2Fprotected%3Fasdf%3Dqwerty%26a%3D123"}
    {:protected-prefixes #{"/protected"}
     :login-uri "/custom"
     :next-param :special
     :test/body [:p "reset page"]}
    {:request-method :get
     :query-string "asdf=qwerty&a=123"
     :uri "/protected"}

    ;; Requesting anonymously with protected route prefixes AND a custom
    ;; next-params configured. Protected URIs should get redirected with the
    ;; proper query string, also including the destination query string.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/custom?special=%2Fprotected%3Fmatz%3D%E3%81%BE%E3%81%A4%E3%82%82%E3%81%A8%E3%82%86%E3%81%8D%E3%81%B2%E3%82%8D"}
     :session nil
     ::bread/data {:session nil}
     ;; TODO
     :body "/custom?special=%2Fprotected%3Fmatz%3D%E3%81%BE%E3%81%A4%E3%82%82%E3%81%A8%E3%82%86%E3%81%8D%E3%81%B2%E3%82%8D"}
    {:protected-prefixes #{"/protected"}
     :login-uri "/custom"
     :next-param :special
     :test/body [:p "protected page"]}
    {:request-method :get
     :query-string "matz=まつもとゆきひろ"
     :uri "/protected"}

    ;; Requesting anonymously with protected route prefixes configured.
    ;; Protected URIs should get redirected.
    {:status 302
     :headers {"content-type" "text/html"
               "Location" "/custom?next=%2Fprotected%2Fsub-route"}
     :session nil
     ::bread/data {:session nil}
     :body "/custom?next=%2Fprotected%2Fsub-route"}
    {:protected-prefixes #{"/protected"}
     :login-uri "/custom"
     :test/body [:p "protected page"]}
    {:request-method :get
     :uri "/protected/sub-route"}

    ;; Requesting the login page.
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :get
     :uri "/login"}

    ;; Requesting the login page; empty auth config map.
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body nil}
    {}
    {:request-method :get
     :uri "/login"}

    ;; Requesting the login page; custom :login-uri.
    ;; This matters because it's what the ::auth/require-auth hook uses
    ;; to check against the current URI, to avoid redirect loops.
    {:status 200
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body [:p "login page"]}
    {:login-uri "/custom"
     :test/body [:p "login page"]}
    {:request-method :get
     :uri "/custom"}

    ;; POST with no data
    {:status 401
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil
                   :auth/result {:valid false :user nil}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :uri "/login"}

    ;; POST with missing password
    {:status 401
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil
                   :auth/result {:valid false :user nil}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "no one"}
     :uri "/login"}

    ;; POST with missing password
    {:status 401
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil
                   :auth/result {:valid false :user nil}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "no one" :password nil}
     :uri "/login"}

    ;; POST with bad username AND password
    {:status 401
     :headers {"content-type" "text/html"}
     :session nil
     ::bread/data {:session nil
                   :auth/result {:valid false :user nil}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "no one" :password "nothing"}
     :uri "/login"}

    ;; POST with bad password
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user angela}
     ::bread/data {:session {:auth/user angela}
                   :auth/result {:update false :valid false :user angela}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "angela" :password "wrongpassword"}
     :uri "/login"}

    ;; POST with correct username & password. Sets session user.
    {:status 302
     :headers {"Location" "/"
               "content-type" "text/html"}
     :session {:user angela
               :auth/step :logged-in}
     ::bread/data {:session {:user angela
                             :auth/step :logged-in}
                   :auth/result {:update false :valid true :user angela}}
     :body "/"}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "angela" :password "abolition4lyfe"}
     :uri "/login"}

    ;; POST with correct username & password. Sets session user.
    {:status 302
     :headers {"Location" "/"
               "content-type" "text/html"}
     :session {:user bobby
               :auth/step :logged-in}
     ::bread/data {:session {:user bobby
                             :auth/step :logged-in}
                   :auth/result {:update false :valid true :user bobby}}
     :body "/"}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "bobby" :password "pantherz"}
     :uri "/login"}

    ;; POST with correct password & next param.
    ;; Sets session user and redirects to account page.
    {:status 302
     :headers {"Location" "/successful-login"
               "content-type" "text/html"}
     :session {:user bobby
               :auth/step :logged-in}
     ::bread/data {:session {:user bobby
                             :auth/step :logged-in}
                   :auth/result {:update false :valid true :user bobby}}
     :body "/successful-login"}
    {:test/body [:p "login page"]}
    {:request-method :post
     :params {:username "bobby" :password "pantherz" :next "/successful-login"}
     :uri "/login"}

    ;; POST with correct password & redirect, with custom :next-param.
    ;; Sets session user and redirects the URI according to the custom param.
    {:status 302
     :headers {"Location" "/successful-login-next"
               "content-type" "text/html"}
     :session {:user bobby
               :auth/step :logged-in}
     ::bread/data {:session {:user bobby
                             :auth/step :logged-in}
                   :auth/result {:update false :valid true :user bobby}}
     ;; User is authorized; renders the body but still redirects.
     :body "/successful-login-next"}
    {:next-param :special
     :test/body [:p "protected page"]}
    {:request-method :post
     :params {:username "bobby" :password "pantherz" :special "/successful-login-next"}
     :uri "/login"}

    ;; POST with correct password; custom hash algo. Sets session user.
    {:status 302
     :headers {"Location" "/"
               "content-type" "text/html"}
     :session {:user crenshaw
               :auth/step :logged-in}
     ::bread/data {:session {:user crenshaw
                             :auth/step :logged-in}
                   :auth/result {:update false :valid true :user crenshaw}}
     :body "/"}
    {:auth/hash-algorithm :argon2id
     :test/body [:p "login page"]}
    {:request-method :post
     :params {:username "crenshaw" :password "intersectionz"}
     :uri "/login"}

    ;; GET when already logged in. Redirect to / by default.
    {:status 302
     :headers {"Location" "/"
               "content-type" "text/html"}
     :session {:user crenshaw}
     ::bread/data {:session {:user crenshaw}}
     :body "/"}
    {:test/body [:p "login page"]}
    {:request-method :get
     :session {:user crenshaw}
     :uri "/login"}

    ;; GET when already logged in. Redirect to custom /
    {:status 302
     :headers {"Location" "/"
               "content-type" "text/html"}
     :session {:user crenshaw}
     ::bread/data {:session {:user crenshaw}}
     :body "/"}
    {:test/body [:p "login page"]}
    {:request-method :get
     :session {:user crenshaw}
     :uri "/login"}

    ;; Logout
    {:status 302
     :headers {"Location" "/login"
               "content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body "/login"}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:user douglass
               :auth/step :logged-in}
     :params {:submit "logout"}
     :uri  "/logout"}

    ;; Logout with custom :login-uri
    {:status 302
     :headers {"Location" "/custom"
               "content-type" "text/html"}
     :session nil
     ::bread/data {:session nil}
     :body "/custom"}
    {:login-uri "/custom"
     :test/body [:p "protected page"]}
    {:request-method :post
     :session {:user douglass
               :auth/step :logged-in}
     :params {:submit "logout"}
     :uri  "/whatever"}

    ;;
    ))

(defn valid-code?
  ([^long code ^String secret]
   (and (= 123456 code) (= SECRET secret)))
  ([^long code ^String secret _]
   (and (= 123456 code) (= SECRET secret))))

(deftest
  ^:bread/auth ^:bread/auth.mfa
  test-authentication-flow-with-mfa
  (are
    [expected auth-config req]
    (= expected (with-redefs [ot/is-valid-totp-token? valid-code?
                              ot/generate-secret-key (constantly SECRET)]
                  (let [handler (-> auth-config config->plugins plugins->loaded bread/handler)
                        data (-> req handler ->auth-data)]
                    (if (get-in data [::bread/data :auth/result])
                      (walk/postwalk #(if (map? %) (dissoc % :db/id) %) data)
                      data))))

    ;; Successful username/password login requiring 2FA step. Should not
    ;; set session user.
    {:status 302
     :headers {"Location" "/login"
               "content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:update false :valid true :user douglass}}
     :body "/login"}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :params {:username "douglass" :password "liber4tion"}
     :uri "/login"}

    ;; Successful username/password login requiring 2FA site-wide, with
    ;; :user/totp-key missing. Should not redirect or set session user yet!
    {:status 302
     :headers {"Location" "/login?next=%2Fsetup-mfa"
               "content-type" "text/html"}
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     ::bread/data {:session {:auth/user crenshaw :auth/step :setup-two-factor}
                   :auth/result {:update false
                                 :valid true
                                 :user crenshaw}}
     :body "/login?next=%2Fsetup-mfa"}
    {:require-mfa? true
     :test/body [:p "protected page"]}
    {:request-method :post
     :params {:username "crenshaw" :password "intersectionz" :next "/setup-mfa"}
     :uri "/login"}

    ;; Redirected after a successful username/password login, before the user
    ;; has set up *REQUIRED* MFA. Should generate a TOTP secret for the user.
    {:status 200
     :headers {"content-type" "text/html"}
     :session {:auth/user crenshaw
               :auth/step :setup-two-factor}
     ::bread/data {:session {:auth/user crenshaw :auth/step :setup-two-factor}
                   :totp {:totp-key SECRET :issuer "example.com"}}
     :body [:p "login page"]}
    {:require-mfa? true
     :test/body [:p "login page"]}
    {:request-method :get
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     :uri "/login"
     :server-name "example.com"}

    ;; Required MFA with a configured MFA issuer.
    {:status 200
     :headers {"content-type" "text/html"}
     :session {:auth/user crenshaw
               :auth/step :setup-two-factor}
     ::bread/data {:session {:auth/user crenshaw :auth/step :setup-two-factor}
                   :totp {:totp-key SECRET :issuer "BREAD"}}
     :body [:p "TOTP page"]}
    {:require-mfa? true
     :mfa-issuer "BREAD"
     :test/body [:p "TOTP page"]}
    {:request-method :get
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     :uri "/login"
     :server-name "example.com"}

    ;; Successful username/password login requiring 2FA step, next param
    ;; Should not redirect or set session user yet!
    {:status 302
     :headers {"Location" "/login?next=%2Fdest2fa"
               "content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:update false :valid true :user douglass}}
     :body "/login?next=%2Fdest2fa"}
    {:test/body [:p "2FA page"]}
    {:request-method :post
     :params {:username "douglass" :password "liber4tion" :next "/dest2fa"}
     :uri "/login"}

    ;; Successful username/password login requiring 2FA step, custom next
    ;; param present. Should not redirect or set session user yet!
    {:status 302
     :headers {"Location" "/login?special=%2Fdest2fa"
               "content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:update false :valid true :user douglass}}
     :body "/login?special=%2Fdest2fa"}
    {:next-param :special
     :test/body [:p "2FA page"]}
    {:request-method :post
     :params {:username "douglass" :password "liber4tion" :special "/dest2fa"}
     :uri "/login"}

    ;; Successful username/password login requiring 2FA step, custom next
    ;; param present. Should redirect but should not set session user yet!
    {:status 302
     :headers {"Location" "/login?special=%2Fdest2fa%3Fquery%3Dstring%26a%3D1"
               "content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:update false :valid true :user douglass}}
     :body "/login?special=%2Fdest2fa%3Fquery%3Dstring%26a%3D1"}
    {:next-param :special
     :test/body [:p "login page"]}
    {:request-method :post
     :params {:username "douglass"
              :password "liber4tion"
              :special "/dest2fa?query=string&a=1"}
     :uri "/login"}

    ;; Setting new TOTP key, submitting a blank code.
    ;; Should show an error and not redirect.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     ::bread/data {:session {:auth/user crenshaw :auth/step :setup-two-factor}
                   :auth/result {:valid false :user crenshaw}
                   :totp {:totp-key SECRET :issuer "example.com"}}
     :body [:p "TOTP page"]}
    {:require-mfa? true
     :login-uri "/setup-two-factor"
     :test/body [:p "TOTP page"]}
    {:request-method :post
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     :params {:totp-key SECRET :two-factor-code ""}
     :uri "/setup-two-factor"
     :server-name "example.com"}

    ;; Setting new TOTP key, submitting an invalid code.
    ;; Should show an error and not redirect.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     ::bread/data {:session {:auth/user crenshaw :auth/step :setup-two-factor}
                   :auth/result {:valid false :user crenshaw}
                   :totp {:totp-key SECRET :issuer "example.com"}}
     :body [:p "TOTP page"]}
    {:require-mfa? true
     :login-uri "/setup-two-factor"
     :test/body [:p "TOTP page"]}
    {:request-method :post
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     :params {:totp-key SECRET :two-factor-code "999999"}
     :uri "/setup-two-factor"
     :server-name "example.com"}

    ;; Setting new TOTP key with a valid code. Sets the session user
    ;; and redirects to /.
    (let [user (assoc crenshaw :user/totp-key SECRET)
          session {:user user :auth/step :logged-in}]
      {:status 302
       :headers {"content-type" "text/html"
                 "Location" "/"}
       :session session
       ::bread/data {:session session
                     :auth/result {:valid true :user user}}
       :body "/"})
    {:require-mfa? true
     :test/body [:p "login page"]}
    {:request-method :post
     :session {:auth/user crenshaw :auth/step :setup-two-factor}
     :params {:totp-key SECRET :two-factor-code "123456"}
     :uri "/login"}

    ;; 2FA with blank code. Should not set session user.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:valid false :user douglass}}
     :body [:p "protected page"]}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code ""}
     :uri  "/login"}

    ;; 2FA with blank code, next param present. Should not set session user.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:valid false :user douglass}}
     :body [:p "protected page"]}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "" :next "/blank-code"}
     :uri  "/login"}

    ;; 2FA with invalid code. Should not set session user.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:valid false :user douglass}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "wpeovwoeginawge"}
     :uri  "/login"}

    ;; 2FA with invalid code, next param present. Should not redirect or set
    ;; session user yet.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:valid false :user douglass}}
     :body [:p "protected page"]}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "wpeovwoeginawge" :next "/invalid-code"}
     :uri  "/login"}

    ;; Unsuccessful 2FA. Should not set session user.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:valid false :user douglass}}
     :body [:p "protected page"]}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "654321"}
     :uri  "/login"}

    ;; Unsuccessful 2FA, next param present. Should not redirect or set
    ;; session user yet.
    {:status 401
     :headers {"content-type" "text/html"}
     :session {:auth/user douglass
               :auth/step :two-factor}
     ::bread/data {:session {:auth/user douglass
                             :auth/step :two-factor}
                   :auth/result {:valid false :user douglass}}
     :body [:p "login page"]}
    {:test/body [:p "login page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "654321" :next "/unsuccessful-2fa"}
     :uri  "/login"}

    ;; Successful 2FA. Sets session user.
    {:status 302
     :headers {"Location" "/"
               "content-type" "text/html"}
     :session {:user douglass
               :auth/step :logged-in}
     ::bread/data {:session {:user douglass
                             :auth/step :logged-in}
                   :auth/result {:valid true :user douglass}}
     :body "/"}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "123456"}
     :uri  "/login"}

    ;; Successful 2FA with redirect. Sets session user and redirects to
    ;; correct destination.
    {:status 302
     :headers {"Location" "/successful-2fa-redirect"
               "content-type" "text/html"}
     :session {:user douglass
               :auth/step :logged-in}
     ::bread/data {:session {:user douglass
                             :auth/step :logged-in}
                   :auth/result {:valid true :user douglass}}
     :body "/successful-2fa-redirect"}
    {:test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "123456" :next "/successful-2fa-redirect"}
     :uri  "/login"}

    ;; Successful 2FA with redirect & custom :next-param. Sets session user
    ;; and redirects correctly.
    {:status 302
     :headers {"Location" "/successful-2fa-custom-next"
               "content-type" "text/html"}
     :session {:user douglass
               :auth/step :logged-in}
     ::bread/data {:session {:user douglass
                             :auth/step :logged-in}
                   :auth/result {:valid true :user douglass}}
     :body "/successful-2fa-custom-next"}
    {:next-param :special
     :test/body [:p "protected page"]}
    {:request-method :post
     :session {:auth/user douglass
               :auth/step :two-factor}
     :params {:two-factor-code "123456"
              :special "/successful-2fa-custom-next"}
     :uri  "/login"}

    ;;
    ))

(deftest test-log-attempt
  (let [!NOW! (Date.)
        app (plugins->loaded [(db/plugin config) (auth/plugin)])
        conn (db/connection app)
        get-user (fn [username]
                   (db/q @conn
                         '{:find [(pull ?e [:user/username
                                            :user/failed-login-count
                                            :user/locked-at]) .]
                           :in [$ ?username]
                           :where [[?e :user/username ?username]]}
                         (or username "")))]
    (are
      [expected args]
      (= expected (let [[effect-data result] args
                        effect (assoc effect-data
                                      :effect/name ::auth/log-attempt)
                        app (-> app
                                (bread/add-effect effect)
                                (assoc ::bread/data {:auth/result result}))]
                    ;; TODO add a step to effect handling so we can
                    ;; return transactions statelessly
                    (binding [t/*now* !NOW!]
                      (bread/hook app ::bread/effects!))
                    (-> result :user :user/username get-user)))

      nil [{:conn conn :max-failed-login-count 5}
           {:user nil :valid nil}]

      ;; valid, 2FA step -> no action
      {:user/username "douglass"
       :user/failed-login-count 0}
      [;; effect
       {:conn conn :max-failed-login-count 5}
       ;; :auth/result
       {:valid true
        :auth/step :two-factor
        :debug true
        :user {;; NOTE: user data isn't checked by auth code here.
               :user/username "douglass"}}]

      ;; valid, :logged-in -> reset count
      {:user/username "douglass"
       :user/failed-login-count 0}
      [;; effect
       {:conn conn :max-failed-login-count 5}
       ;; :auth/result
       {:valid true
        :auth/step :logged-in
        :user {:user/username "douglass"
               :user/failed-login-count 4}}]

      ;; max failed logins
      {:user/username "angela"
       :user/failed-login-count 0
       :user/locked-at !NOW!}
      [;; effect
       {:conn conn :max-failed-login-count 5}
       ;; :auth/result
       {:valid false
        :user {:user/username "angela"
               :user/failed-login-count 5}}]

      ;; locked
      {:user/username "angela"
       :user/failed-login-count 0
       :user/locked-at !NOW!}
      [;; effect
       {:conn conn :max-failed-login-count 5 :lock-seconds 3600}
       ;; :auth/result
       {:valid false
        :user {:user/username "angela"
               :user/failed-login-count 0
               :user/locked-at !NOW!}}]

      ;; invalid -> increment count
      {:user/username "bobby"
       :user/failed-login-count 1}
      [;; effect
       {:conn conn :max-failed-login-count 5}
       ;; :auth/result
       {:valid false
        :user {:user/username "bobby"
               :user/failed-login-count 0}}]

      ;; invalid -> increment count
      {:user/username "crenshaw"
       :user/failed-login-count 3}
      [;; effect
       {:conn conn :max-failed-login-count 5}
       ;; :auth/result
       {:valid false
        :user {:user/username "crenshaw"
               :user/failed-login-count 2}}]

      ;;
      )))

(deftest test-session-store
  (let [app (plugins->loaded [(db/plugin config) (auth/plugin)])
        conn (db/connection app)
        secret-key "qwerty"
        session-store (auth/session-store {:secret-key secret-key} conn)
        get-session-data (fn [sk]
                           (db/q @conn
                                 '{:find [?data .]
                                   :in [$ ?hash]
                                   :where [[?e :session/id ?hash]
                                           [?e :session/data ?data]]}
                                 (sha-512 (str secret-key ":" sk))))]

    (testing "write-session"
      (testing "passing a random string for session key"
        (let [sk (ss/write-session session-store (random/hex 32) {:a :b})]
          (is (string? sk))
          (is (= "{:a :b}" (get-session-data sk)))))

      (testing "passing nil session key"
        (let [sk (ss/write-session session-store nil {:a :b})]
          (is (string? sk))
          (is (= "{:a :b}" (get-session-data sk))))))

    (testing "read-session"
      (let [sk (ss/write-session session-store nil {:a :b})]
        (is (= {:a :b} (dissoc (ss/read-session session-store sk) :db/id)))
        (is (= {:a :b} (dissoc (ss/read-session session-store (str sk)) :db/id))))

      (testing "updating secret-key invalidates all sessions"
        (let [sk (ss/write-session session-store nil {:a :b})
              updated-store (auth/session-store {:secret-key "updated"} conn)]
          (is (nil? (ss/read-session updated-store sk)))))

      (testing "passing nil session key"
        (is (nil? (ss/read-session session-store nil)))))

    (testing "delete-session"
      (let [sk (ss/write-session session-store nil {:a :b})]
        (ss/delete-session session-store sk)
        (is (nil? (get-session-data sk)))
        (is (nil? (ss/read-session session-store sk)))))

    ,))

(comment
  (require '[datahike.api :as d])
  (d/create-database config)
  (d/delete-database config)
  (require '[systems.bread.alpha.util.datalog :as datalog])

  (def app (plugins->loaded [(db/plugin config)]))
  (def $conn (db/connection app))
  (def $store (auth/session-store $conn))
  (def $uuid (UUID/randomUUID))
  (satisfies? ss/SessionStore $store)
  (ss/write-session $store $uuid {:a :b})
  (datalog/attrs @$conn)
  (db/q @$conn '{:find [(pull ?e [*]) .]
                 :in [$ ?sk]
                 :where [[?e :session/data]]})

  (require '[kaocha.repl :as k])
  (k/run #'test-authentication-flow {:color? false})
  (k/run #'test-authentication-flow-with-mfa {:color? false})
  (k/run #'test-authentication-flow #'test-authentication-flow-with-mfa {:color? false})
  (k/run #'test-session-store {:color? false})
  (k/run {:color? false}))
