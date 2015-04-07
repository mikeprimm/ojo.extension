(ns ^:impl ojo.extension.track-appends.impl
  (:require [fs.core :as fs]
            [clojure.java.io :as io])
  (:import [java.io InputStreamReader RandomAccessFile]
           java.util.zip.CRC32
           org.apache.commons.io.IOUtils))

(defn verify-update-file-crc-32 [file prevlen prevcrc]
  "Single pass verify of existing content CRC-32, and calculation of new length and CRC32 - 
return list with verify result (true/false), new length, and new CRC32"
  (with-open [fd (java.io.RandomAccessFile. (fs/file file) "r")]
    (let [ba (byte-array 65536)
          crcprev (CRC32.)
          crcnew (CRC32.)
          newlen (fs/size file)]
      (loop [tprevlen prevlen
             tnewlen newlen
             rlen (.read fd ba 0 (min (max newlen prevlen) 65536))]
        (if (> rlen 0)
          (do 
            (if (> tprevlen 0) (.update crcprev ba 0 (min rlen tprevlen)))
            (if (> tnewlen 0) (.update crcnew ba 0 (min rlen tnewlen)))
            (recur (- tprevlen rlen) (- tnewlen rlen) (.read fd ba 0 (min (- (max tnewlen tprevlen) rlen) 65536))))))
      (list (and (>= newlen prevlen) (= (.getValue crcprev) prevcrc)) newlen (.getValue crcnew)))))

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
