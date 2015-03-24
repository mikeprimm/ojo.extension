(ns ^:impl ojo.extension.track-appends.impl
  (:require [fs.core :as fs]
            [clojure.java.io :as io])
  (:import [java.io InputStreamReader RandomAccessFile]
           java.util.zip.CRC32
           org.apache.commons.io.IOUtils))

(defn file-crc-32-len [file len]
  (with-open [fd (java.io.RandomAccessFile. (fs/file file) "r")]
    (let [ba (byte-array 65536)
          crc (CRC32.)]
      (loop [tlen len
             rlen (.read fd ba 0 (min len 65536))]
        (if (> rlen 0)
          (do 
            (.update crc ba 0 rlen)
            (recur (- tlen rlen) (.read fd ba 0 (min (- tlen rlen) 65536))))))
      (.getValue crc))))

(defn appended-only?
  "check the previously recorded checksum against the crc-32 checksum of the
   changed file truncated to previous bit-position."
  [{:keys [kind file] :as event}
   {:keys [bit-position checksum] :as earlier-state}]
  (and
   (<= bit-position (fs/size file))
   (= (file-crc-32-len file bit-position) checksum)))
