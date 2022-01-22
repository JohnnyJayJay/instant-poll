(ns instant-poll.state
  (:require [clojure.edn :as edn]
            [mount.core :refer [defstate]]
            [discljord.messaging :as discord]
            [datalevin.core :as d])
  (:import (java.util.concurrent Executors ScheduledExecutorService)))


(defstate ^ScheduledExecutorService scheduler
  :start (Executors/newSingleThreadScheduledExecutor)
  :stop (.shutdown ^ScheduledExecutorService scheduler))

(defstate config
  :start (edn/read-string (slurp (or (System/getenv "CONFIG") "config/config.edn"))))

(defstate discord-conn
  :start (discord/start-connection! (:token config))
  :stop (discord/stop-connection! discord-conn))

(defstate app-id
  :start (:id @(discord/get-current-application-information! discord-conn)))

(defstate db
  :start (doto (d/open-kv (:db config)) (d/open-dbi "polls"))
  :stop (d/close-kv db))
