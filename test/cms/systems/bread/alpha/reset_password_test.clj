(ns systems.bread.alpha.reset-password-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are]]

    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded
                                              mock-derive
                                              mock-sha-512]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.plugin.auth :as auth]
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
        (fn [seconds]
          {:expansion/name ::auth/authenticate-reset
           :expansion/description "Authentication reset code."
           :expansion/key :validation
           :reset-expiration-seconds seconds})]
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
                    (seconds->authenticate-expansion 42)]}
      {:reset-expiration-seconds 42}
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

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
