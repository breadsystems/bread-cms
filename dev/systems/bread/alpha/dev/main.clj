(ns systems.bread.alpha.dev.main
  (:require
    [aero.core :as aero]
    [buddy.hashers :as hashers]
    [integrant.core :as ig]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.cms.main :as main]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.schema :as schema]
    [systems.bread.alpha.dev.data :as data]
    [systems.bread.alpha.tools.util])
  (:gen-class))

(defmethod aero/reader 'buddy/derive [_ _ [pw algo]]
  (hashers/derive pw (when algo {:alg algo})))

(defmethod ig/init-key :bread/db
  [_ {:as db-spec :db/keys [recreate? config initial-txns]}]
  (log/info "initializing :bread/db with config:" config)
  (db/create! db-spec)
  (let [initial (when recreate? (concat (data/initial) initial-txns))]
    (assoc db-spec
           :db/initial-txns initial
           :db/migrations schema/initial)))

(comment
  (require '[flow-storm.api :as flow])
  (flow/local-connect)

  (main/restart! (-> "dev/main.edn" aero/read-config))
  (main/restart! (-> "dev/minimal.edn" aero/read-config
                (assoc-in [:http :port] 1333)))
  (main/stop!)
  (deref main/system)
  (:http @main/system)
  (:ring/wrap-defaults @main/system)
  (:ring/session-store @main/system)
  (-> @main/system :initial-config :ring/session-store :secret-key)
  (:bread/app @main/system)
  (:bread/routes @main/system)
  (:bread/router @main/system)
  (:bread/db @main/system)
  (:bread/profilers @main/system)

  (set! *print-namespace-maps* false)

  (require '[systems.bread.alpha.database :as db])

  (db/exists? (-> "dev/main.edn" aero/read-config :bread/db))
  (deref (db/connect (-> "dev/main.edn" aero/read-config :bread/db)))

  (= (db/connection (:bread/app @main/system))
     (db/connect (:bread/db @main/system)))
  ;; => true

  ;; Connection pool...
  (db/connection (:bread/app @main/system))

  ;; EMAIL
  (:email (:bread/app (:initial-config @main/system)))
  (email/config->postal (::bread/config (:bread/app @main/system)))
  (:email/smtp-from-email (::bread/config (:bread/app @main/system)))

  (require '[postal.core :as postal])
  (def $postal-config {:host (System/getenv "BREAD_SMTP_HOST")
                       :port (Integer. (System/getenv "BREAD_SMTP_PORT"))
                       :user (System/getenv "BREAD_SMTP_USERNAME")
                       :pass (System/getenv "BREAD_SMTP_PASSWORD")
                       :tls true})
  (def fut (future
             (postal/send-message $postal-config
                                  {:from (System/getenv "BREAD_SMTP_FROM_EMAIL")
                                   :to ["coby@tamayo.email"]
                                   :subject "Postal test"
                                   :body "Testing from Clojure Postal"})))
  (deref fut 60000 :timeout)

  (alter-var-root #'bread/*enable-profiling* not)

  (def $req {:uri "/en" :request-method :get})
  (def $req {:uri "/en/hello" :request-method :get})
  (def $req {:uri "/en/hello/child-page" :request-method :get})
  (def $req {:uri "/en/tag/one" :request-method :get})
  (def $req {:uri "/fr/tag/one" :request-method :get})
  (def $req {:uri "/en/404" :request-method :get})

  (do
    (def $req {:uri "/~/signup" :request-method :get})
    (def ->app (partial util/->app (:bread/app @main/system)))
    (def diagnose-expansions (partial util/diagnose-expansions (:bread/app @main/system)))

    (defn db []
      (db/database (->app $req)))
    (deref (db/connect (:bread/db @main/system)))
    (db/database (:bread/app @main/system))

    (defn q [& args]
      (apply
        db/q
        (db/database (->app $req))
        args)))

  (diagnose-expansions (->app $req))
  (util/do-expansions (->app $req) 1)
  (util/do-expansions (->app $req) 2)
  (util/do-expansions (->app $req) 3)

  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (::bread/dispatcher $))
  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (::bread/expansions $))
  (as-> (->app  $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (::bread/data $))
  (as-> (->app $req) $
    (bread/hook $ ::bread/route)
    (bread/hook $ ::bread/dispatch)
    (bread/hook $ ::bread/expand)
    (bread/hook $ ::bread/render)
    (select-keys $ [:status :body :headers]))

  (bread/config (->app $req) :i18n/supported-langs)

  ;; TODO Nice debug mechanism:
  (catch-as-> (->app $req)
              [::bread/route ::bread/dispatcher]
              [::bread/dispatch ::bread/expansions]
              [::bread/expand ::bread/data (diagnose-expansions $)]
              [::bread/render (select-keys $ [:status :body :headers])])

  ;; querying for inverse relationships (post <-> taxon):
  (q '{:find [(pull ?t [:db/id {:post/_taxons [*]}])]
       :in [$ ?slug]
       :where [[?t :taxon/taxonomy :taxon.taxonomy/tag]
               [?t :thing/slug ?slug]]}
     "one")
  (q '{:find [(pull ?p [:db/id {:post/taxons [*]}])]
       :in [$ ?slug]
       :where [[?p :post/type :page]
               [?p :thing/slug ?slug]]}
     "hello")

  ;; Menu expansions

  (q '{:find [(pull ?e [:db/id
                        :taxon/taxonomy
                        :thing/slug
                        {:thing/_children [:thing/slug
                                           {:thing/_children ...}]}
                        {:thing/children ...}
                        {:thing/fields [*]}])]
       :in [$ ?taxonomy]
       :where [[?e :taxon/taxonomy ?taxonomy]]}
     :taxon.taxonomy/tag)

  (q '{:find [(pull ?e [;; Post menus don't store their own data in the db:
                        ;; instead, they follow the post hierarchy itself.
                        :db/id
                        :post/type
                        :post/status
                        {:thing/fields [*]}
                        {:thing/_children [:thing/slug {:thing/_children ...}]}
                        {:thing/children ...}])]
       :in [$ ?type [?status ...]]
       :where [[?e :post/type ?type]
               [?e :post/status ?status]
               (not-join [?e] [?_ :thing/children ?e])]}
     :page
     #{:post.status/published})

  (slurp (io/resource "public/assets/hi.txt"))

  (response ((:bread/handler @main/system) {:uri "/en"}))
  (response ((:bread/handler @main/system) {:uri "/en/hello"}))
  (response ((:bread/handler @main/system) {:uri "/en/hello/child-page"}))
  ;; This should 404:
  (response ((:bread/handler @main/system) {:uri "/en/child-page"}))

  (response ((:bread/handler @main/system) {:uri "/login"}))
  (response ((:bread/handler @main/system) {:uri "/login"
                                            :request-method :post
                                            :params {:username "coby"
                                                     :password "hello"}}))



  ;; MEDIA
  (q '{:find [(pull ?e [:db/id :thing/slug {:thing/fields [*]}])]
       :in [$]
       :where [[?e :post/type :media]
               [?e :post/type :media]
               [?e :post/status :post.status/published]
               [?e :post/status :post.status/published]]})

  ;; AUTH

  (->> (q '{:find [(pull ?e [:db/id
                            :thing/created-at
                            :thing/updated-at
                            :session/id
                            :session/data
                            {:user/_sessions
                             [:db/id :user/username]}])]
           :in [$]
           :where [[?e :session/id]]})
      (map (comp #(update % :session/data edn/read-string) first)))

  (def $user
    (-> (q '{:find [(pull ?e [:db/id
                              :thing/created-at
                              :user/username
                              :user/totp-key
                              :user/name
                              {:user/emails [*]}
                              :user/preferences
                              :user/failed-login-count
                              {:user/roles
                               [:role/key
                                {:role/abilities [:ability/key]}]}
                              {:invitation/_redeemer
                               [:db/id
                                :invitation/code
                                {:invitation/invited-by
                                 [:db/id :user/username]}]}
                              {:user/sessions [*]}]) .]
             :in [$ ?username]
             :where [[?e :user/username ?username]]}
           "bread")
        (update :user/sessions (fn [sessions]
                                 (map #(update % :session/data edn/read-string)
                                      sessions)))))

  (q '{:find [(pull ?e [:db/id *])]
       :where [[?e :invitation/code]]})
  (user/can? $user :edit-posts)
  (defn retraction [{e :db/id :as entity}]
    (mapv #(vector :db/retract e %) (filter #(not= :db/id %) (keys entity))))
  (retraction $user)
  (db/transact (db/connection (:bread/app @main/system))
               (retraction $user))
  (db/transact (db/connection (:bread/app @main/system))
               [{:user/username "bread"
                 :user/locked-at (java.util.Date.)}])

  (require '[kaocha.repl :as k])
  (k/run :unit)

  (-main "-f" "dev/minimal.edn"))

(def -main main/-main)
