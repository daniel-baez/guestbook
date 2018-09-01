(ns guestbook.routes.ws
  (:require [compojure.core :refer [GET POST defroutes]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant 
             :refer [sente-web-server-adapter]]
            [bouncer.core :as b]
            [bouncer.validators :as v]
            [guestbook.db.core :as db]))

(let [connection (sente/make-channel-socket!
                   sente-web-server-adapter 
                   {:user-id-fn 
                    (fn [req] (get-in req [:params :client-id]))})]
  (def chsk-send! (:send-fn connection))
  (def connected-uids (:connected-uids connection))
  (def ch-chsk (:ch-recv connection))
  (def ring-ajax-get-or-ws-handshake (:ajax-get-or-ws-handshake-fn connection))
  (def ring-ajax-post (:ajax-post-fn connection)))

(defn validate-message [message]
  (first
    (b/validate 
      message
      :name v/required
      :timestamp v/required
      :message [v/required [v/min-count 10]])) )

(defn save-message! [message]
  (if-let [errors (validate-message message)]
    {:errors errors}
    (do 
      (db/save-message! message)
      message)))

(defn handle-message! [{:keys [id client-id ?data]}]
  (when (= :guestbook/add-message id)
    (let [response (-> ?data 
                       (assoc :timestamp (java.util.Date.))
                       save-message!)]
      (if (:errors response)
        (chsk-send! client-id [:guestbook/error response])
        (doseq [uid (:any @connected-uids)]
          (chsk-send! uid [:guestbook/add-message response]))))))

(defn start-router! []
  (sente/start-chsk-router! ch-chsk handle-message!))

(defn stop-router! [stop-fn]
  (when stop-fn (stop-fn)))

(defstate router
  :start (start-router!)
  :stop (stop-router! router))

(defroutes ws-routes
  (GET "/ws" r (ring-ajax-get-or-ws-handshake r))
  (POST "/ws" r (ring-ajax-post r)))

