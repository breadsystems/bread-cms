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
                                                   {:alg :argon2id}))]})

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
                        bread/handler))]
    (are
      [expected args]
      (= expected (let [[auth-config req] args
                        handler (->handler auth-config)
                        data (-> (handler req)
                                 ::bread/data
                                 (select-keys [:headers
                                               :session
                                               :auth/result
                                               :auth/user]))]
                    (if (:auth/user data)
                      (-> data
                          (update :auth/user dissoc :db/id)
                          (update-in [:auth/result :user] dissoc :db/id))
                      data)))

      {:session nil}
      [nil {:request-method :get}]

      {:session nil}
      [{} {:request-method :get}]

      {:session nil
       :auth/result {:update false :valid false}
       :auth/user nil}
      [{} {:request-method :post}]

      {:session nil
       :auth/result {:update false :valid false}
       :auth/user nil}
      [{} {:request-method :post
           :params {:username "no one"}}]

      {:session nil
       :auth/result {:update false :valid false}
       :auth/user nil}
      [{} {:request-method :post
           :params {:username "no one" :password nil}}]

      {:session nil
       :auth/result {:update false :valid false}
       :auth/user nil}
      [{} {:request-method :post
           :params {:username "no one" :password "nothing"}}]

      {:session nil
       :auth/result {:update false :valid false :user nil}
       :auth/user angela}
      [{} {:request-method :post
           :params {:username "angela" :password "wrongpassword"}}]

      {:session nil
       :auth/result {:update false :valid true :user angela}
       :auth/user angela}
      [{} {:request-method :post
           :params {:username "angela" :password "abolition4lyfe"}}]

      {:session nil
       :auth/result {:update false :valid true :user bobby}
       :auth/user bobby}
      [{} {:request-method :post
           :params {:username "bobby" :password "pantherz"}}]

      {:session nil
       :auth/result {:update false :valid true :user crenshaw}
       :auth/user crenshaw}
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
