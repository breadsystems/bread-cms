(ns systems.bread.alpha.auth-test
  (:require
    [buddy.hashers :as hashers]
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
   :user/lang :en-US})

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
    (assoc douglass
           :user/password (hashers/derive "liber4tion")
           :user/two-factor-key "fake")]})

(use-datastore :each config)

(defmethod bread/action ::route
  [req _ _]
  (assoc req ::bread/dispatcher {:dispatcher/type ::auth/login}))

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
                                                       :auth/result
                                                       :auth/user])
                       :session session
                       :headers headers
                       :status status})]
    (are
      [expected args]
      (= expected (let [[auth-config req] args
                        handler (->handler auth-config)
                        data (-> req handler ->auth-data)]
                    (if (get-in data [::bread/data :auth/user])
                      (-> data
                          ;; TODO yikes
                          (update-in [:session :user] dissoc :db/id)
                          (update-in [::bread/data :auth/user] dissoc :db/id)
                          (update-in [::bread/data :auth/result :user] dissoc :db/id))
                      data)))

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

      {:status 401
       :headers {"content-type" "text/html"}
       :session {}
       ::bread/data {:session nil
                     :auth/result {:update false :valid false}
                     :auth/user nil}}
      [{} {:request-method :post}]

      {:status 401
       :headers {"content-type" "text/html"}
       :session {}
       ::bread/data {:session nil
                     :auth/result {:update false :valid false}
                     :auth/user nil}}
      [{} {:request-method :post
           :params {:username "no one"}}]

      {:status 401
       :headers {"content-type" "text/html"}
       :session {}
       ::bread/data {:session nil
                     :auth/result {:update false :valid false}
                     :auth/user nil}}
      [{} {:request-method :post
           :params {:username "no one" :password nil}}]

      {:status 401
       :headers {"content-type" "text/html"}
       :session {}
       ::bread/data {:session nil
                     :auth/result {:update false :valid false}
                     :auth/user nil}}
      [{} {:request-method :post
           :params {:username "no one" :password "nothing"}}]

      {:status 401
       :headers {"content-type" "text/html"}
       :session {:user nil}
       ::bread/data {:session nil
                     :auth/result {:update false :valid false :user nil}
                     :auth/user angela}}
      [{} {:request-method :post
           :params {:username "angela" :password "wrongpassword"}}]

      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user angela
                 :auth/step :logged-in}
       ::bread/data {:session nil
                     :auth/result {:update false :valid true :user angela}
                     :auth/user angela}}
      [{} {:request-method :post
           :params {:username "angela" :password "abolition4lyfe"}}]

      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user bobby
                 :auth/step :logged-in}
       ::bread/data {:session nil
                     :auth/result {:update false :valid true :user bobby}
                     :auth/user bobby}}
      [{} {:request-method :post
           :params {:username "bobby" :password "pantherz"}}]

      {:status 302
       :headers {"Location" "/login"
                 "content-type" "text/html"}
       :session {:user crenshaw
                 :auth/step :logged-in}
       ::bread/data {:session nil
                     :auth/result {:update false :valid true :user crenshaw}
                     :auth/user crenshaw}}
      [{:auth/hash-algorithm :argon2id}
       {:request-method :post
        :params {:username "crenshaw" :password "intersectionz"}}]

      ;;
      )))

(comment
  (require '[datahike.api :as d])
  (d/delete-database config)

  (require '[kaocha.repl :as k])
  (k/run))
