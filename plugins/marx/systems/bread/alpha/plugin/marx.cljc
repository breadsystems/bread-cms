(ns systems.bread.alpha.plugin.marx
  (:require
    [clojure.edn :as edn]
    [cognitect.transit :as transit]
    [com.rpl.specter :as s]
    [editscript.core :as edit]
    [hickory.core :as hickory]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route])
  (:import
    [java.io ByteArrayInputStream]))

(defn on-websocket-message [app message]
  (let [message (bread/hook app ::websocket-message (edn/read-string message))]
    (prn message)
    (def $app (assoc app :marx/edit message))
    (def $db (db/database $app))
    (-> app
        (assoc :marx/edit message)
        (bread/hook ::bread/route)
        (bread/hook ::bread/dispatch)
        (bread/hook ::bread/expand)
        (bread/hook ::bread/effects!))))

(defn render-field [field field-type & {:as extra}]
  (let [tag (:tag extra :div)
        data-attr (-> field
                      (dissoc :field/content)
                      (assoc :marx/field-type field-type)
                      pr-str)]
    [tag {:data-marx data-attr}
     (:field/content field)]))

(defn render-bar [{{:user/keys [preferences]} :user :as data}]
  (let [doc {:query/pull (:query/pull data)
             :db/id (:db/id (get data (:query/key data)))}]
    [:div {:data-marx (pr-str {:field/key :bar
                               :marx/field-type :bar
                               :marx/document doc
                               :sections (or (:bar/sections preferences)
                                             [:site-name
                                              :settings
                                              :media
                                              :spacer
                                              :publish-button])
                               ;; Tell the frontend not to send info about
                               ;; BreadBar itself.
                               :persist? false})}]))

(defn fragment
  "Wrap a hiccup-style vector in a hiccup-style fragment."
  [v]
  (vec (cons :<> v)))

(defmulti edit->transactions :edit/action)

(defmethod edit->transactions :publish-fields [{:keys [fields]}]
  (map (fn [field]
         (-> field
             (update :field/content
                     #(->> %
                           hickory/parse-fragment
                           (mapv hickory/as-hiccup)
                           fragment))
             i18n/with-serialized
             (select-keys [:db/id :field/content])))
       fields))

(defn- revision-diff [db pull id txs]
  (let [query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}]
    (edit/diff (db/q db query id)
               (db/q (db/db-with db {:tx-data txs}) query id))))

(defn- transactions->revision [app txs]
  (let [{{doc :marx/document :as edit} :marx/edit} app
        ;; TODO support revisions for multiple Things
        pulls [(:query/pull doc)]
        ids [(:db/id doc)]]
    {:revision/diffs
     (map (fn [pull id]
            {:diff/op (pr-str (revision-diff (db/database app) pull id txs))
             :diff/thing id})
          pulls ids)
     :revision/note (:revision/note edit)}))

(comment
  (:user $app)
  (:marx/edit $app)

  (db/q (db/database $app) '{:find [(pull ?e [*])]
                             :where [[?e :revision/diffs]]})

  (let [db $db
        id 76
        txs (edit->transactions (:marx/edit $app))
        revised-db (db/db-with db {:tx-data txs})
        pull [:field/content]
        query {:find [(list 'pull '?e pull) '.]
               :in '[$ ?e]}
        data-orig (db/q db query id)
        data-revised (db/q revised-db query id)]
    (edit/diff data-orig data-revised))

  (let [txs (edit->transactions (:marx/edit $app))]
    (revision-diff $db [:field/content] 76 txs))

  (let [txs (edit->transactions (:marx/edit $app))]
    (transactions->revision $app txs))

  ;;
  )

(defn- transit-decode [s]
  (let [in (ByteArrayInputStream. (.getBytes s))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defmethod bread/dispatch ::edit=>
  [{:keys [marx/edit body session] :as req}]
  (let [edit (if edit edit (transit-decode (slurp body)))]
    (when (bread/hook req ::allow-edit? (boolean (:user session)) edit)
      (let [txs (edit->transactions edit)
            txs (if (:revision? edit)
                  [(transactions->revision req txs)]
                  txs)]
        (log/debug edit)
        {:effects
         [{:effect/name ::db/transact
           :effect/description "Persist edits."
           :effect/key (:edit/key edit)
           :conn (db/connection req)
           :txs (bread/hook req ::transactions txs edit)}]}))))

(defn EditorMeta [{{:marx/keys [site-name editor-name bar-settings backend]}
                   :config
                   {preferences :user/preferences} :user}]
  (let [user-bar-settings (select-keys preferences [:bar/position])
        bar-settings (merge-with #(or %1 %2) user-bar-settings bar-settings)
        marx-config {:name editor-name
                     :site/name site-name
                     :site/settings bar-settings
                     :backend backend}]
    [:meta {:content (pr-str marx-config)
            :name editor-name}]))

(defmethod bread/action ::dispatcher
  [app _ [dispatcher]]
  (if (bread/config app :marx/websocket?)
    {:dispatcher/type ::edit=>
     :dispatcher/description
     "Special dispatcher for saving edits made in the Marx editor."}
    dispatcher))

(defn plugin [{:as config :keys [backend bar-position editor-name site-name
                                 default-theme]
               :or {site-name "My Bread Site"
                    editor-name "marx-editor"
                    backend {:type :bread/http
                             :endpoint "/~/edit"}
                    #_ {:type :bread/websocket
                        :uri "ws://localhost:13120/_bread"}
                    bar-position :bottom
                    default-theme :dark}}]
  {:plugin/id ::marx
   :config {;; TODO support secure websockets
            :marx/websocket? false
            :marx/site-name site-name
            :marx/editor-name editor-name
            :marx/bar-settings {:bar/position bar-position
                                :theme/variant default-theme}
            :marx/backend backend
            #_#_
            :marx/collaboration {:strategy :webrtc
                                 ;; TODO from session...
                                 :user {:name "cobby"
                                        ;; TODO coordinating colors will be fun :D
                                        :color "red"
                                        :data-avatar "/cat.jpg"}}}
   :hooks
   {::route/dispatcher
    [{:action/name ::dispatcher
      :action/description
      "Conditionally returns a dispatcher for saving edits."}]}})
