(ns instant-poll.component
  (:require [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.state :refer [config]]
            [discljord.util :refer [parse-if-str]]
            [discljord.formatting :as discord-fmt]
            [discljord.permissions :as discord-perms]
            [slash.component.structure :as cmp]
            [slash.response :as rsp]))

(def max-options 15)

(def action-separator "_")
(def action-separator-pattern (re-pattern action-separator))

(defn parse-custom-id [custom-id]
  (string/split custom-id action-separator-pattern))

(def show-votes-button
  (cmp/button :secondary "show-votes" :label "Show Votes" :emoji {:name "🔎"}))

(def add-option-button
  (cmp/button :success "add-option" :label "Add Option" :emoji {:name "➕"}))

(def close-poll-button
  (cmp/button :danger "close" :label "Close Poll" :emoji {:name "🔒"}))

(defn make-components [{:keys [options show-votes allow-add-options?] :as _poll}]
  (concat
   (for [option-group (partition-all 5 options)]
     (apply
      cmp/action-row
      (for [{:keys [key emoji]} option-group]
        (cmp/button :primary (str "vote" action-separator key) :label key :emoji emoji))))
   [(apply
     cmp/action-row
     (cond-> []
       (= show-votes :always) (conj show-votes-button)
       allow-add-options? (conj add-option-button)
       true (conj close-poll-button)))]))

(defmulti poll-action (fn [action _interaction _poll _options] action))

(defmethod poll-action "vote"
  [_ {{{user-id :id} :user} :member {message-id :id :keys [channel-id]} :message :as _interaction} {:keys [id] :as _poll} [option]]
  (let [updated-poll (polls/toggle-vote! id user-id option)]
    (polls/put-poll! (assoc updated-poll :channel-id channel-id :message-id message-id))
    (rsp/update-message {:content (str (polls/render-poll updated-poll (:bar-length config)) \newline (polls/close-notice updated-poll true))})))

(defn group-by-votes [votes]
  (reduce-kv
   (fn [vote-table user user-votes]
     (reduce #(update %1 %2 (fnil conj []) user) vote-table user-votes))
   {}
   votes))

(defmethod poll-action "show-votes"
  [_ {} {:keys [votes] :as _poll} _]
  (-> {:content
       (let [msg (str
                  "**Here are the individual votes for this poll:**\n"
                  (string/join
                   "\n\n"
                   (map (fn [[option users]]
                          (str "`" option "`:\n" (string/join ", " (map discord-fmt/mention-user users))))
                        (group-by-votes votes))))]
         (if (> (count msg) 2000)
           "Sorry, I can't display the votes, there are too many."
           msg))
       :allowed_mentions {}}
      rsp/channel-message
      rsp/ephemeral))

(defn estimate-size [question options bar-length]
  (count (polls/render-poll
          {:question question
           :options options
           :votes {"123" (set (map :key options))}
           :multi-vote? true}
          bar-length)))


(defn keys-only? [poll]
  (-> poll :options first :description nil?))

(defn available-size [poll bar-length max-key-length]
  (let [updated (update poll :options conj (if (keys-only? poll)
                                       {:key ""}
                                       {:key (apply str (repeat max-key-length \a)) :description ""}))]
    (- 1920 (count (polls/render-poll updated bar-length)))))

(defmethod poll-action "add-option"
  [_ {{{user-id :id} :user} :member :as _interaction} {:keys [creator-id options] :as poll} _]
  (cond
    (not= user-id creator-id) (-> {:content "❌ Sorry, only the creator of this poll can add more options."} rsp/channel-message rsp/ephemeral)
    (> (count options) max-options) (-> {:content "❌ The maximum number of supported options has already been reached." rsp/channel-message rsp/ephemeral})
    :else
    (let [max-key-length (:max-key-length config)
          available (available-size poll (:bar-length config) max-key-length)]
      (if (<= available 0)
        (-> {:content "Can't add another option, your poll is too big!"} rsp/channel-message rsp/ephemeral)
        (apply
         rsp/modal
         "Add a poll option"
         "add-option-form"
         (cmp/action-row (cmp/text-input :short "option-emoji" "Option emoji" :placeholder "🙂" :required false))
         (if (keys-only? poll)
           [(cmp/action-row (cmp/text-input :short "option-key" "Option key" :required true :max-length (min available max-key-length)))]
           [(cmp/action-row (cmp/text-input :short "option-key" "Option key" :required true :max-length max-key-length))
            (cmp/action-row (cmp/text-input :paragraph "option-description" "Option description" :required false :max-length available))]))))))

(defmethod poll-action "close"
  [_ {{{user-id :id} :user :keys [permissions]} :member :as _interaction} {:keys [id creator-id show-votes] :as _poll} _]
  (if (or (= user-id creator-id) (discord-perms/has-permission-flag? :manage-messages (parse-if-str permissions)))
    (let [poll (polls/close-poll! id)]
      (rsp/update-message
       {:content (str (polls/render-poll poll (:bar-length config)) \newline
                      "Poll closed " (discord-fmt/timestamp (quot (System/currentTimeMillis) 1000) :relative-time)
                      " by " (discord-fmt/mention-user user-id) \.)
        :components (cond-> [] (#{:always :after-closing} show-votes) (conj (cmp/action-row show-votes-button)))}))
    (-> {:content "You do not have permission to close this poll."} rsp/channel-message rsp/ephemeral)))

(defmethod poll-action :default
  [action _ _ _]
  (-> {:content (str "Unknown action '" action "'. This is a bug. Please report this to the developers.")}
      rsp/channel-message
      rsp/ephemeral))

(defn handle-button-press
  [{{:keys [custom-id]} :data
    {{poll-id :id} :interaction} :message
    :as interaction}]
  (if-let [poll (polls/find-poll poll-id)]
    (let [[action & options] (parse-custom-id custom-id)]
      (poll-action action interaction poll options))
    ;; if poll not found
    (-> {:content "This poll isn't active anymore."} rsp/channel-message rsp/ephemeral)))
