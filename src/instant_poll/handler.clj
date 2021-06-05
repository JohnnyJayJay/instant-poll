(ns instant-poll.handler
  (:gen-class)
  (:require [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring-debug-logging.core :refer [wrap-with-logger]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.util.response :as response]
            [instant-poll.auth :refer [wrap-authenticate]]
            [instant-poll.state :refer [config]]
            [instant-poll.command :refer [handle-command]]
            [instant-poll.component :refer [handle-button-press]]
            [mount.core :as mount]
            [org.httpkit.server :as http-server]
            [discljord.util :as discord-util]))

(defmulti handle-event :type)

(defmethod handle-event 1 [_] {:type 1})
(defmethod handle-event 2 [interaction] (handle-command interaction))
(defmethod handle-event 3 [interaction] (handle-button-press interaction))

(defn handler [{:keys [body]}]
  (response/response (handle-event body)))

(defn wrap-clean-json [handler]
  #(handler (update % :body discord-util/clean-json-input)))

(defn -main [& args]
  (mount/start)
  (http-server/run-server
   (-> handler
      wrap-json-response
      wrap-clean-json
      wrap-json-body
      (wrap-authenticate (:public-key config))
      #_wrap-reload
      #_wrap-with-logger)
   (:server config)))
