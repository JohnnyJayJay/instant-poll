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

(defn make-components [{:keys [options open?] :as _poll}]
  (concat
   (for [option-group (partition-all 5 options)]
     (apply
      cmp/action-row
      (for [{:keys [key emoji]} option-group]
        (cmp/button :primary (str "vote" action-separator key) :label key :emoji emoji))))
   [(apply
     cmp/action-row
     (cond-> []
       open? (conj (cmp/button :secondary "show-votes" :label "Show Votes" :emoji {:name "🔎"}))
       true (conj (cmp/button :danger "close" :label "Close Poll" :emoji {:name "🔒"}))))]))

(defmulti poll-action (fn [action _interaction _poll _options] action))

(defmethod poll-action "vote"
  [_ {{{user-id :id} :user} :member {message-id :id :keys [channel-id]} :message :as _interaction} {:keys [id] :as _poll} [option]]
  (let [updated-poll (polls/toggle-vote! id user-id option)]
    (swap! polls update id assoc :channel-id channel-id :message-id message-id)
    (rsp/update-message {:content (str (polls/render-poll updated-poll (:bar-length config)) \newline (polls/close-notice updated-poll true))})))

(defmethod poll-action "show-votes"
  [_ {} {:keys [votes] :as _poll} _]
  (-> {:content
       (let [msg (str
                  "**Here are the individual votes for this poll:**\n"
                  (string/join
                   "; "
                   (map (fn [[user-id options]]
                          (str (discord-fmt/mention-user user-id) ": " (discord-fmt/code (string/join ", " options))))
                        votes)))]
         (if (> (count msg) 2000)
           "Sorry, I can't display the votes, there are too many."
           msg))
       :allowed_mentions {}}
      rsp/channel-message
      rsp/ephemeral))

(defmethod poll-action "close"
  [_ {{{user-id :id} :user :keys [permissions]} :member :as _interaction} {:keys [id creator-id] :as _poll} _]
  (if (or (= user-id creator-id) (discord-perms/has-permission-flag? :manage-messages (parse-if-str permissions)))
    (let [poll (polls/close-poll! id)]
      (rsp/update-message
       {:content (str (polls/render-poll poll (:bar-length config)) \newline
                      "Poll closed " (discord-fmt/timestamp (quot (System/currentTimeMillis) 1000) :relative-time)
                      " by " (discord-fmt/mention-user user-id) \.)
        :components []}))
    (-> {:content "You do not have permission to close this poll."} rsp/channel-message rsp/ephemeral)))

(defmethod poll-action :default
  [action _ _ _]
  (-> {:content (str "Unknown action '" action "'. This is a bug. Please report this to the developers.")}))

(defn handle-button-press
  [{{:keys [custom-id]} :data
    {{poll-id :id} :interaction} :message
    :as interaction}]
  (if-let [poll (polls/find-poll poll-id)]
    (let [[action & options] (parse-custom-id custom-id)]
      (poll-action action interaction poll options))
    ;; if poll not found
    (-> {:content "This poll isn't active anymore."} rsp/channel-message rsp/ephemeral)))
