(ns instant-poll.component
  (:require [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.state :refer [config polls]]
            [discljord.util :refer [parse-if-str]]
            [discljord.formatting :as discord-fmt]
            [discljord.permissions :as discord-perms]
            [slash.component.structure :as cmp]
            [slash.response :as rsp]))

(def action-separator "_")
(def action-separator-pattern (re-pattern action-separator))

(defn parse-custom-id [custom-id]
  (string/split custom-id action-separator-pattern))

(defn make-components [{:keys [options] :as _poll}]
  (concat
   (for [option-group (partition-all 5 options)]
     (apply
      cmp/action-row
      (for [[key _] option-group]
        (cmp/button :primary (str "vote" action-separator key) :label key))))
   [(cmp/action-row
     (cmp/button :danger "close" :label "Close Poll"))]))

(defn handle-button-press
  [{{{user-id :id} :user :keys [permissions]} :member
    {:keys [custom-id]} :data
    {message-id :id {poll-id :id} :interaction :keys [channel-id]} :message
    :as _interaction}]
  (if-let [{:keys [creator-id] :as _poll} (polls/find-poll poll-id)]
    (let [[action option] (parse-custom-id custom-id)]
      (case action
        "close"
        (if (or (= user-id creator-id) (discord-perms/has-permission-flag? :manage-messages (parse-if-str permissions)))
          (let [poll (polls/close-poll! poll-id)]
            (rsp/update-message
             {:content (str (polls/render-poll poll (:bar-length config)) \newline
                            "Poll closed " (discord-fmt/timestamp (quot (System/currentTimeMillis) 1000) :relative-time)
                            " by " (discord-fmt/mention-user user-id) \.)
              :components []}))
          (-> {:content "You do not have permission to close this poll."} rsp/channel-message rsp/ephemeral))

        "vote"
        (let [updated-poll (polls/toggle-vote! poll-id user-id option)]
          (swap! polls update poll-id assoc :channel-id channel-id :message-id message-id)
          (rsp/update-message {:content (str (polls/render-poll updated-poll (:bar-length config)) \newline (polls/close-notice updated-poll true))}))

        (-> {:content (str "Unknown action '" action "'. This is a bug. Please report this to the developers.")})))
    ;; if poll not found
    (-> {:content "This poll isn't active anymore."} rsp/channel-message rsp/ephemeral)))
