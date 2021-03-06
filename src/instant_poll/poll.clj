(ns instant-poll.poll
  (:require [clojure.string :as string]
            [instant-poll.bar :as bar]
            [instant-poll.state :refer [scheduler db]]
            [datalevin.core :as d])
  (:import (java.util.concurrent TimeUnit)))

(defn put-poll! [poll]
  (d/transact-kv db [[:put "polls" (:id poll) poll]]))

(defn find-poll [poll-id]
  (d/get-value db "polls" poll-id))

(defn close-poll! [poll-id]
  (doto (assoc (find-poll poll-id) :closed true) put-poll!))

(defn create-poll! [id poll close-in close-action]
  (let [poll (cond-> (assoc poll :votes {} :id id)
               (pos? close-in) (assoc :close-timestamp (+' (quot (System/currentTimeMillis) 1000) close-in)))]
    (put-poll! poll)
    (when (pos? close-in)
      (.schedule scheduler ^Runnable (fn [] (close-action (close-poll! id))) ^long close-in ^TimeUnit TimeUnit/SECONDS))
    poll))

(defn toggle-vote [poll user option]
  (let [prev-votes (get-in poll [:votes user])
        multi-vote? (:multi-vote? poll)
        poll (if (contains? prev-votes option)
               (update-in poll [:votes user] disj option)
               (update poll :votes (fn [votes-map]
                                     (if (and multi-vote? (contains? votes-map user))
                                       (update votes-map user conj option)
                                       (assoc votes-map user #{option})))))]
    (cond-> poll
      (empty? (get-in poll [:votes user])) (update :votes dissoc user))))

(defn toggle-vote! [poll-id user option]
  (doto (toggle-vote (find-poll poll-id) user option) put-poll!))

(defn count-votes [options votes]
  (reduce-kv
   (fn [acc _user-id opts]
     (reduce #(update %1 %2 inc) acc opts))
   (zipmap (map :key options) (repeat 0))
   votes))

(defn render-option-result [option bar-length width votes total-votes]
  (let [part (if (= total-votes 0) 0 (/ votes total-votes))]
    (format "%s %s %.1f%%" (format (str "%-" width \s) option) (bar/render bar-length  part) (double (* 100 part)))))

(defn render-poll [{:keys [votes question options multi-vote?] :as _poll} bar-length]
  (let [vote-counts (count-votes options votes)
        total-votes (count votes)
        width (->> options (map :key) (map count) (reduce max))
        option-list (string/join
                     \newline
                     (keep (fn [{:keys [key description]}]
                             (and description (str key ": " description)))
                           options))
        option-results (string/join \newline (map #(render-option-result % bar-length width (vote-counts %) total-votes) (map :key options)))]
    (format
     "%s%n%s%n```%n%s%n```%s%nNumber of participants: %d"
     question
     (cond->> option-list (seq option-list) (str \newline))
     option-results
     (str "You can pick " (if multi-vote? "**multiple** options." "**one** option."))
     total-votes)))

(defn close-notice [{:keys [close-timestamp] :as _poll} open?]
  (when close-timestamp
    (str "Poll close" (if open? \s \d) " <t:" close-timestamp ":R>")))
