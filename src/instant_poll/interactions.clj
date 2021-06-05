(ns instant-poll.interactions)


(defn normal-response [data]
  {:type 4
   :data (merge {:allowed_mentions []} data)})

(defn ephemeral-response [data]
  (normal-response (assoc data :flags 64)))

(defn update-message-response [data]
  {:type 7
   :data data})
