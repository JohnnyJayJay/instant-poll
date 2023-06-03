(ns instant-poll.handler
  (:gen-class)
  (:require [ring.middleware.json :as json]
            [ring.util.response :as r]
            [ring-discord-auth.ring :as auth]
            [instant-poll.state :refer [config]]
            [instant-poll.command :refer [handle-command]]
            [instant-poll.component :refer [handle-button-press handle-add-option-form-submit]]
            [instant-poll.multipart :as multipart]
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
  (r/response (slash/route-interaction slash-handlers body)))

(defn wrap-clean-json [handler]
  #(handler (update % :body discord-util/clean-json-input)))

(defn wrap-content-response
  "Middleware that processes response data for transmission via HTTP.

  If the response body has `:multipart?` meta set to true, the response will be processed as a `multipart/form-data` response.
  Otherwise, a `application/json` response will be created."
  [handler]
  (fn [request]
    (let [resp (handler request)]
      (if (-> resp :body meta :multipart?)
        (multipart/form-data-response resp)
        (json/json-response resp {})))))


(defn -main [& _args]
  (mount/start)
  (http-server/run-server
   (-> #'handler
      wrap-content-response
      wrap-clean-json
      json/wrap-json-body
      (auth/wrap-authenticate (:public-key config)))
   (:server config)))
