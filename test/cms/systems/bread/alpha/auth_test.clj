(ns systems.bread.alpha.auth-test
  (:require
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [clojure.test :refer [deftest are is testing]]
    [ring.middleware.session.store :as ss]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.cms.defaults :as defaults]
    [systems.bread.alpha.datastore :as store]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.test-helpers :refer [plugins->loaded
                                              use-datastore]])
  (:import
    [java.util UUID]))

(def angela
  {:user/username "angela"
   :user/name "Angela Y. Davis"
   :user/failed-login-count 0
   :user/lang :en-US})

(def bobby
  {:user/username "bobby"
   :user/name "Bobby Seale"
   :user/failed-login-count 0
   :user/lang :en-US})

(def crenshaw
  {:user/username "crenshaw"
   :user/name "Kimberly Crenshaw"
   :user/failed-login-count 0
   :user/lang :en-US})

(def douglass
  {:user/username "douglass"
   :user/name "Frederick Douglass"
   :user/failed-login-count 0
   :user/lang :en-US
   :user/two-factor-key "fake"})

(def config
  {:datastore/type :datahike
   :store {:id "authdb" :backend :mem}
   :recreate? true
   :force? true
   :datastore/initial-txns
   [(assoc angela :user/password (hashers/derive "abolition4lyfe"))
    (assoc bobby :user/password (hashers/derive "pantherz"))
    ;; NOTE: you wouldn't normally mix and match hashing algorithms like this.
    ;; This is just a way to test configuring :auth/hash-algorithm.
    (assoc crenshaw :user/password (hashers/derive "intersectionz"
                                                   {:alg :argon2id}))
    (assoc douglass :user/password (hashers/derive "liber4tion"))]})

(use-datastore :each config)

(defmethod bread/action ::route
  [req _ _]
  (assoc req ::bread/dispatcher {:dispatcher/type ::auth/login}))

(defn fake-2fa-validator
  ([^String _ ^long code]
   (= 123456 code))
  ([^String _ ^long code ^long t]
   (= 123456 code)))

