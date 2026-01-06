(ns systems.bread.alpha.plugin.marx
  (:require
    [clojure.edn :as edn]
    [cognitect.transit :as transit]
    [com.rpl.specter :as s]
    [editscript.core :as edit]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.component :refer [defc Section]]
    [systems.bread.alpha.database :as db]
    [systems.bread.alpha.dispatcher :as dispatcher]
    [systems.bread.alpha.i18n :as i18n]
    [systems.bread.alpha.route :as route]
    [systems.bread.alpha.thing :as thing]
    [systems.bread.alpha.util.datalog :as datalog])
  (:import
    [java.io ByteArrayInputStream]
    [org.owasp.html HtmlPolicyBuilder]))

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

(defn Field [can-edit? thing field-type k & opts]
  (let [{:keys [tag wrapper attrs]
         :or {tag :div
              wrapper [:div]
              attrs {}}} opts
        {{:as fields field-defs :bread/fields} :thing/fields} thing
        content (get fields k)
        field (get field-defs k)
        attrs (if can-edit?
                (merge attrs {:data-marx (-> field
                                             (dissoc :field/content)
                                             (assoc :marx/field-type field-type)
                                             pr-str)
                              :tabindex 0})
                attrs)
        html (if wrapper
               (vec (conj wrapper [tag attrs content]))
               [tag attrs content])]
    html))

(defmethod Section ::site-name [{{:marx/keys [site-name]} :config} _]
  [:div site-name])

;; TODO move to UI ns
(defmethod Section :loading-spinner [_ _]
  [:span {:style {:cursor :wait}} "loading..."])

;; TODO move to settings plugin ns
(defmethod Section ::settings [{:as data :keys [i18n]} _]
  [:div
   [:div {:id :settings
          "data-show" "$showSettings"
          :style {:display :none}}
    (Section data :loading-spinner)]
   [:button {"data-on:click" "$showSettings = !$showSettings"}
    ;; TODO i18n
    (:marx/settings i18n "Settings")]])

