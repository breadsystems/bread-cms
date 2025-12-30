(ns systems.bread.alpha.plugin.email
  (:require
    [postal.core :as postal]
    [taoensso.timbre :as log]

    [systems.bread.alpha.core :as bread]
    [systems.bread.alpha.thing :as thing]
    [systems.bread.alpha.database :as db]))

(defn- summarize [email]
  (update email :body #(str "[" (-> % .getBytes count) " bytes]")))

(defn config->postal [{:email/keys [smtp-host
                                    smtp-port
                                    smtp-username
                                    smtp-password
                                    smtp-tls?]}]
  {:host smtp-host
   :port smtp-port
   :user smtp-username
   :pass smtp-password
   :tls smtp-tls?})

(defmethod bread/effect ::bread/email! send-email!
  [effect {{:as config :email/keys [dry-run? smtp-from-email]} :config}]
  (let [send? (and (not dry-run?) (not (:dry-run? effect)))
        postal-config (config->postal config)
        email {:from (or (:from effect) smtp-from-email)
               :to (:to effect)
               :subject (:subject effect)
               :body (:body effect)}]
    (log/info (if send? "sending email" "simlulating email") (summarize email))
    (when send?
      (postal/send-message postal-config email))))

(defn plugin [{:keys [smtp-from-email
                      smtp-host
                      smtp-port
                      smtp-username
                      smtp-password
                      smtp-tls?]
               :or {smtp-port 587}}]
  {:config
   {:email/smtp-from-email smtp-from-email
    :email/smtp-host smtp-host
    :email/smtp-port (Integer. smtp-port)
    :email/smtp-username smtp-username
    :email/smtp-password smtp-password
    :email/smtp-tls? (boolean smtp-tls?)}})
