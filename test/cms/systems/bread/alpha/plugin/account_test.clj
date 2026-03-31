(ns systems.bread.alpha.plugin.account-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are]]

    [systems.bread.alpha.test-helpers :refer [db->plugin
                                              plugins->loaded
                                              use-db]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.plugin.account :as account]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.ring :as ring]
    [systems.bread.alpha.schema :as schema]))

(def db-config
  {:db/type :datahike
   :db/migrations schema/initial
   :db/config {:store {:backend :mem :id "account-test-db"}}})

(use-db :each db-config)

(deftest test-account=>
  (let [db-plugin (db->plugin ::FAKEDB)
        db-conn (:db/connection (:config db-plugin))
        query-user
        {:expansion/args ['{:find [(pull ?e [:user/attrs]) .]
                            :in [$ ?e]}
                          123]
         :expansion/db ::FAKEDB
         :expansion/description "Query for all user account data"
         :expansion/key :user
         :expansion/name ::db/query}
        expand-user
        {:expansion/description "Expand user data"
         :expansion/key :user
         :expansion/name ::account/user}
        success-hook
        {:action/name ::ring/redirect
         :action/description "Redirect to account page after taking an account action"
         :flash {:success-key :account/account-updated}
         :to "/account"}]
    (are
      [expected config req]
      (= expected (let [dispatcher {:dispatcher/type ::account/account=>
                                    :dispatcher/pull [:user/attrs]}
                        {:keys [account-config auth-config]} config
                        app (plugins->loaded [db-plugin
                                              (i18n/plugin
                                                {:supported-langs [:be :de]
                                                 :lang-names {:be "Belarusian"
                                                              :de "Deutsche"}})
                                              (auth/plugin auth-config)
                                              (account/plugin account-config)])
                        req* (merge app req {::bread/dispatcher dispatcher})]
                    (with-redefs [hashers/derive (fn [pw {:keys [alg]}]
                                                   (str "[" alg "+" pw "]"))]
                      (bread/dispatch req*))))

      ;; Just loading the account page.
      {:expansions [query-user
                    expand-user
                    {:expansion/description "Supported languages"
                     :expansion/key :supported-langs
                     :expansion/name ::bread/value
                     :expansion/value [:be :de]}
                    {:expansion/key :lang-names
                     :expansion/name ::bread/value
                     :expansion/description "Language names for display"
                     :expansion/value {:be "Belarusian"
                                       :de "Deutsche"}}]}
      {}
      {:request-method :get
       :session {:user {:db/id 123}}}

      ;; Updating name.
      {:expansions
       [query-user expand-user]
       :effects
       [{:effect/name ::db/transact
         :effect/key nil
         :effect/description "Update account details"
         :txs [{:db/id 123 :user/name "Spongebob Squarepants"}]
         :conn db-conn}]
       :hooks
       {::bread/expand
        [success-hook]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"}
       :session {:user {:db/id 123}}}

      ;; Updating preferences.
      {:expansions [query-user expand-user]
       :effects [{:effect/name ::db/transact
                  :effect/key nil
                  :effect/description "Update account details"
                  :txs [{:db/id 123
                         :user/name "Spongebob Squarepants"
                         :user/preferences (pr-str {:pronouns "he/they"
                                                    :timezone "America/Los_Angeles"})}]
                  :conn db-conn}]
       :hooks {::bread/expand [success-hook]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :pronouns "he/they"
                :timezone "America/Los_Angeles"}
       :session {:user {:db/id 123}}}

      ;; Any custom fields on the account form are treated as preferences.
      {:expansions [query-user expand-user]
       :effects [{:effect/name ::db/transact
                  :effect/key nil
                  :effect/description "Update account details"
                  :txs [{:db/id 123
                         :user/name "Spongebob Squarepants"
                         :user/preferences (pr-str {:custom "something"
                                                    :other "something else"})}]
                  :conn db-conn}]
       :hooks {::bread/expand [success-hook]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :custom "something"
                :other "something else"}
       :session {:user {:db/id 123}}}

      ;; Updating password - mismatched passwords error.
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                :flash {:error-key :auth/passwords-must-match}
                                :to "/account"}]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password "xyz"
                :password-confirmation ""}
       :session {:user {:db/id 123}}}

      ;; Updating password - only confirmation submitted.
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                :flash {:error-key :auth/passwords-must-match}
                                :to "/account"}]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password ""
                :password-confirmation "xyz"}
       :session {:user {:db/id 123}}}

      ;; Updating password - both fields submitted, but still mismatched
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                :flash {:error-key :auth/passwords-must-match}
                                :to "/account"}]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password "abc"
                :password-confirmation "xyz"}
       :session {:user {:db/id 123}}}

      ;; Updating password - too short.
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                ;; 12 is the default from auth
                                :flash {:error-key [:auth/password-must-be-at-least 12]}
                                :to "/account"}]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password "tooshort"
                :password-confirmation "tooshort"}
       :session {:user {:db/id 123}}}

      ;; Updating password - too short with custom auth config.
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                ;; 12 is the default from auth
                                :flash {:error-key [:auth/password-must-be-at-least 8]}
                                :to "/account"}]}}
      {:auth-config {:min-password-length 8}}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password "2shrt"
                :password-confirmation "2shrt"}
       :session {:user {:db/id 123}}}

      ;; Updating password - just long enough with custom config.
      {:expansions [query-user expand-user]
       :effects [{:effect/name ::db/transact
                  :effect/description "Update account details"
                  :effect/key nil
                  :txs [{:db/id 123
                         :user/name "Spongebob Squarepants"
                         :user/password "[:bcrypt+blake2b-512+password]"}]
                  :conn db-conn}]
       :hooks {::bread/expand [success-hook]}}
      {:auth-config {:min-password-length 8}}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password "password"
                :password-confirmation "password"}
       :session {:user {:db/id 123}}}

      ;; Updating password - meets default 12-char minimum.
      {:expansions [query-user expand-user]
       :effects [{:effect/name ::db/transact
                  :effect/description "Update account details"
                  :effect/key nil
                  :txs [{:db/id 123
                         :user/name "Spongebob Squarepants"
                         :user/password "[:bcrypt+blake2b-512+password1234]"}]
                  :conn db-conn}]
       :hooks {::bread/expand [success-hook]}}
      {}
      {:request-method :post
       :params {:action "update"
                :name "Spongebob Squarepants"
                :password "password1234"
                :password-confirmation "password1234"}
       :session {:user {:db/id 123}}}

      ;; Updating password - meets default 12-char minimum.
      {:expansions [query-user expand-user]
       :effects [{:effect/name ::db/transact
                  :effect/description "Update account details"
                  :effect/key nil
                  :txs [{:db/id 123
                         :user/name "Spongebob Squarepants"
                         :user/password (str
                                          "[:bcrypt+blake2b-512+"
                                          "twelve_chars"
                                          "twelve_chars"
                                          "twelve_chars"
                                          "twelve_chars"
                                          "twelve_chars"
                                          "twelve_chars"
                                          "]")}]
                  :conn db-conn}]
       :hooks {::bread/expand [success-hook]}}
      {}
      (let [;; 6 * 12 = 72
            long-password (apply str (repeat 6 "twelve_chars"))]
        {:request-method :post
         :params {:action "update"
                  :name "Spongebob Squarepants"
                  :password long-password
                  :password-confirmation long-password}
         :session {:user {:db/id 123}}})

      ;; Updating password - too long!
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                ;; 12 is the default from auth
                                :flash {:error-key [:auth/password-must-be-at-most 72]}
                                :to "/account"}]}}
      {}
      (let [;; 1 + 6 * 12 = 73
            long-password (apply str "x" (repeat 6 "twelve_chars"))]
        {:request-method :post
         :params {:action "update"
                  :name "Spongebob Squarepants"
                  :password long-password
                  :password-confirmation long-password}
         :session {:user {:db/id 123}}})

      ;; Updating password - too long with custom auth config.
      {:hooks {::bread/expand [{:action/name ::ring/redirect
                                :action/description
                                "Redirect to account page after an error"
                                ;; 12 is the default from auth
                                :flash {:error-key [:auth/password-must-be-at-most 71]}
                                :to "/account"}]}}
      {:auth-config {:max-password-length 71}}
      (let [;; 6 * 12 = 72
            long-password (apply str (repeat 6 "twelve_chars"))]
        {:request-method :post
         :params {:action "update"
                  :name "Spongebob Squarepants"
                  :password long-password
                  :password-confirmation long-password}
         :session {:user {:db/id 123}}})

      ,)))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