(defc MediaLibrary [{:keys [media config]}]
  {:key :media
   :query '[:thing/slug {:thing/fields [*]}]}
  (let [id (:media/media-library-html-id config :media-library)]
    [:aside {:id id
             :data-show "$showMedia"}
     [:h2 "Media: " (count media) " posts"]
     (doall (map (fn [{{:keys [alt-text uri]} :thing/fields}]
                   [:img {:alt alt-text :src uri :width 150}])
                 media))]))

(defmethod bread/dispatch ::media.library=>
  [{{post-type :post/type
     post-status :post/status
     :or {post-type :media
          post-status :post.status/published}
     :as dispatcher} ::bread/dispatcher
    {:keys [user]} :session
    :keys [params]
    :as req}]
  (when (bread/hook req ::allow-list-media? user)
    (let [;; TODO params for filtering, pagination, etc.
          pull (datalog/ensure-db-id (:dispatcher/pull dispatcher))
          args [{:find [(list 'pull '?e pull)]
                 :in '[$]
                 :where [['?e :post/type post-type]
                         ['?e :post/status post-status]]}]
          k (:dispatcher/key dispatcher :media)
          query {:expansion/name ::db/query
                 :expansion/key k
                 :expansion/db (db/database req)
                 :expansion/args args
                 :expansion/description "Query for items in the media library."}
          paginate {:expansion/name ::thing/paginate
                    :expansion/key k
                    :expansion/description "Paginate media items."
                    :page (Integer. (:page params 1))
                    :per-page (:per-page dispatcher 25)}]
      {:expansions (conj (bread/hook req ::i18n/expansions query) paginate)})))

(defn ->sig [x]
  (if (map? x)
    (str "{"
         (clojure.string/join
           ", "
           (reduce (fn [signals [k v]]
                     (conj signals (str (name k) ": " (->sig v)))) [] x))
         "}")
    (str x)))

(comment
  (->sig {:foo {:bar 123}}))

;; TODO move to media plugin ns
(defmethod Section ::media [{:as data :keys [i18n]} _]
  [:div {:data-signals (->sig {:mediaPage 1})}
   [:div {:id :media-library
          :data-show "$showMedia"
          :style {:display :none}}
    (Section data :loading-spinner)]
   [:button {"data-on:click" "$showMedia = !$showMedia; $showMedia && @get(`/~/marx/media?page=${$mediaPage}`)"}
    ;; TODO i18n
    (:marx/settings i18n "Media")]])

(defmethod Section ::publish [{:keys [i18n]} _]
  [:div
   [:button {"data-on:click" "marxPublish()"}
    (:publish i18n "Publish")]])

(defn BreadBar [{{:marx/keys [bar-sections bar-position]} :config
                 :as data}]
  [:aside {:data-bread true :data-bar-position bar-position}
   (doall (map (partial Section data) bar-sections))])

(defn Embed [{{:marx/keys [backend bar-settings datastar-uri editor-name marx-css-uri
                           marx-js-uri site-name]}
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
       [:link {:rel :stylesheet :href marx-css-uri}]
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

(defn- sanitize-fields [policy e]
  (update e :fields (fn [fields]
                      (map (fn [field]
                             (update field :field/content #(.sanitize policy %)))
                           fields))))

(defn- sanitizer-policy [{:keys [allow-elements images links]}]
  (let [str-array (fn [v] (into-array String v))]
    (cond-> (HtmlPolicyBuilder.)
      (seq allow-elements) (.allowElements (str-array (map name allow-elements)))

      links
      (.allowElements (str-array ["a"]))
      (seq (:allow-attrs links))
      (.allowAttributes (str-array (map name (:allow-attrs links))))
      (seq (:allow-attrs links))
      (.onElements (str-array ["a"]))
      (seq (:allow-url-protocols links))
      (.allowUrlProtocols (str-array (map name (:allow-url-protocols links))))
      (:require-rel-nofollow? links)
      (.requireRelNofollowOnLinks)

      images
      (.allowElements (str-array ["img"]))
      (seq (:allow-attrs images))
      (.allowAttributes (str-array (map name (:allow-attrs images))))
      (seq (:allow-attrs links))
      (.onElements (str-array ["img"]))
      (seq (:allow-url-protocols images))
      (.allowUrlProtocols (str-array (map name (:allow-url-protocols images))))

      true (.toFactory))))

(comment
  (sanitize-html (sanitizer-policy {:allow-elements [:p]
                                    :links {:allow-attrs [:href :title]}})
                 (str "<a id='mylink' href='/mylink'>CLICK HERE</a>"
                      "<p>paragraph</p>"
                      "<script>alert(1);<script>")))

(defmethod bread/dispatch ::edit=>
  [{:keys [marx/edit body session] :as req}]
  (let [policy-config (bread/hook req ::sanitizer-policy
                                  (bread/config req :marx/sanitizer-policy))
        policy (sanitizer-policy policy-config)
        e (->> (if edit edit (transit-decode (slurp body)))
               (sanitize-fields policy))]
    (when (bread/hook req ::allow-edit? (boolean (:user session)) e)
      (log/info "edit" e)
      (let [txs (edit->transactions e)
            txs (if (:revision? e)
                  [(transactions->revision req txs)]
                  txs)]
        (log/debug "editor txs" txs)
        {:effects
         [{:effect/name ::db/transact
           :effect/description "Persist edits."
           :effect/key (:edit/key e)
           :conn (db/connection req)
           :txs (bread/hook req ::transactions txs e)}]}))))

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
                                 marx-css-uri
                                 sanitizer-policy
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
                    marx-js-uri "/marx/js/marx.js"
                    marx-css-uri "/marx/css/marx.css"
                    sanitizer-policy
                    {:allow-elements [:h1 :h2 :h3 :h4 :h5 :h6
                                      :p :br :hr :pre :code
                                      :div :blockquote
                                      :ul :ol :li :dl :dt :dd
                                      :strong :em :b :i :u :s :mark
                                      :sub :sup :abbr :cite :q]
                     :links {:allow-attrs [:href :title :class :id]
                             :allow-url-protocols [:http :https :mailto]
                             :require-rel-nofollow? true}
                     :images {:allow-attrs [:src :alt :title :width :height]
                              :allow-url-protocols [:http]}}}}]
  {:plugin/id ::marx
   :config {;; TODO support secure websockets
            :marx/backend backend
            :marx/bar-position bar-position
            :marx/bar-sections bar-sections
            :marx/datastar-uri datastar-uri
            :marx/editor-name editor-name
            :marx/marx-js-uri marx-js-uri
            :marx/marx-css-uri marx-css-uri
            :marx/site-name site-name
            ;; TODO #_#_
            :marx/bar-settings {:bar/position bar-position
                                :theme/variant default-theme}
            :marx/sanitizer-policy sanitizer-policy
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