(deftest test-authentication-flow
  (let [route-plugin
        {:hooks
         {::bread/route
          [{:action/name ::route
            :action/description
            "Return a hard-coded dispatcher for testing purposes"}]}}
        app-config {:datastore config
                    :plugins [route-plugin]
                    :cache false
                    :navigation false
                    :routes false
                    :i18n false
                    :renderer false}
        ->handler (fn [auth-config]
                    (-> app-config
                        (assoc :auth auth-config)
                        defaults/app
                        bread/load-app
                        bread/handler))
        ;; What matters is a combination of view data, session, headers, and
        ;; status; so get that stuff to evaluate against.
        ->auth-data (fn [{::bread/keys [data] :keys [headers status session]}]
                      {::bread/data (select-keys data [:session
                                                       :auth/result])
                       :session session
                       :headers headers
                       :status status})]
    (are
      [expected args]
      (= expected (with-redefs [totp/valid-code? fake-2fa-validator]
                    (let [[auth-config req] args
                          handler (->handler auth-config)
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
      [nil {:request-method :get}]

      {:status 200
       :headers {"content-type" "text/html"}
       :session nil
       ::bread/data {:session nil}}
      [{} {:request-method :get}]

      ;; POST with no data
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      [{} {:request-method :post}]

      ;; POST with missing password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      [{} {:request-method :post
           :params {:username "no one"}}]

      ;; POST with missing password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      [{} {:request-method :post
           :params {:username "no one" :password nil}}]

      ;; POST with bad username AND password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user nil}}}
      [{} {:request-method :post
           :params {:username "no one" :password "nothing"}}]

      ;; POST with bad password
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session {:user nil}
                     :auth/result {:update false :valid false :user angela}}}
      [{} {:request-method :post
           :params {:username "angela" :password "wrongpassword"}}]

      ;; POST with correct password
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user angela
                 :auth/step :logged-in}
       ::bread/data {:session {:user angela
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user angela}}}
      [{} {:request-method :post
           :params {:username "angela" :password "abolition4lyfe"}}]

      ;; POST with correct password
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user bobby
                 :auth/step :logged-in}
       ::bread/data {:session {:user bobby
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user bobby}}}
      [{} {:request-method :post
           :params {:username "bobby" :password "pantherz"}}]

      ;; POST with correct password; custom hash algo
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user crenshaw
                 :auth/step :logged-in}
       ::bread/data {:session {:user crenshaw
                               :auth/step :logged-in}
                     :auth/result {:update false :valid true :user crenshaw}}}
      [{:auth/hash-algorithm :argon2id}
       {:request-method :post
        :params {:username "crenshaw" :password "intersectionz"}}]

      ;; Successful username/password login requiring 2FA step
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:update false :valid true :user douglass}}}
      [{}
       {:request-method :post
        :params {:username "douglass" :password "liber4tion"}}]

      ;; 2FA with blank code
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:valid false :user douglass}}}
      [{}
       {:request-method :post
        :session {:user douglass
                  :auth/step :two-factor}
        :params {:two-factor-code ""}}]

      ;; 2FA with invalid code
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:valid false :user douglass}}}
      [{}
       {:request-method :post
        :session {:user douglass
                  :auth/step :two-factor}
        :params {:two-factor-code "wpeovwoeginawge"}}]

      ;; Unsuccessful 2FA
      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user douglass
                 :auth/step :two-factor}
       ::bread/data {:session {:user douglass
                               :auth/step :two-factor}
                     :auth/result {:valid false :user douglass}}}
      [{}
       {:request-method :post
        :session {:user douglass
                  :auth/step :two-factor}
        :params {:two-factor-code "654321"}}]

      ;; Successful 2FA
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user douglass
                 :auth/step :logged-in}
       ::bread/data {:session {:user douglass
                               :auth/step :logged-in}
                     :auth/result {:valid true :user douglass}}}
      [{}
       {:request-method :post
        :session {:user douglass
                  :auth/step :two-factor}
        :params {:two-factor-code "123456"}}]

      ;; Logout
      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session nil
       ::bread/data {:session nil}}
      [{}
       {:request-method :post
        :session {:user douglass
                  :auth/step :logged-in}
        :params {:submit "logout"}}]

      ;;
      )))

(deftest test-log-attempt
  (let [app (plugins->loaded [(store/plugin config)])
        conn (store/connection app)
        get-user (fn [username]
                   (store/q @conn
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
                    (bread/hook app ::bread/effects!)
                    (-> result :user :user/username get-user)))

      nil [{:conn conn :max-failed-login-count 5}
           {:user nil :valid nil}]

      ;; valid, 2FA step -> no action
      ;; valid, :logged-in -> reset count

      ;; locked

      ;; max failed logins
      {:user/username "angela"
       :user/failed-login-count 0
       :user/locked-at (java.util.Date.)}
      [;; effect
       {:conn conn :max-failed-login-count 5}
       ;; :auth/result
       {:valid false
        :user {:user/username "angela"
               :user/failed-login-count 5}}]

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
  (let [app (plugins->loaded [(store/plugin config)])
        conn (store/connection app)
        session-store (auth/session-store conn)
        get-session-data (fn [sk]
                           (store/q @conn
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

  (def app (plugins->loaded [(store/plugin config)]))
  (def $conn (store/connection app))
  (def $store (auth/session-store $conn))
  (def $uuid (UUID/randomUUID))
  (satisfies? ss/SessionStore $store)
  (ss/write-session $store $uuid {:a :b})
  (datalog/attrs @$conn)
  (store/q @$conn '{:find [(pull ?e [*]) .]
                    :in [$ ?sk]
                    :where [[?e :session/data]]})

  (require '[kaocha.repl :as k])
  (k/run))
