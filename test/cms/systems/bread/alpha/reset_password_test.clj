(ns systems.bread.alpha.reset-password-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are]]
    [crypto.random :as random]

    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded
                                              mock-derive
                                              mock-sha-512]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.plugin.email :as email]
    [systems.bread.alpha.ring :as ring])
  (:import
    [java.util Date]))

(deftest test-reset-password=>
  (let [db-plugin (db->plugin ::FAKEDB)
        db-conn (:db/connection (:config db-plugin))
        code->expansion
        (fn [code]
          {:expansion/args
           ['{:find [(pull
                       ?e
                       [:db/id
                        :thing/updated-at
                        {:reset/user [:db/id
                                      :user/username
                                      :user/totp-key
                                      :user/locked-at
                                      :user/failed-login-count]}]) .]
              :in [$ ?code]
              :where [[?e :reset/code ?code]
                      (not [?e :reset/reset-at])]}
            (mock-sha-512 code)]
           :expansion/db ::FAKEDB
           :expansion/description "Find the user matching the reset code."
           :expansion/key :reset
           :expansion/name ::db/query})
        seconds->authenticate-expansion
        (fn [expiration-seconds & [lock-seconds]]
          {:expansion/name ::auth/authenticate-reset
           :expansion/description "Authentication reset code."
           :expansion/key :validation
           :reset-expiration-seconds expiration-seconds
           :lock-seconds (or lock-seconds 3600)})]
    (are
      [expected config req]
      (= expected (let [dispatcher {:dispatcher/type ::auth/reset-password=>}
                        auth-config (merge {:secret-key "secret"} config)
                        app (plugins->loaded [db-plugin (auth/plugin auth-config)])
                        req* (merge app req {::bread/dispatcher dispatcher})]
                    (with-redefs [hashers/derive mock-derive
                                  sha-512 mock-sha-512]
                      (bread/dispatch req*))))

      ;; Just loading the reset page.
      {:expansions [(code->expansion "secret:qwerty")
                    (seconds->authenticate-expansion 600)]}
      nil
      {:request-method :get
       :uri "/reset"
       :params {:code "qwerty"}}

      ;; Loading the reset page, different code.
      {:expansions [(code->expansion "secret:foo")
                    (seconds->authenticate-expansion 600)]}
      nil
      {:request-method :get
       :uri "/reset"
       :params {:code "foo"}}

      ;; Loading reset page with non-default expiration seconds.
      {:expansions [(code->expansion "secret:foo")
                    (seconds->authenticate-expansion 42 420)]}
      {:reset-expiration-seconds 42
       :lock-seconds 420}
      {:request-method :get
       :uri "/reset"
       :params {:code "foo"}}

      ;; Submitting reset page.
      {:expansions [(code->expansion "secret:foo")
                    (seconds->authenticate-expansion 600)
                    {:expansion/name ::auth/validate-reset
                     :expansion/key :validation
                     :expansion/description "Validate password update."
                     :params {:code "foo"
                              :password "newpassword"
                              :password-confirmation "newpassword"}
                     :min-password-length 12
                     :max-password-length 72}]
       :effects [{:effect/name ::auth/reset-password!
                 :effect/description "Update password upon valid submission."
                 :hash-algorithm :bcrypt+blake2b-512
                 :params {:code "foo"
                          :password "newpassword"
                          :password-confirmation "newpassword"}
                 :conn db-conn}]
       :hooks {::bread/render
               [{:action/name ::ring/redirect-when
                 :action/description "Render reset page or redirect to login."
                 :to "/login"
                 :path [:validation 0]}]}}
      {}
      {:request-method :post
       :uri "/reset"
       :params {:code "foo"
                :password "newpassword"
                :password-confirmation "newpassword"}}

      ;; Submitting reset page.
      {:expansions [(code->expansion "secret:foo")
                    (seconds->authenticate-expansion 600)
                    {:expansion/name ::auth/validate-reset
                     :expansion/key :validation
                     :expansion/description "Validate password update."
                     :params {:code "foo"
                              :password "newpassword"
                              :password-confirmation "newpassword"}
                     :min-password-length 12
                     :max-password-length 72}]
       :effects [{:effect/name ::auth/reset-password!
                 :effect/description "Update password upon valid submission."
                 :hash-algorithm :bcrypt+blake2b-512
                 :params {:code "foo"
                          :password "newpassword"
                          :password-confirmation "newpassword"}
                 :conn db-conn}]
       :hooks {::bread/render
               [{:action/name ::ring/redirect-when
                 :action/description "Render reset page or redirect to login."
                 :to "/login"
                 :path [:validation 0]}]}}
      {}
      {:request-method :post
       :uri "/reset"
       :params {:code "foo"
                :password "newpassword"
                :password-confirmation "newpassword"}}

      ;; Submitting reset page with custom auth config.
      {:expansions [(code->expansion "different:foo")
                    (seconds->authenticate-expansion 42)
                    {:expansion/name ::auth/validate-reset
                     :expansion/key :validation
                     :expansion/description "Validate password update."
                     :params {:code "foo"
                              :password "newpassword"
                              :password-confirmation "newpassword"}
                     :min-password-length 3
                     :max-password-length 33}]
       :effects [{:effect/name ::auth/reset-password!
                 :effect/description "Update password upon valid submission."
                 :hash-algorithm :bcrypt+blake2b-512
                 :params {:code "foo"
                          :password "newpassword"
                          :password-confirmation "newpassword"}
                 :conn db-conn}]
       :hooks {::bread/render
               [{:action/name ::ring/redirect-when
                 :action/description "Render reset page or redirect to login."
                 :to "/login"
                 :path [:validation 0]}]}}
      {:secret-key "different"
       :reset-expiration-seconds 42
       :min-password-length 3
       :max-password-length 33}
      {:request-method :post
       :uri "/reset"
       :params {:code "foo"
                :password "newpassword"
                :password-confirmation "newpassword"}}

      ,)))

