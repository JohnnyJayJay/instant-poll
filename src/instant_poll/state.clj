(ns instant-poll.state
  (:require [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [discljord.messaging :as discord])
  (:import (java.util.concurrent Executors ScheduledExecutorService)))


(defstate ^ScheduledExecutorService scheduler
  :start (Executors/newSingleThreadScheduledExecutor)
  :stop (.shutdown ^ScheduledExecutorService scheduler))

(defstate polls
  :start (atom {}))

(defstate config
  :start (edn/read-string (slurp (or (System/getenv "CONFIG") "config/config.edn"))))

(defstate discord-conn
  :start (discord/start-connection! (:token config))
  :stop (discord/stop-connection! discord-conn))

(defstate app-id
  :start (:id @(discord/get-current-application-information! discord-conn)))
