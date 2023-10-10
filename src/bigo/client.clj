(ns bigo.client
  (:require [clojure.tools.logging :refer :all]
            [jepsen [client :as client]
             [independent :as independent]]
            [slingshot.slingshot :refer [try+]]
            [bigo.httpclient :as v]
            [bigo.utils :as utils])
  (:import (java.net SocketTimeoutException)))


(defrecord Client [conn]
  client/Client
  (open! [this test node]
    (assoc this :conn (v/connect (utils/client-url node)
                                 {:timeout 5000})))

  (setup! [this test])

  (invoke! [_ test op]
    (let [[k v] (:value op)]
      (try+
        (case (:f op)
          :read (let [value (-> conn (v/get k))]
                  (assoc op :type :ok, :value (independent/tuple k value)))

          :write (do (v/reset! conn k v)
                     (assoc op :type :ok)))

        (catch SocketTimeoutException e
          (assoc op
            :type  (if (= :read (:f op)) :fail :info)
            :error :timeout))

        (catch [:errorCode 100] e
          (assoc op :type :fail, :error :not-found)))))

  (teardown! [this test])

  (close! [_ test])
  )