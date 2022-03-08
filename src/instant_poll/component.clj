(ns instant-poll.component
  (:require [clojure.string :as string]
            [instant-poll.poll :as polls]
            [instant-poll.state :refer [config discord-conn app-id]]
            [discljord.util :refer [parse-if-str]]
            [discljord.formatting :as discord-fmt]
            [discljord.permissions :as discord-perms]
            [slash.component.structure :as cmp]
            [slash.response :as rsp])
  (:import (com.vdurmont.emoji EmojiManager)))

(def max-options 15)

(def action-separator "_")
(def action-separator-pattern (re-pattern action-separator))

(defn parse-custom-id [custom-id]
  (string/split custom-id action-separator-pattern))

(def show-votes-button
  (cmp/button :secondary "show-votes" :label "Show Votes" :emoji {:name "🔎"}))

(def add-option-button
  (cmp/button :success "add-option" :label "Option" :emoji {:name "➕"}))

(def remove-option-button
  (cmp/button :danger "remove-option" :label "Option" :emoji {:name "➖"}))

(def close-poll-button
  (cmp/button :danger "close" :label "Close Poll" :emoji {:name "🔒"}))

(defn make-components [{:keys [options show-votes allow-change-options?] :as _poll}]
  (concat
   (for [option-group (partition-all 5 options)]
     (apply
      cmp/action-row
      (for [{:keys [key emoji]} option-group]
        (cmp/button :primary (str "vote" action-separator key) :label key :emoji emoji))))
   (when allow-change-options?
     [(cmp/action-row
       #_remove-option-button
       add-option-button)])
   [(apply
     cmp/action-row
     (cond-> []
       (= show-votes :always) (conj show-votes-button)
       true (conj close-poll-button)))]))

(defmulti poll-action (fn [action _interaction _poll _options] action))

(defmethod poll-action "vote"
  [_ {{{user-id :id} :user} :member :keys [token] :as _interaction} {:keys [id] :as poll} [option]]
  (let [updated-poll (polls/toggle-vote (assoc poll :interaction-token token) user-id option)]
    (polls/put-poll! updated-poll)
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

(def creator-only-response
  (-> {:content "❌ Sorry, only the creator of this poll can add more options."} rsp/channel-message rsp/ephemeral))

(defn option-limit-response [type]
  (-> {:content (str "❌ The " type " number of supported options has already been reached.") rsp/channel-message rsp/ephemeral}))

(defmethod poll-action "add-option"
  [_ {{{user-id :id} :user} :member :as _interaction} {:keys [id creator-id options] :as poll} _]
  (cond
    (not= user-id creator-id) creator-only-response
    (= (count options) max-options) (option-limit-response "maximum")
    :else
    (let [max-key-length (:max-key-length config)
          available (available-size poll (:bar-length config) max-key-length)]
      (if (<= available 0)
        (-> {:content "Can't add another option, your poll is too big!"} rsp/channel-message rsp/ephemeral)
        (apply
         rsp/modal
         "Add a poll option"
         id
         (cmp/action-row (cmp/text-input :short "emoji" "Option emoji" :placeholder "🙂" :required false))
         (if (keys-only? poll)
           [(cmp/action-row (cmp/text-input :short "key" "Option key" :required true :max-length (min available max-key-length)))]
           [(cmp/action-row (cmp/text-input :short "key" "Option key" :required true :max-length max-key-length))
            (cmp/action-row (cmp/text-input :paragraph "description" "Option description" :required false :max-length available))]))))))

(defmethod poll-action "remove-option"
  [_ {{{user-id :id} :user} :member :as _interaction} {:keys [id creator-id options] :as poll} _]
  (cond
    (not= user-id creator-id) creator-only-response
    (= (count options) 2) (option-limit-response "minimum")
    :else
    (let [max-removal (- (count options) 2)]
      (rsp/modal
       "Select the options you'd like to remove"
       "remove-selection"
       (cmp/action-row
        (cmp/select-menu
         "options-to-remove"
         (map (fn [{:keys [key emoji]}] (cmp/select-option key key :emoji emoji)) options)
         :placeholder "Select option(s)"
         :max-values max-removal))))))

(defmethod poll-action "options-to-remove"
  [_ {{:keys [values]} :data :as _interaction} {:keys [interaction-token channel-id message-id options] :as poll} _]
  ;; TODO remove selected values from options and from voter map
  )

(defn parse-emoji [^String emoji-str]
  (let [[_ a name id :as match] (re-matches discord-fmt/emoji-mention emoji-str)]
    (cond
      (EmojiManager/isEmoji emoji-str) {:name emoji-str}
      match {:animated (some? a)
             :name name
             :id id})))

(defn unlock-message [feature app-id]
  (-> {:content (str "For " feature " after more than **15 minutes** of inactivity, I need additional authorisation."
                     "\nThis is because Discord doesn't let me edit my messages after a longer period of time anymore if I am not directly on your server.")
       :components [(cmp/action-row
                     (cmp/link-button
                      (str "https://discord.com/api/oauth2/authorize?client_id=" app-id "&scope=bot")
                      :label (str "Unlock " feature " after 15 minutes")
                      :emoji {:name "🔓"}))]}
      rsp/channel-message))

(defn handle-add-option-form-submit
  [{{:keys [components custom-id]} :data}]
  (let [option (->> components
                    (map :components)
                    (map first)
                    (map (juxt (comp keyword :custom-id) :value))
                    (into {}))
        {:keys [key] :as final-option} (update option :emoji parse-emoji)
        {:keys [options] :as old-poll} (polls/find-poll custom-id)
        new-poll (update old-poll  :options conj final-option)
        message {:content (polls/render-poll new-poll (:bar-length config))
                 :components (make-components new-poll)}]
    (if (some #{key} (map :key options))
      (-> {:content (str "There already is an option with key `" key "`!")} rsp/channel-message rsp/ephemeral)
      (do
        (polls/put-poll! new-poll)
        (-> message rsp/update-message rsp/ephemeral)))))

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
    {{poll-id :id} :interaction message-id :id :keys [channel-id]} :message
    :as interaction}]
  (if-let [poll (polls/find-poll poll-id)]
    (let [[action & options] (parse-custom-id custom-id)]
      (when-not (:channel-id poll)
        (polls/put-poll! (assoc poll :channel-id channel-id :message-id message-id)))
      (poll-action action interaction poll options))
    ;; if poll not found
    (-> {:content "This poll isn't active anymore."} rsp/channel-message rsp/ephemeral)))
