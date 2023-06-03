(ns instant-poll.multipart
  "Very basic multipart/form-data encoding to support sending attachments in responses to Discord."
  (:require [ring.util.response :as r]
            [clojure.string :as str]))

;; https://www.rfc-editor.org/rfc/rfc7578
;; https://www.rfc-editor.org/rfc/rfc2046#section-5.1

(def boundary "---")

(def content-type
  (format "multipart/form-data; boundary=\"%s\"" boundary))

(def delimiter
  (format "--%s\r\n" boundary))

(defn- encode-part [{:keys [content name headers]}]
  (let [header-str (->> (assoc headers "Content-Disposition" (str "form-data; name=\"" name "\"\r\n"))
                        (map (fn [[k v]] (str k ": " v "\r\n"))))]
    (str
     header-str
     "\r\n"
     content
     "\r\n"
     delimiter)))

(defn encode
  "Encode edn representation of multipart response to multipart string.

  `parts` is a vector of maps with the following keys:

  - `:name` - form data field name
  - `:headers` - map of string header key to string header value (such as `filename` or `Content-Type`)
  - `:content` - part content, as a string"
  [parts]
  (str (str/join (map encode-part parts)) "--"))

(defn form-data-response [response]
  (-> response
      (r/content-type content-type)
      (update :body encode)))
