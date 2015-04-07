(ns ojo.extension.track-appends
  "An ojo extension for tracking append versus non-append file modifications."
  (:require [ojo.extension.track-appends.impl :refer :all]
            [ojo.respond :refer :all]
            [fs.core :as fs]
            [mostly-useful.core :refer [assoc-keep]]))

(defresponse event-info
  "add :appended-only? to :modify events when the file has only been appended"
  {:events (map
            (fn [{:keys [kind file] :as event}]
              (let [{:keys [bit-position] :as file-state} (*state* file)
                    [prevmatch newlen newcrc] (verify-update-file-crc-32 file (or bit-position 0) (:checksum file-state))]
                (if-not bit-position
                  (assoc-keep event
                    :checksum newcrc
                    :bit-end newlen)
                  (assoc-keep event
                    :bit-position bit-position
                    :appended-only? prevmatch
                    :checksum newcrc
                    :bit-end newlen))))
            *events*)})

(defresponse update-file-info
  "add bit position and checksum info to file state for each of the files"
  {:state (reduce
           (fn [r {file :file flen :bit-end newcrc :checksum}]
               (update-in r [file] #(assoc %
                                      :bit-position flen
                                      :bit-difference (- flen
                                                         (or
                                                          (:bit-position
                                                           (*state* file)) 0))
                                      :checksum newcrc)))
           *state*
           *events*)})

(def track-appends
  {:before-response event-info
   :after-response update-file-info})
