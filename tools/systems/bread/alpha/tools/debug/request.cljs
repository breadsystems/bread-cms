(ns systems.bread.alpha.tools.debug.request
  (:require
    [clojure.string :as string]
    [rum.core :as rum]
    [systems.bread.alpha.tools.debug.event :as e]
    [systems.bread.alpha.tools.debug.db :as db :refer [db]]
    [systems.bread.alpha.tools.impl.util :refer [conjv]]
    [systems.bread.alpha.tools.util :refer [date-fmt date-fmt-ms
                                            join-some pp
                                            req->url shorten-uuid]]))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;          HELPERS           ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; General helpers for dealing with requests
;;

(defn- record-replay [state {replayed :profiler/replay-uuid
                             uuid :request/uuid}]
  (if replayed
    (update-in state [:request/uuid replayed :request/replays] conjv uuid)
    state))

(defn- uuid->req [uuid]
  (get-in @db [:request/uuid uuid]))


    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;       EVENT HANDLERS       ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Data coming over the wire, or arbitrary UI changes
;;

(defmethod e/on-event :profile.type/request
  [[_ {uuid :request/uuid :as req}]]
  (swap! db (fn [state]
              (-> state
                  (assoc-in
                    [:request/uuid uuid]
                    {:request/uuid uuid
                     :request/id (shorten-uuid uuid)
                     ;; Record the raw request on its own.
                     :request/initial req
                     ;; TODO make this a sorted-set?
                     :request/replays []})
                  (update :request/uuids conjv uuid)
                  (record-replay req)))))

(defmethod e/on-event :profile.type/response
  [[_ {uuid :response/uuid :as res}]]
  (swap! db assoc-in [:request/uuid uuid :request/response] res))

(defmethod e/on-event :profile.type/hook
  [[_ {{rid :request/uuid} :hook/request :as invocation}]]
  (swap! db
         update-in [:request/uuid (str rid) :request/hooks]
         conjv invocation))


(defmethod e/on-event :ui/view-req [[_ uuid]]
  (swap! db assoc :ui/selected-req (str uuid)))

(defmethod e/on-event :ui/select-req [[_ idx]]
  (swap! db update :ui/selected-reqs
         (fn [selected]
           (if (selected idx)
             (disj selected idx)
             (conj selected idx)))))



    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;;                            ;;
  ;;        UI COMPONENTS       ;;
 ;;                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;
;; Rum component definitions
;;

(rum/defc request-details < rum/reactive []
  (let [{uuid :request/uuid
         id :request/id
         req :request/initial
         res :request/response
         :as req-data}
        (uuid->req (rum/react db/req-uuid))
        diff-opts (map shorten-uuid @db/req-uuids)
        viewing-hooks? (rum/react db/viewing-hooks?)
        viewing-raw-request? (rum/react db/viewing-raw-request?)
        viewing-raw-response? (rum/react db/viewing-raw-response?)]
    [:article.rows
     [:header.with-sidebar.reverse
      [:div
       [:div
        [:h2.emphasized (req->url req)]]
       [:div
        [:button.close-btn {:on-click #(swap! db assoc
                                              :ui/selected-req nil
                                              :ui/diff nil)}
         "Close"]]]]
     [:div.info (date-fmt-ms (:request/timestamp req))]
     [:div.with-sidebar
      [:div
       [:div "UUID"]
       [:div [:code.uuid uuid]]]]
     (when-let [replayed (:profiler/replay-uuid req)]
       [:div.with-sidebar
        [:div
         [:div "Replay of:"]
         [:div
          [:button.replay-uuid
           {:on-click #(swap! db assoc :ui/selected-req replayed)}
           (shorten-uuid replayed)]]]])
     (when-let [replays (seq (:request/replays req-data))]
       [:div.with-sidebar
        [:div
         [:div "Replays:"]
         [:div
          (map (fn [uuid]
                 [:button.replay-uuid.replay-btn
                  {:key uuid
                   :on-click #(swap! db assoc :ui/selected-req uuid)}
                  (shorten-uuid uuid)])
               replays)]]])
     [:div.flex
      [:div
       [:button {:on-click #(prn 'TODO 'replay-request!)}
        "Replay this request"]]
      [:div
       [:select
        {:on-change (fn [e]
                      (let [target (.. e -target -value)]
                        (swap! db assoc :ui/diff [uuid target])))}
        [:option "Diff against..."]
        (map (fn [opt]
               [:option {:key opt :value opt :disabled (= id opt)}
                opt (when (= id opt) " (this request)")])
             diff-opts)]]]

     [:h3 "Request hooks"]
     [:p.info
      (str (count (:request/hooks req-data))
           " hooks were invoked during this request")]
     [:div
      [:button.lowkey {:on-click #(swap! db update :ui/viewing-hooks? not)}
       (if viewing-hooks? "Hide" "Show")]]
     (when viewing-hooks?
       [:ul
        (map-indexed
          (fn [idx {:hook/keys [name args f file line column from-ns] :as h}]
            [:li {:key idx}
             [:strong (clojure.core/name name)]
             " "
             [:code
              (join-some ":" [(string/replace file
                                              #"systems/bread/alpha"
                                              "core")
                              line column])]])
          (:request/hooks req-data))])

     [:h3 "Response (HTML)"]
     [:div.response
      (:body res)]

     [:h3 "Response (pre-render)"]
     [:div.response
      (pp (:response/pre-render req-data))]

     [:h3 "Raw request"]
     [:div
      [:button.lowkey {:on-click #(swap! db update :ui/viewing-raw-request? not)}
       (if viewing-raw-request? "Hide" "Show")]]
     (when viewing-raw-request?
       [:pre (pp req)])
     [:h3 "Raw response"]
     [:div
      [:button.lowkey {:on-click #(swap! db update :ui/viewing-raw-response? not)}
       (if viewing-raw-response? "Hide" "Show")]]
     (when viewing-raw-response?
       [:pre (pp res)])]))
