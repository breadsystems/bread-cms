(ns systems.bread.alpha.plugin.signup-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are]]

    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded
                                              use-db]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.signup :as signup]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.schema :as schema])
  (:import
    [java.util Date]))

(def db-config
  {:db/type :datahike
   :db/migrations schema/initial
   :db/config {:store {:backend :mem :id "signup-test-db"}}})

(use-db :each db-config)

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

      ,)))

#_
(deftest test-validate-expansion
  ;; TODO
  )

#_
(deftest test-check-invitation-age
  ;; TODO
  )

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
