(ns instant-poll.handler
  (:gen-class)
  (:require [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.util.response :refer [response]]
            [ring-discord-auth.ring :refer [wrap-authenticate]]
            [instant-poll.state :refer [config]]
            [instant-poll.command :refer [handle-command]]
            [instant-poll.component :refer [handle-button-press handle-add-option-form-submit]]
            [mount.core :as mount]
            [org.httpkit.server :as http-server]
            [discljord.util :as discord-util]
            [slash.core :as slash]
            [slash.webhook :refer [webhook-defaults]]))

(def slash-handlers
  (assoc webhook-defaults
         :application-command #'handle-command
         :message-component #'handle-button-press
         :modal-submit #'handle-add-option-form-submit))

(defn handler [{:keys [body]}]
  (response (slash/route-interaction slash-handlers body)))

(defn wrap-clean-json [handler]
  #(handler (update % :body discord-util/clean-json-input)))

(defn -main [& _args]
  (mount/start)
  (http-server/run-server
   (-> #'handler
      wrap-json-response
      wrap-clean-json
      wrap-json-body
      (wrap-authenticate (:public-key config)))
   (:server config)))
