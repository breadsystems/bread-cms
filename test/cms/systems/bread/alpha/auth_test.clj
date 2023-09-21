(ns systems.bread.alpha.auth-test
  (:require
    [buddy.hashers :as hashers]
    [clj-totp.core :as totp]
    [clojure.test :refer [deftest are]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.cms.defaults :as defaults]
    [systems.bread.alpha.plugin.auth :as auth]
    [systems.bread.alpha.test-helpers :refer [use-datastore]]))

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

(comment
  (require '[datahike.api :as d])
  (d/delete-database config)

  (require '[kaocha.repl :as k])
  (k/run))
