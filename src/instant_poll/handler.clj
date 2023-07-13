(ns instant-poll.handler
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.util.response :refer [response]]
   [ring-discord-auth.ring :refer [wrap-authenticate]]
   [instant-poll.state :as state :refer [config]]
   [instant-poll.command :refer [handle-command poll-command]]
   [instant-poll.component :refer [handle-button-press handle-add-option-form-submit]]
   [mount.core :as mount]
   [org.httpkit.server :as http-server]
   [discljord.util :as discord-util]
   [discljord.messaging :as dm]
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
  (when (System/getenv "UPDATE_COMMANDS")
    (log/info "Updating global slash commands")
    (mount/start #'state/config #'state/discord-conn #'state/app-id)
    (let [result @(dm/bulk-overwrite-global-application-commands! state/discord-conn state/app-id [poll-command])]
      (mount/stop)
      (when (instance? Exception result)
        (log/error result "Updating commands was unsuccessful.")
        (System/exit 1)))
    (log/info "Commands updated successfully. It may take up to an hour and reloading your Discord client to see the changes.")
    (System/exit 0))

  (log/info "Starting instant-poll")
  (mount/start)
  (http-server/run-server
   (-> #'handler
      wrap-json-response
      wrap-clean-json
      wrap-json-body
      (wrap-authenticate (:public-key config)))
   (:server config)))
