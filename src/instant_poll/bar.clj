(ns instant-poll.bar
  (:require [clojure.string :as string]))

(def block-step 0.125)

(def whole "█")
(def blocks ["" "▏" "▎" "▍" "▌" "▋" "▊" "▉" "█"])

(def lsep "▏")
(def rsep "▕")

(defn render [max-length part]
  (let [length (* part max-length)
        whole-tiles-count (Math/floor length)
        frac-part (- length whole-tiles-count)
        frac-index (int (/ frac-part block-step))
        filled-bar (str (string/join (repeat whole-tiles-count whole)) (blocks frac-index))
        missing (- max-length (count filled-bar))
        empty-bar (string/join (repeat missing " "))]
    (str lsep filled-bar empty-bar rsep)))
