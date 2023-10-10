(ns bigo.main
  (:require [clojure.tools.logging :refer :all]
            [jepsen [checker :as checker]
             [cli :as cli]
             [generator :as gen]
             [independent :as independent]
             [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.os.debian :as debian]
            [bigo.db :as db]
            [bigo.client :as client]
            [bigo.utils :as utils]
            [knossos.model :as model])
  (:import (bigo.client Client)))

(def printable-ascii (->> (concat (range 48 57)
                                  (range 66 90)
                                  (range 97 122))
                          (map char)
                          char-array))

(defn rand-str
  "Random ascii string of n characters"
  [n]
  (let [s (StringBuilder. n)]
    (dotimes [_ n]
      (.append s ^char
               (->> printable-ascii
                    alength
                    rand-int
                    (aget printable-ascii))))
    (.toString s)))

(defn payload
  "Generate random payload for test."
  []
  {
    :eventId (.toString (java.util.UUID/randomUUID))
    :data (rand-str (rand-int 1000))
  })

(defn random-number-of-10-digits []
  (let [num-vector (shuffle (range 10))]
    (apply str num-vector)))

(defn probe-id-gen [] (str "PRB" (random-number-of-10-digits)))

(defn probe-id-seq [] (repeatedly #(probe-id-gen)))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (payload)})

(defn bigo-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:pure-generators true
          :name            "big-o"
          :os              debian/os
          :nodes           ["n1" "n2" "n3"]
          :db              (db/db)
          :client          (Client. nil)
          :nemesis         (nemesis/partition-random-halves)
          :checker         (checker/compose
                             {:perf  (checker/perf)
                              :indep (independent/checker
                                       (checker/compose
                                         {:linear   (checker/linearizable
                                                      {:model     (model/cas-register)
                                                       :algorithm :linear})
                                          :timeline (timeline/html)}))})
          :generator       (->> (independent/concurrent-generator
                                  10
                                  (probe-id-seq)
                                  (fn [k]
                                    (->> (gen/mix [r w])
                                         (gen/stagger (/ (:rate opts)))
                                         (gen/limit (:ops-per-key opts)))))
                                (gen/nemesis
                                  (->> [(gen/sleep 5)
                                        {:type :info, :f :start}
                                        (gen/sleep 5)
                                        {:type :info, :f :stop}]
                                       cycle))
                                (gen/time-limit (:time-limit opts)))})
  )

(def cli-opts
  "Additional command line options."
  [["-r" "--rate HZ" "Approximate number of requests per second, per thread."
    :default 10
    :parse-fn read-string
    :validate [#(and (number? %) (pos? %)) "Must be a positive number"]]
   [nil "--ops-per-key NUM" "Maximum number of operations on any given key."
    :default  100
    :validate [pos? "Must be a positive integer."]]])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn  bigo-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))