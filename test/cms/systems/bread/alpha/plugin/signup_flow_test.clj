(ns systems.bread.alpha.plugin.signup-test
  (:require
    [buddy.hashers :as hashers]
    [clojure.test :refer [deftest are]]

    [systems.bread.alpha.test-helpers :refer [naive-router
                                              plugins->loaded
                                              use-db]]
    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
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
                      :invitation/code (sha-512 (str #_AUTH-SECRET-KEY #_":" "qwerty"))}]
   :db/config {:store {:backend :mem :id "signup-test-db"}}})

(use-db :each db-config)

(defn- ->signup-data [{:keys [::bread/data headers status body]}]
  {::bread/data (select-keys data [:validation :not-found?])
   :headers headers
   :status status
   :body body})

(defmethod bread/action ::route
  [req _ _]
  (assoc req ::bread/dispatcher {:dispatcher/type ::signup/signup=>
                                 :dispatcher/key :invitation}))

(defmethod bread/action ::body
  [res {:keys [body]} _]
  (if (:body res) res (assoc res :body body)))

(defn- config->handler [{:keys [signup-config auth-config test/body]}]
  (-> (conj (defaults/plugins {:db db-config
                               :routes false})
            (route/plugin {:router (naive-router)})
            {:hooks
             {::bread/route
              [{:action/name ::route
                :action/description "Hard-code the dispatcher."}]
              ::bread/expand
              [{:action/name ::body :body body}]}}
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
    {:body [:p "Signup page"]
     :headers {"content-type" "text/html"}
     :status 200
     ::bread/data {:not-found? false}}
    {:auth-config {:secret-key "SECRET"}
     :test/body [:p "Signup page"]}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "qwerty"}}

    ;; Loading the signup page with a bad code.
    {:body [:p "Signup page"]
     :headers {"content-type" "text/html"}
     :status 404
     ::bread/data {:not-found? true}}
    {:test/body [:p "Signup page"]}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "invalid"}}

    ;; Loading the signup page after changing :auth/secret-key.
    #_#_#_
    {:body [:p "Signup page"]
     :headers {"content-type" "text/html"}
     :status 404
     ::bread/data {:not-found? true}}
    {:auth-config {:secret-key "updated!"}
     :test/body [:p "Signup page"]}
    {:request-method :get
     :uri "/_/signup"
     :params {:code "invalid"}}

    ,))

(comment
  (require '[kaocha.repl :as k])
  (k/run {:color? false}))
