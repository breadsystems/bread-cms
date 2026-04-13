(ns systems.bread.alpha.plugin.signup-test
  (:require
    [clojure.test :refer [deftest are is]]

    [systems.bread.alpha.test-helpers :refer [naive-router
                                              plugins->loaded
                                              use-db]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.internal.interop :refer [sha-512]]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.plugin.invitations :as invitations]
    [systems.bread.alpha.plugin.signup :as signup]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.schema :as schema])
  (:import
    [java.util Date]))

(def AUTH-SECRET-KEY "secret!")

(def db-config
  {:db/type :datahike
   :db/migrations (conj schema/initial auth/schema invitations/schema)
   :db/initial-txns [{:thing/created-at (t/now)
                      :thing/updated-at (t/now)
                      :invitation/code (sha-512 (str AUTH-SECRET-KEY ":" "qwerty"))}
                     {:thing/created-at (t/now)
                      :thing/updated-at (t/now)
                      :invitation/code (sha-512 (str AUTH-SECRET-KEY ":" "validcode"))}
                     {:user/username "existing"}]
   :db/config {:store {:backend :mem :id "signup-test-db"}}})

(use-db :each db-config)

(defn- ->signup-data [{:keys [::bread/data headers status]}]
  {::bread/data (select-keys data [:validation :not-found?])
   :headers headers
   :status status})

(defmethod bread/action ::route
  [req _ _]
  (assoc req ::bread/dispatcher {:dispatcher/type ::signup/signup=>
                                 :dispatcher/key :invitation}))

(defmethod bread/action ::body
  [res {:keys [body]} _]
  (if (:body res) res (assoc res :body body)))

(defn- config->handler [{:keys [signup-config auth-config]}]
  (-> (conj (defaults/plugins {:db db-config
                               :routes false})
            (route/plugin {:router (naive-router)})
            {:hooks
             {::bread/route
              [{:action/name ::route
                :action/description "Hard-code the dispatcher."}]}}
            (auth/plugin auth-config)
            (signup/plugin signup-config))
      plugins->loaded
      bread/handler))

(deftest test-signup-flow
  (are
    [expected config req]
    (= expected (let [handler (config->handler config)]
                  (-> req handler ->signup-data)))

    ;; Just loading the signup page.
    {:headers {"content-type" "text/html"}
     :status 200
     ::bread/data {:not-found? false}}
    {:auth-config {:secret-key AUTH-SECRET-KEY}}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "qwerty"}}

    ;; Loading the signup page with a bad code.
    {:headers {"content-type" "text/html"}
     :status 404
     ::bread/data {:not-found? true}}
    {:auth-config {:secret-key AUTH-SECRET-KEY}}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "invalid"}}

    ;; Loading the signup page after changing :auth/secret-key,
    ;; using a previously valid code.
    {:headers {"content-type" "text/html"}
     :status 404
     ::bread/data {:not-found? true}}
    {:auth-config {:secret-key "UPDATED!"}}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "qwerty"}}

    ;; Invalid signup.
    {:headers {"content-type" "text/html"}
     :status 404 ;; TODO 400 ?
     ::bread/data {:not-found? true
                   :validation [false :signup/all-fields-required]}}
    {:auth-config {:secret-key "UPDATED!"}}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"}}

    ;; Invalid signup.
    {:headers {"content-type" "text/html"}
     :status 404 ;; TODO 400 ?
     ::bread/data {:not-found? true
                   :validation [false :signup/all-fields-required]}}
    {:auth-config {:secret-key "UPDATED!"}}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"
              :username "test"
              :password ""
              :password-confirmation ""}}

    ;; Violating minimum password length requirement.
    {:headers {"content-type" "text/html"}
     :status 404 ;; TODO 400 ?
     ::bread/data {:not-found? true
                   :validation [false [:auth/password-must-be-at-least 12]]}}
    {:auth-config {:secret-key "UPDATED!"}}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"
              :username "test"
              :password "asdf"
              :password-confirmation "asdf"}}

    ;; Password mismatch.
    {:headers {"content-type" "text/html"}
     :status 404 ;; TODO 400 ?
     ::bread/data {:not-found? true
                   :validation [false :auth/passwords-must-match]}}
    {:auth-config {:secret-key "UPDATED!"}}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"
              :username "test"
              :password "nope"
              :password-confirmation "asdf"}}

    ;; Existing username.
    {:headers {"content-type" "text/html"}
     :status 404 ;; TODO 400 ?
     ::bread/data {:not-found? true
                   :validation [false :signup/username-exists]}}
    {:auth-config {:secret-key "UPDATED!"}}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"
              :username "existing"
              :password "password1234"
              :password-confirmation "password1234"}}

    ;; Successful signup.
    {:headers {"Location" "/login"
               "content-type" "text/html"}
     :status 302
     ::bread/data {:not-found? false
                   :validation [true nil]}}
    {:auth-config {:secret-key AUTH-SECRET-KEY}}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"
              :username "test"
              :password "password1234"
              :password-confirmation "password1234"}}

    ,))

(deftest test-signup-flow-custom-config
  (are
    [expected req]
    (= expected (let [handler (config->handler
                                {:auth-config {:secret-key AUTH-SECRET-KEY
                                               :login-uri "/custom"}})]
                  (-> req handler (select-keys [:headers :status]))))

    ;; Just loading the signup page.
    {:headers {"content-type" "text/html"}
     :status 200}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "qwerty"}}

    ;; Successful signup.
    {:headers {"Location" "/custom"
               "content-type" "text/html"}
     :status 302}
    {:request-method :post
     :uri "/_/signup"
     :params {:code "qwerty"
              :username "test"
              :password "password1234"
              :password-confirmation "password1234"}}

    ,))

(deftest test-signup-flow-successful-signup
  (let [handler (config->handler {:auth-config {:secret-key AUTH-SECRET-KEY}})
        signup {:request-method :post
                :uri "/_/signup"
                :params {:code "qwerty"
                         :username "test"
                         :password "password1234"
                         :password-confirmation "password1234"}}]
    ;; Complete successful signup
    (-> signup handler)
    (are
      [expected req]
      (= expected
         (-> req
             handler
             ->signup-data
             (select-keys [:status ::bread/data])))

      ;; Code is redeemed.
      {:status 404
       ::bread/data {:not-found? true}}
      {:request-method :get
       :uri "/_/signup"
       :params {:code "qwerty"}}

      ;; New username exists.
      {:status 200 ;; TODO 400 ?
       ::bread/data {:not-found? false
                     :validation [false :signup/username-exists]}}
      {:request-method :post
       :uri "/_/signup"
       :params {:code "validcode"
                :username "test"
                :password "newpassword123"
                :password-confirmation "newpassword123"}}

      ,))

  ,)

(comment
  (require '[kaocha.repl :as k])
  (k/run #'test-signup-flow-successful-signup {:color? false})
  (k/run {:color? false}))
