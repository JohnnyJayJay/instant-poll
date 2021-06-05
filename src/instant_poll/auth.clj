(ns instant-poll.auth
  (:require [clojure.java.io :as io]
            [caesium.crypto.sign :as sign]
            [ring.util.response :as response]))

(defn str->bytes [^String str ^String encoding]
  (.getBytes str encoding))

(defn hex->bytes [^String hex-str]
  (let [len (count hex-str)
        result (byte-array (quot (inc len) 2))]
    (doseq [[i hex-part] (map-indexed vector (map (partial apply str) (partition-all 2 hex-str)))]
      (aset result i (unchecked-byte (Short/parseShort hex-part 16))))
    result))

(defn wrap-authenticate [handler public-key]
  (let [public-key (hex->bytes public-key)]
    (fn [{:keys [body character-encoding]
          {signature "x-signature-ed25519" timestamp "x-signature-timestamp"} :headers
          :or {character-encoding "utf8"}
          :as request}]
      (if (and body signature timestamp)
        (let [body-str (slurp body :encoding character-encoding)
              message (str timestamp body-str)
              authentic? (try
                           (sign/verify (hex->bytes signature) (str->bytes message character-encoding) public-key)
                           true
                           (catch RuntimeException _ false))]
          (if authentic?
            (with-open [is (io/input-stream (str->bytes body-str character-encoding))]
              (handler (assoc request :body is)))
            (response/status {:body "Unauthorized; signature was not authentic."} 401)))
        (response/bad-request "Bad request; missing body, signature or timestamp.")))))
