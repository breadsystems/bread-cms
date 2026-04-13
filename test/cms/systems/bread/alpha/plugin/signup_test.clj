(ns systems.bread.alpha.plugin.signup-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are]]

    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.signup :as signup]
    [systems.bread.alpha.plugin.auth :as auth])
  (:import
    [java.util Date]))

(deftest test-signup=>
  (let [!now (Date.)
        db-plugin (db->plugin ::FAKEDB)
        db-conn (:db/connection (:config db-plugin))]
    (are
      [expected config req]
      (= expected (let [dispatcher {:dispatcher/type ::signup/signup=>}
                        {:keys [signup-config auth-config]} config
                        app (plugins->loaded [db-plugin
                                              (auth/plugin auth-config)
                                              (signup/plugin signup-config)])
                        req* (merge app req {::bread/dispatcher dispatcher})]
                    (binding [t/*now* !now]
                      (with-redefs [hashers/derive (fn [pw {:keys [alg]}]
                                                     (str "[" alg "+" pw "]"))
                                    sha-512 #(str "sha-512[" % "]")]
                        (bread/dispatch req*)))))

      ;; Just loading the signup page.
      {:expansions [{:expansion/name ::db/query
                     :expansion/description "Query invitation by code."
                     :expansion/key :invitation
                     :expansion/db ::FAKEDB
                     :expansion/args ['{:find [(pull ?e [:thing/updated-at
                                                         :invitation/code
                                                         {:invitation/email [*]}]) .]
                                        :in [$ ?code]
                                        :where [[?e :invitation/code ?code]
                                                (not [?e :invitation/redeemer])]}
                                      "sha-512[qwerty]"]}
                    {:expansion/name ::signup/check-invitation-age
                     :expansion/description "Ensure invitation is sufficiently recent."
                     :expansion/key :invitation
                     :invitation-expiration-seconds (* 72 60 60)}]}
      {}
      {:request-method :get
       :uri "/signup"
       :params {:code "qwerty"}}

      ;; Loading signup page, custom invitation seconds.
      {:expansions [{:expansion/name ::db/query
                     :expansion/description "Query invitation by code."
                     :expansion/key :invitation
                     :expansion/db ::FAKEDB
                     :expansion/args ['{:find [(pull ?e [:thing/updated-at
                                                         :invitation/code
                                                         {:invitation/email [*]}]) .]
                                        :in [$ ?code]
                                        :where [[?e :invitation/code ?code]
                                                (not [?e :invitation/redeemer])]}
                                      "sha-512[qwerty]"]}
                    {:expansion/name ::signup/check-invitation-age
                     :expansion/description "Ensure invitation is sufficiently recent."
                     :expansion/key :invitation
                     :invitation-expiration-seconds 3600}]}
      {:signup-config {:invitation-expiration-seconds 3600}}
      {:request-method :get
       :uri "/signup"
       :params {:code "qwerty"}}

      ;; Enacting a signup.
      {:expansions [{:expansion/name ::db/query
                     :expansion/description "Query invitation by code."
                     :expansion/key :invitation
                     :expansion/db ::FAKEDB
                     :expansion/args ['{:find [(pull ?e [:thing/updated-at
                                                         :invitation/code
                                                         {:invitation/email [*]}]) .]
                                        :in [$ ?code]
                                        :where [[?e :invitation/code ?code]
                                                (not [?e :invitation/redeemer])]}
                                      "sha-512[submitted]"]}
                    {:expansion/name ::signup/check-invitation-age
                     :expansion/description "Ensure invitation is sufficiently recent."
                     :expansion/key :invitation
                     :invitation-expiration-seconds (* 72 60 60)}
                    {:expansion/name ::db/query
                     :expansion/key :existing-username
                     :expansion/description "Check for existing users by username."
                     :expansion/db ::FAKEDB
                     :expansion/args ['{:find [?e .]
                                        :in [$ ?username]
                                        :where [[?e :user/username ?username]]}
                                      "coby"]}
                    {:expansion/name ::signup/validate
                     :expansion/description "Validate this signup request."
                     :expansion/key :validation
                     :params {:code "submitted"
                              :username "coby"
                              :password "password"
                              :password-confirmation "password"}
                     :min-password-length 12
                     :max-password-length 72
                     :invite-only? false}]
       :effects [{:effect/name ::signup/enact-valid-signup
                  :effect/description "If the signup is valid, create the account."
                  :effect/key :new-user
                  :user {:thing/created-at !now
                         :user/username "coby"
                         :user/password "[:bcrypt+blake2b-512+password]"}
                  :conn db-conn}]
       :hooks {::bread/render [{:action/description "Redirect to login"
                                :action/name ::signup/redirect}]}}
      {}
      {:request-method :post
       :uri "/signup"
       :params {:code "submitted"
                :username "coby"
                :password "password"
                :password-confirmation "password"}}

      ;; Enacting a signup with custom config.
      {:expansions [{:expansion/name ::db/query
                     :expansion/description "Query invitation by code."
                     :expansion/key :invitation
                     :expansion/db ::FAKEDB
                     :expansion/args ['{:find [(pull ?e [:thing/updated-at
                                                         :invitation/code
                                                         {:invitation/email [*]}]) .]
                                        :in [$ ?code]
                                        :where [[?e :invitation/code ?code]
                                                (not [?e :invitation/redeemer])]}
                                      "sha-512[submitted]"]}
                    {:expansion/name ::signup/check-invitation-age
                     :expansion/description "Ensure invitation is sufficiently recent."
                     :expansion/key :invitation
                     :invitation-expiration-seconds (* 72 60 60)}
                    {:expansion/name ::db/query
                     :expansion/key :existing-username
                     :expansion/description "Check for existing users by username."
                     :expansion/db ::FAKEDB
                     :expansion/args ['{:find [?e .]
                                        :in [$ ?username]
                                        :where [[?e :user/username ?username]]}
                                      "coby"]}
                    {:expansion/name ::signup/validate
                     :expansion/description "Validate this signup request."
                     :expansion/key :validation
                     :params {:code "submitted"
                              :username "coby"
                              :password "password"
                              :password-confirmation "password"}
                     :min-password-length 4
                     :max-password-length 42
                     :invite-only? true}]
       :effects [{:effect/name ::signup/enact-valid-signup
                  :effect/description "If the signup is valid, create the account."
                  :effect/key :new-user
                  :user {:thing/created-at !now
                         :user/username "coby"
                         :user/password "[:bcrypt+blake2b-512+password]"}
                  :conn db-conn}]
       :hooks {::bread/render [{:action/description "Redirect to login"
                                :action/name ::signup/redirect}]}}
      {:signup-config {:invite-only? true}
       :auth-config {:min-password-length 4
                     :max-password-length 42}}
      {:request-method :post
       :uri "/signup"
       :params {:code "submitted"
                :username "coby"
                :password "password"
                :password-confirmation "password"}}

      ,)))

(deftest test-validate-expansion
  (are
    [expected expansion data]
    (= expected (bread/expand (assoc expansion :expansion/name ::signup/validate) data))

    [false :signup/all-fields-required]
    {:params {}
     ;; NOTE: doesn't matter what these are for this check
     :min-password-length 0
     :max-password-length 0}
    {}

    [false :signup/all-fields-required]
    {:params {:username "" :password "" :password-confirmation ""}
     :min-password-length 1
     :max-password-length 72}
    {}

    [false :signup/all-fields-required]
    {:params {:username "" :password ""}
     :min-password-length 0
     :max-password-length 0}
    {}

    [false :signup/all-fields-required]
    {:params {:password "" :password-confirmation ""}
     :min-password-length 0
     :max-password-length 0}
    {}

    [false :signup/all-fields-required]
    {:params {:username "" :password-confirmation ""}
     :min-password-length 0
     :max-password-length 0}
    {}

    [false :auth/passwords-must-match]
    {:params {:username "a" :password "a" :password-confirmation ""}
     :min-password-length 0
     :max-password-length 0}
    {:existing-username false}

    [false :auth/passwords-must-match]
    {:params {:username "a" :password "a" :password-confirmation "b"}
     :min-password-length 0
     :max-password-length 0}
    {:existing-username false}

    [false :auth/passwords-must-match]
    {:params {:username "a" :password "abc" :password-confirmation "xyz"}
     :min-password-length 0
     :max-password-length 0}
    {:existing-username false}

    [false [:auth/password-must-be-at-least 4]]
    {:params {:username "a" :password "abc" :password-confirmation "abc"}
     :min-password-length 4
     :max-password-length 0}
    {:existing-username false}

    [false [:auth/password-must-be-at-least 10]]
    {:params {:username "a" :password "abc" :password-confirmation "abc"}
     :min-password-length 10
     :max-password-length 0}
    {:existing-username false}

    [false [:auth/password-must-be-at-most 4]]
    {:params {:username "a" :password "12345" :password-confirmation "12345"}
     :min-password-length 3
     :max-password-length 4}
    {:existing-username false}

    [false [:auth/password-must-be-at-most 10]]
    {:params {:username "a" :password "12345678901" :password-confirmation "12345678901"}
     :min-password-length 3
     :max-password-length 10}
    {:existing-username false}

    [false :signup/username-exists]
    {:params {:username "a" :password "password" :password-confirmation "password"}
     :min-password-length 8
     :max-password-length 10}
    {:existing-username {}}

    [true nil]
    {:params {:username "a" :password "password" :password-confirmation "password"}
     :min-password-length 8
     :max-password-length 10}
    {:existing-username false}

    ,))

(deftest test-check-invitation-age
  (are
    [expected !now expansion data]
    (= expected (binding [t/*now* !now]
                  (bread/expand (assoc expansion
                                       :expansion/name ::signup/check-invitation-age)
                                data)))

    nil (Date.) {:invitation-expiration-seconds 0} {}
    nil (Date.) {:invitation-expiration-seconds 3600} {}
    nil (Date.) {:invitation-expiration-seconds 1} {}

    ;; JUST expired.
    nil
    (Date.)
    {:invitation-expiration-seconds 3600}
    {:invitation {:thing/updated-at (t/seconds-ago 3600)}}

    ;; JUST expired.
    nil
    #inst "2026-04-12T00:00:00"
    {:invitation-expiration-seconds 60}
    {:invitation {:thing/updated-at #inst "2026-04-11T23:59:00"
                  :invitation/code "qwerty"}}

    ;; Expired hours ago.
    nil
    #inst "2026-04-12T00:00:00"
    {:invitation-expiration-seconds 3600}
    {:invitation {:thing/updated-at #inst "2026-04-11T20:00:00"
                  :invitation/code "qwerty"}}

    ;; Invitation is *just* recent enough by one second.
    {:thing/updated-at #inst "2026-04-11T23:59:01"
     :invitation/code "qwerty"}
    #inst "2026-04-12T00:00:00"
    {:invitation-expiration-seconds 60}
    {:invitation {:thing/updated-at #inst "2026-04-11T23:59:01"
                  :invitation/code "qwerty"}}

    ;; Invitation expires in the future.
    {:thing/updated-at #inst "2026-04-12T00:00:00"
     :invitation/code "qwerty"}
    #inst "2026-04-12T00:00:00"
    {:invitation-expiration-seconds 3600}
    {:invitation {:thing/updated-at #inst "2026-04-12T00:00:00"
                  :invitation/code "qwerty"}}

    ;; Updated just now.
    {:thing/updated-at #inst "2026-04-12T00:00:00"
     :invitation/code "qwerty"}
    #inst "2026-04-12T00:00:00"
    {:invitation-expiration-seconds 3600}
    {:invitation {:thing/updated-at #inst "2026-04-12T00:00:00"
                  :invitation/code "qwerty"}}

    ;; Updated just a minute ago.
    {:thing/updated-at #inst "2026-04-12T00:00:00"
     :invitation/code "qwerty"}
    #inst "2026-04-12T00:01:00"
    {:invitation-expiration-seconds 3600}
    {:invitation {:thing/updated-at #inst "2026-04-12T00:00:00"
                  :invitation/code "qwerty"}}

    ;; Setting expiration seconds < 0 means invitation codes are ALWAYS expired.
    ;; This could be used e.g. to (temporarily?) disable invitations.
    nil
    #inst "2026-04-12T00:01:00"
    {:invitation-expiration-seconds -1}
    {:invitation {:thing/updated-at #inst "2026-04-12T00:00:00"
                  :invitation/code "qwerty"}}

    ;; Setting an expiration of zero means codes never expire.
    {:thing/updated-at #inst "2026-04-11T00:00:00"
     :invitation/code "qwerty"}
    #inst "2026-04-12T00:00:00"
    {:invitation-expiration-seconds 0}
    {:invitation {:thing/updated-at #inst "2026-04-11T00:00:00"
                  :invitation/code "qwerty"}}

    ,))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
