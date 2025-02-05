(ns systems.bread.alpha.auth-test
  (:require
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [clojure.test :refer [deftest are is testing]]
    [ring.middleware.session.store :as ss]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.defaults :as defaults]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.internal.time :as t]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.test-helpers :refer [naive-router
                                              plugins->loaded
                                              plugins->handler
                                              use-db]])
  (:import
    [java.util Date UUID]))

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
   :user/two-factor-key "fake"})

(def config
  {:db/type :datahike
   :store {:id "authdb" :backend :mem}
   :recreate? true
   :force? true
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
  (assoc req ::bread/dispatcher {:dispatcher/type ::auth/login}))

(defn fake-2fa-validator
  ([^String _ ^long code]
   (= 123456 code))
  ([^String _ ^long code ^long t]
   (= 123456 code)))

(deftest test-authentication-flow
  (let [->handler (fn [auth-config]
                    (plugins->handler
                      (conj
                        (defaults/plugins {:db config
                                           :routes false})
                        (route/plugin {:router (naive-router)})
                        {:hooks
                         {::bread/route
                          [{:action/name ::route
                            :action/description "Hard-code the dispatcher."}]}}
                        (auth/plugin auth-config))))
        ;; What matters is a combination of view data, session, headers, and
        ;; status; so get that stuff to evaluate against.
        ->auth-data (fn [{::bread/keys [data] :keys [headers status session]}]
                      {::bread/data (select-keys data [:session
                                                       :auth/result])
                       :session session
                       :headers headers
                       :status status})]
    (are
      [expected auth-config req]
      (= expected (with-redefs [totp/valid-code? fake-2fa-validator]
                    (let [handler (->handler auth-config)
                          data (-> req handler ->auth-data)]
                      (if (get-in data [::bread/data :auth/result])
                        (-> data
                            (update-in [:session :user] dissoc :db/id)
                            (update-in [::bread/data :session :user] dissoc :db/id)
                            (update-in [::bread/data :auth/result :user] dissoc :db/id))
                        data))))

      ;; Requesting the login page.
      {:status 200
       :headers {"content-type" "text/html"}
       :session nil
       ::bread/data {:session nil}}
      nil
      {:request-method :get}

      {:status 200
       :headers {"content-type" "text/html"}
       :session nil
       ::bread/data {:session nil}}
      {}
      {:request-method :get}

      ;; POST with no data
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      {}
      {:request-method :post}

      ;; POST with missing password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      {}
      {:request-method :post
       :params {:username "no one"}}

      ;; POST with missing password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      {}
      {:request-method :post
       :params {:username "no one" :password nil}}

      ;; POST with bad username AND password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      {}
      {:request-method :post
       :params {:username "no one" :password "nothing"}}

      ;; POST with bad password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user angela}}}
      {}
      {:request-method :post
       :params {:username "angela" :password "wrongpassword"}}

      ;; POST with correct password
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user angela
                 :auth/step :logged-in}
       ::bread/data {:session {:user angela
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user angela}}}
      {}
      {:request-method :post
       :params {:username "angela" :password "abolition4lyfe"}}

      ;; POST with correct password
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user bobby
                 :auth/step :logged-in}
       ::bread/data {:session {:user bobby
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user bobby}}}
      {}
      {:request-method :post
       :params {:username "bobby" :password "pantherz"}}

      ;; POST with correct password & redirect
      {:status 302
       :headers {"Location" "/destination"
                 "content-type" "text/html"}
       :session {:user bobby
                 :auth/step :logged-in}
       ::bread/data {:session {:user bobby
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user bobby}}}
      {}
      {:request-method :post
       :params {:username "bobby" :password "pantherz" :next "/destination"}}

      ;; POST with correct password & redirect, with custom :next-param
      {:status 302
       :headers {"Location" "/destination"
                 "content-type" "text/html"}
       :session {:user bobby
                 :auth/step :logged-in}
       ::bread/data {:session {:user bobby
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user bobby}}}
      {:next-param :special}
      {:request-method :post
       :params {:username "bobby" :password "pantherz" :special "/destination"}}

      ;; POST with correct password; custom hash algo
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user crenshaw
                 :auth/step :logged-in}
       ::bread/data {:session {:user crenshaw
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user crenshaw}}}
      {:auth/hash-algorithm :argon2id}
      {:request-method :post
       :params {:username "crenshaw" :password "intersectionz"}}

      ;; Successful username/password login requiring 2FA step
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:update false :valid true :user douglass}}}
      {}
      {:request-method :post
       :params {:username "douglass" :password "liber4tion"}}

      ;; 2FA with blank code
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:valid false :user douglass}}}
      {}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code ""}}

      ;; 2FA with invalid code
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:valid false :user douglass}}}
      {}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code "wpeovwoeginawge"}}

      ;; Unsuccessful 2FA
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:valid false :user douglass}}}
      {}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code "654321"}}

      ;; Successful 2FA
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :logged-in}
       ::bread/data {:session {:user douglass
                               :auth/step :logged-in}
                     :auth/result {:valid true :user douglass}}}
      {}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code "123456"}}

      ;; Successful 2FA with custom :login-uri
      {:status 302
       :headers {"Location" "/custom"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :logged-in}
       ::bread/data {:session {:user douglass
                               :auth/step :logged-in}
                     :auth/result {:valid true :user douglass}}}
      {:login-uri "/custom"}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code "123456"}}

      ;; Successful 2FA with redirect
      {:status 302
       :headers {"Location" "/destination"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :logged-in}
       ::bread/data {:session {:user douglass
                               :auth/step :logged-in}
                     :auth/result {:valid true :user douglass}}}
      {}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code "123456"
                :next "/destination"}}

      ;; Successful 2FA with redirect & custom :next-param
      {:status 302
       :headers {"Location" "/destination"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :logged-in}
       ::bread/data {:session {:user douglass
                               :auth/step :logged-in}
                     :auth/result {:valid true :user douglass}}}
      {:next-param :special}
      {:request-method :post
       :session {:user douglass
                 :auth/step :two-factor}
       :params {:two-factor-code "123456"
                :special "/destination"}}

      ;; Logout
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session nil
       ::bread/data {:session nil}}
      {}
      {:request-method :post
       :session {:user douglass
                 :auth/step :logged-in}
       :params {:submit "logout"}}

      ;;
      )))

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
        session-store (auth/session-store conn)
        get-session-data (fn [sk]
                           (db/q @conn
                                 '{:find [?data .]
                                   :in [$ ?sk]
                                   :where [[?e :session/uuid ?sk]
                                           [?e :session/data ?data]]}
                                 sk))]

    (testing "write-session"
      (testing "passing a UUID"
        (let [sk (ss/write-session session-store (UUID/randomUUID) {:a :b})]
          (is (uuid? sk))
          (is (= "{:a :b}" (get-session-data sk)))))

      (testing "passing a UUID-formatted string"
        (let [uuid (UUID/randomUUID)
              sk (ss/write-session session-store (str uuid) {:a :b})]
          (is (uuid? sk))
          (is (= "{:a :b}" (get-session-data sk)))))

      (testing "passing nil session key"
        (let [sk (ss/write-session session-store nil {:a :b})]
          (is (uuid? sk))
          (is (= "{:a :b}" (get-session-data sk))))))

    (testing "read-session"
      (let [sk (ss/write-session session-store nil {:a :b})]
        (is (= {:a :b} (ss/read-session session-store sk)))
        (is (= {:a :b} (ss/read-session session-store (str sk))))))

    (testing "delete-session"
      (testing "passing a UUID"
        (let [sk (ss/write-session session-store nil {:a :b})]
          (ss/delete-session session-store sk)
          (is (nil? (get-session-data sk)))
          (is (nil? (ss/read-session session-store sk)))))

      (testing "passing a UUID-formatted string"
        (let [sk (ss/write-session session-store nil {:a :b})]
          (ss/delete-session session-store (str sk))
          (is (nil? (get-session-data sk)))
          (is (nil? (ss/read-session session-store sk))))))

    ;;
    ))

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
  (k/run {:color? false}))