(deftest test-forgot-password=>
  (let [db-plugin (db->plugin ::FAKEDB)
        db-conn (:db/connection (:config db-plugin))
        username->expansion
        (fn [username]
          {:expansion/name ::db/query
           :expansion/description "Query user by username."
           :expansion/key :user
           :expansion/db ::FAKEDB
           :expansion/args ['{:find [(pull ?e [:db/id
                                               :user/locked-at
                                               {:reset/_user
                                                [:db/id
                                                 :thing/updated-at
                                                 :reset/reset-at]}
                                               {:user/emails [*]}]) .]
                              :in [$ ?username]
                              :where [[?e :user/username ?username]]}
                            username]})
        forgot-effect
        {:effect/name ::auth/forgot-password!
         :effect/description "Send user a reset link, if they have a confirmed email."
         :conn db-conn
         :secret-key "secret"}]
    (are
      [expected config req]
      (= expected (let [dispatcher {:dispatcher/type ::auth/forgot-password=>}
                        auth-config (merge {:secret-key "secret"} config)
                        app (plugins->loaded [db-plugin (auth/plugin auth-config)])
                        req* (merge app req {::bread/dispatcher dispatcher})]
                    (bread/dispatch req*)))

      ;; Just loading the forgot password page. No special logic for GET requests.
      nil nil {:request-method :get :uri "/forgot"}

      ;; Submitting the forgot page.
      {:expansions [(username->expansion "test")]
       :effects [forgot-effect]}
      nil
      {:request-method :post
       :uri "/forgot"
       :params {:username "test"}}

      ;; Submitting the forgot page with a differen username.
      {:expansions [(username->expansion "soandso")]
       :effects [forgot-effect]}
      nil
      {:request-method :post
       :uri "/forgot"
       :params {:username "soandso"}}

      ,)))

