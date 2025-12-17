(ns systems.bread.alpha.plugin.marx
  (:require
    [clojure.edn :as edn]
    [cognitect.transit :as transit]
    [com.rpl.specter :as s]
    [editscript.core :as edit]
    [hickory.core :as hickory]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [Section]]
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

(defn Editable [field field-type & {:as extra}]
  (let [tag (:tag extra :div)
        data-attr (-> field
                      (dissoc :field/content)
                      (assoc :marx/field-type field-type)
                      pr-str)]
    [tag {:data-marx data-attr}
     (:field/content field)]))

(defmethod Section ::site-name [{{:marx/keys [site-name]} :config} _]
  [:div site-name])

(defmethod Section ::settings [{:keys [i18n]} _]
  [:div
   [:div {"data-show" "$showSettings"
          :style {:display :none}}
    "SETTINGS"]
   [:button {"data-on:click" "$showSettings = !$showSettings"}
    ;; TODO i18n
    (:marx/settings i18n "Settings")]])

(defmethod Section ::media [{:keys [i18n]} _]
  [:div
   [:div {"data-show" "$showMedia"
          :style {:display :none}}
    "MEDIA LIBRARY"]
   [:button {"data-on:click" "$showMedia = !$showMedia"}
    ;; TODO i18n
    (:marx/settings i18n "Media")]])

(defmethod Section ::publish [{:keys [i18n]} _]
  [:div
   [:button {"data-on:click" "marxPublish()"}
    (:publish i18n "Publish")]])

(defn BreadBar [{{:marx/keys [bar-sections bar-position]} :config
                 :as data}]
  (let [style {:position :fixed :left 0 bar-position 0}]
    [:div {:style {:position :fixed bar-position 0 :left 0 #_#_:width "100%"}}
     (doall (map (partial Section data) bar-sections))]))

(defn Embed [{{:marx/keys [backend bar-settings datastar-uri editor-name marx-js-uri site-name]}
              :config
              :keys [hook user]
              :as data}]
  (when (hook ::show-editor? (boolean user))
    (let [doc {:query/pull (:query/pull data)
               :db/id (:db/id (get data (:query/key data)))}
          editor-config {:name editor-name
                         :site/name site-name
                         :site/settings bar-settings
                         :backend backend
                         :marx/document doc}]
      [:<>
       (BreadBar data)
       [:script {:type :module :src datastar-uri}]
       [:script {:type "application/edn" :data-marx-editor editor-name}
        (pr-str editor-config)]
       [:script {:src "/marx/js/marx.js"}]])))

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

(defmethod bread/action ::dispatcher
  [app _ [dispatcher]]
  (if (bread/config app :marx/websocket?)
    {:dispatcher/type ::edit=>
     :dispatcher/description
     "Special dispatcher for saving edits made in the Marx editor."}
    dispatcher))

(defn plugin [{:as config :keys [backend
                                 bar-position
                                 bar-sections
                                 datastar-uri
                                 default-theme
                                 editor-name
                                 marx-js-uri
                                 site-name]
               :or {site-name "My Bread Site"
                    backend {:type :bread/http :endpoint "/~/edit"}
                    #_ {:type :bread/websocket
                        :uri "ws://localhost:13120/_bread"}
                    bar-position :bottom
                    bar-sections [::site-name
                                  ::settings
                                  ::media
                                  :spacer
                                  ::publish]
                    datastar-uri "https://cdn.jsdelivr.net/gh/starfederation/datastar@1.0.0-RC.6/bundles/datastar.js"
                    default-theme :dark
                    editor-name "marx-editor"
                    marx-js-uri "/marx/js/marx.js"}}]
  {:plugin/id ::marx
   :config {;; TODO support secure websockets
            :marx/backend backend
            :marx/bar-position bar-position
            :marx/bar-sections bar-sections
            :marx/datastar-uri datastar-uri
            :marx/editor-name editor-name
            :marx/marx-js-uri marx-js-uri
            :marx/site-name site-name
            ;; TODO #_#_
            :marx/bar-settings {:bar/position bar-position
                                :theme/variant default-theme}
            :marx/websocket? false
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
