(ns bigo.db
  (:require [clojure.tools.logging :refer :all]
            [bigo.utils :as utils]
            [jepsen
             [control :as c]
             [db :as db]]
            [jepsen.control.util :as cu]))

(defn db
  "Handles DB initialization and operations."
  []
  (reify db/DB
    (setup! [_ test node]
      (info "Starting the Database.")
      (c/su
        (c/exec :mkdir :-p utils/dir)
        (cu/start-daemon!
          {:logfile utils/logfile
           :pidfile utils/pidfile
           :chdir   utils/dir}
          utils/binary
          ;Add command line arguments here
          ;:--listen-peer-urls             (peer-url   node)
          ;:--listen-client-urls           (client-url node)
          ;:--advertise-client-urls        (client-url node)
          ;:--initial-cluster-state        :new
          ;:--initial-advertise-peer-urls  (peer-url node)
          ;:--initial-cluster              (initial-cluster test)
         )

        (Thread/sleep 10000)))

    (teardown! [_ test node]
      (info node "tearing down the database")
      (cu/stop-daemon! utils/binary utils/pidfile)
      (c/su (c/exec :rm :-rf utils/dir)))

    db/LogFiles
    (log-files [_ test node]
      [utils/logfile])))