(deftest test-forgot-password!
  (let [!now (Date.)]
    (are
      [expected effect data]
      (= expected (with-redefs [sha-512 mock-sha-512
                                random/hex (constantly "randomhex")]
                    (binding [t/*now* !now]
                      (bread/effect effect data))))

      ;; Invalid request; noop.
      nil {:effect/name ::auth/reset-password!} nil
      nil {:effect/name ::auth/reset-password!} {}

      ;; Bad username.
      nil {:effect/name ::auth/reset-password!} {:user false}

      ;; User with no emails setup.
      nil
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123}
       :config {:auth/lock-seconds 3600}}

      ;; User with no confirmed emails.
      nil
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123
              :user/emails [{:email/address "whatever"}]}
       :config {:auth/lock-seconds 3600}}

      ;; Unconfirmed primary email. This is an abnormal situation, but we want to assert
      ;; assert here that both conditions (primary, confirmed) should be true.
      nil
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123
              :user/emails [{:email/address "whatever"
                             :email/primary? true}]}
       :config {:auth/lock-seconds 3600}}

      ;; Confirmed, non-primary email. Also abnormal, per above.
      nil
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123
              :user/emails [{:email/address "whatever"
                             :email/confirmed-at (t/seconds-ago 1)}]}
       :config {:auth/lock-seconds 3600}}

      ;; First forgot request.
      {:effects
       [{:effect/name ::db/transact
         :effect/description "Create a password reset."
         :conn ::DBCONN
         :txs [{:reset/code "sha-512[secret:randomhex]"
                :reset/user 123
                :thing/updated-at !now
                :thing/created-at !now}]}
        {:effect/name ::auth/reset-password-email!
         :effect/decsription "Create password reset message."
         :to "someone@example.com"
         :code "randomhex"}]}
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123
              :user/emails [{:email/address "someone@example.com"
                             :email/primary? true
                             :email/confirmed-at (t/seconds-ago 1)}]}
       :config {:auth/lock-seconds 3600}}

      ;; Subsequent forgot request.
      {:effects
       [{:effect/name ::db/transact
         :effect/description "Create a password reset."
         :conn ::DBCONN
         :txs [{:db/id 456 ;; no created-at
                :reset/code "sha-512[secret:randomhex]"
                :reset/user 123
                :thing/updated-at !now}]}
        {:effect/name ::auth/reset-password-email!
         :effect/decsription "Create password reset message."
         :to "someone@example.com"
         :code "randomhex"}]}
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123
              :user/emails [{:email/address "someone@example.com"
                             :email/primary? true
                             :email/confirmed-at (t/seconds-ago 1)}]
              :reset/_user [{:db/id 456
                             :reset/code "oldcode"
                             :thing/updated-at (t/seconds-ago 60)}]}
       :config {:auth/lock-seconds 3600}}

      ;; Subsequent forgot request; multiple confirmed emails.
      {:effects
       [{:effect/name ::db/transact
         :effect/description "Create a password reset."
         :conn ::DBCONN
         :txs [{:db/id 456 ;; no created-at
                :reset/code "sha-512[secret:randomhex]"
                :reset/user 123
                :thing/updated-at !now}]}
        {:effect/name ::auth/reset-password-email!
         :effect/decsription "Create password reset message."
         :to "primary@example.com"
         :code "randomhex"}]}
      {:effect/name ::auth/forgot-password!
       :secret-key "secret"
       :conn ::DBCONN}
      {:user {:db/id 123
              :user/emails [{:email/address "primary@example.com"
                             :email/primary? true
                             :email/confirmed-at (t/seconds-ago 100)}
                            {:email/address "second@example.com"
                             :email/confirmed-at (t/seconds-ago 10)}
                            {:email/address "first@example.com"
                             :email/confirmed-at (t/seconds-ago 1)}]
              :reset/_user [{:db/id 456
                             :reset/code "oldcode"
                             :thing/updated-at (t/seconds-ago 60)}]}
       :config {:auth/lock-seconds 3600}}

      ,)))

(deftest test-reset-password-email!
  (are
    [expected effect data]
    (= expected (with-redefs [random/hex (constantly "randomhex")]
                  (bread/effect effect data)))

    {:effects
     [{:effect/name ::email/send!
       :effect/description "Send reset password email."
       :message {:from "app@bread.systems"
                 :to "someone@example.com"
                 :subject "reset yr pwd"
                 :body (str "site: Example Site "
                            "link: https://bread.systems/reset?code=qwerty")}}]}
    {:effect/name ::auth/reset-password-email!
     :to "someone@example.com"
     :code "qwerty"}
    {:config {:site/name "Example Site"
              :auth/reset-password-uri "/reset"
              :email/smtp-from-email "app@bread.systems"}
     :i18n {:auth/reset-password-email-body "site: %s link: %s"
            :auth/reset-password-email-subject "reset yr pwd"}
     :ring/scheme :https
     :ring/server-name "bread.systems"}

    ;; Without a configured :site/name.
    {:effects
     [{:effect/name ::email/send!
       :effect/description "Send reset password email."
       :message {:from "alt@bread.systems"
                 :to "other@example.com"
                 :subject "reset yr pwd"
                 :body (str "site: bread.systems "
                            "link: http://bread.systems:8080/reset?code=mycode")}}]}
    {:effect/name ::auth/reset-password-email!
     :to "other@example.com"
     :code "mycode"}
    {:config {:auth/reset-password-uri "/reset"
              :email/smtp-from-email "alt@bread.systems"}
     :i18n {:auth/reset-password-email-body "site: %s link: %s"
            :auth/reset-password-email-subject "reset yr pwd"}
     :ring/scheme :http
     :ring/server-port 8080
     :ring/server-name "bread.systems"}

    ,))

(deftest test-authenticate-reset
  (are
    [expected expansion data]
    (= expected (bread/expand expansion data))

    [false :auth/invalid-reset]
    {:expansion/name ::auth/authenticate-reset
     :reset-expiration-seconds 1
     :lock-seconds 3600}
    {:reset false}

    [false :auth/invalid-reset]
    {:expansion/name ::auth/authenticate-reset
     :reset-expiration-seconds 60
     :lock-seconds 3600}
    {:reset false}

    [true nil]
    {:expansion/name ::auth/authenticate-reset
     :reset-expiration-seconds 60
     :lock-seconds 3600}
    {:reset {:thing/updated-at (t/seconds-ago 59)
             :reset/user {:db/id 123}}}

    ;; Attempting to reset a locked account.
    [false :auth/invalid-reset]
    {:expansion/name ::auth/authenticate-reset
     :reset-expiration-seconds 60
     :lock-seconds 3600}
    {:reset {:thing/updated-at (t/seconds-ago 59)
             :reset/user {:db/id 123
                          :user/locked-at (t/seconds-ago 3599)}}}

    ;; Previously locked account.
    [true nil]
    {:expansion/name ::auth/authenticate-reset
     :reset-expiration-seconds 60
     :lock-seconds 3600}
    {:reset {:thing/updated-at (t/seconds-ago 59)
             :reset/user {:db/id 123
                          :user/locked-at (t/seconds-ago 3601)}}}

    ,))

(deftest test-validate-reset
  (are
    [expected expansion data]
    (= expected (bread/expand expansion data))

    ;; Prior validation. Pass-through.
    [false :auth/invalid-reset]
    {:expansion/name ::auth/validate-reset}
    {:validation [false :auth/invalid-reset]}

    ;; No params present.
    [false :auth/enter-confirm-new-password]
    {:expansion/name ::auth/validate-reset
     :min-password-length 1
     :max-password-length 2
     :params {}}
    {:validation [true nil]}

    ;; No params present.
    [false :auth/enter-confirm-new-password]
    {:expansion/name ::auth/validate-reset
     :min-password-length 1
     :max-password-length 2
     :params {;; Code does not come into play at this stage.
              :password ""
              :password-confirmation ""}}
    {:validation [true nil]}

    ;; Password but no confirmation.
    [false :auth/enter-confirm-new-password]
    {:expansion/name ::auth/validate-reset
     :min-password-length 1
     :max-password-length 2
     :params {;; Code does not come into play at this stage.
              :password "asdf"
              :password-confirmation ""}}
    {:validation [true nil]}

    ;; Confirmation; no password.
    [false :auth/enter-confirm-new-password]
    {:expansion/name ::auth/validate-reset
     :min-password-length 1
     :max-password-length 2
     :params {;; Code does not come into play at this stage.
              :password ""
              :password-confirmation "asdf"}}
    {:validation [true nil]}

    ;; Password mismatch.
    [false :auth/passwords-must-match]
    {:expansion/name ::auth/validate-reset
     :min-password-length 3
     :max-password-length 10
     :params {;; Code does not come into play at this stage.
              :password "one"
              :password-confirmation "two"}}
    {:validation [true nil]}

    ;; Password under minimum length.
    [false [:auth/password-must-be-at-least 12]]
    {:expansion/name ::auth/validate-reset
     :min-password-length 12
     :max-password-length 72
     :params {;; Code does not come into play at this stage.
              :password "elevenchars"
              :password-confirmation "elevenchars"}}
    {:validation [true nil]}

    ;; Password under minimum length.
    [false [:auth/password-must-be-at-least 12]]
    {:expansion/name ::auth/validate-reset
     :min-password-length 12
     :max-password-length 72
     :params {;; Code does not come into play at this stage.
              :password "2short"
              :password-confirmation "2short"}}
    {:validation [true nil]}

    ;; Password over maximum length.
    [false [:auth/password-must-be-at-most 12]]
    {:expansion/name ::auth/validate-reset
     :min-password-length 12
     :max-password-length 12
     :params {;; Code does not come into play at this stage.
              :password "thirteenchars"
              :password-confirmation "thirteenchars"}}
    {:validation [true nil]}

    ;; Password over maximum length.
    [false [:auth/password-must-be-at-most 12]]
    {:expansion/name ::auth/validate-reset
     :min-password-length 12
     :max-password-length 12
     :params {;; Code does not come into play at this stage.
              :password "this password is way too long"
              :password-confirmation "this password is way too long"}}
    {:validation [true nil]}

    ,))

(deftest test-reset-password!
  (let [!now (Date.)]
    (are
      [expected effect data]
      (= expected (with-redefs [hashers/derive mock-derive]
                    (binding [t/*now* !now]
                      (bread/effect effect data))))

      nil {:effect/name ::auth/reset-password!} nil
      nil {:effect/name ::auth/reset-password!} {}
      nil {:effect/name ::auth/reset-password!} {:validation nil}
      nil {:effect/name ::auth/reset-password!} {:validation []}
      nil {:effect/name ::auth/reset-password!} {:validation [false :whatever]}

      {:effects [{:effect/name ::db/transact
                  :conn ::DBCONN
                  :txs [{:db/id 123
                         ;; TODO secret-key
                         :user/password "[:algo+password123]"
                         :thing/updated-at !now}
                        {:db/id 456
                         :reset/reset-at !now
                         :thing/updated-at !now}]}]}
      {:effect/name ::auth/reset-password!
       :params {:password "password123"}
       :conn ::DBCONN
       :hash-algorithm :algo}
      {:validation [true nil]
       :reset {:db/id 456 :reset/user {:db/id 123}}}

      {:effects [{:effect/name ::db/transact
                  :conn ::DBCONN
                  :txs [{:db/id 567
                         ;; TODO secret-key
                         :user/password "[:roll-yr-own-crypto+newpass]"
                         :thing/updated-at !now}
                        {:db/id 345
                         :reset/reset-at !now
                         :thing/updated-at !now}]}]}
      {:effect/name ::auth/reset-password!
       :params {:password "newpass"}
       :conn ::DBCONN
       :hash-algorithm :roll-yr-own-crypto}
      {:validation [true nil]
       :reset {:db/id 345 :reset/user {:db/id 567}}}

      ,)))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
