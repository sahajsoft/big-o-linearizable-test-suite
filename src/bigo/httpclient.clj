(ns bigo.httpclient
  (:refer-clojure :exclude [swap! reset! get set])
  (:require [clojure.core           :as core]
            [clojure.java.io        :as io]
            [clj-http.client        :as client]
            [cheshire.core          :as json]
            [slingshot.slingshot    :refer [try+ throw+]])
  (:import (com.fasterxml.jackson.core JsonParseException)
           (java.io InputStream)))

(def default-timeout "milliseconds" 1000)

(defn connect
  ([server-uri]
   (connect server-uri {}))
  ([server-uri opts]
   (merge {:timeout           default-timeout
           :endpoint          server-uri}
          opts)))

(defn construct-url
  [client key]
  (str (:endpoint client) "/probe/" key))

(defn http-opts
  "Given a map of options for a request, constructs a clj-http options map.
  :timeout is used for the socket and connection timeout."
  [client]
  {:content-type :application/json
   :throw-exceptions?     true
   :throw-entire-message? true
   :socket-timeout        (:timeout client)
   :conn-timeout          (:timeout client)
   }
  )

(defn parse-json
  "Parse an inputstream or string as JSON"
  [str-or-stream]
  (if (instance? InputStream str-or-stream)
    (json/parse-stream (io/reader str-or-stream) true)
    (json/parse-string str-or-stream true)))

(defn filter-req [m]
  (select-keys m [:eventId, :data]))

(defn parse-resp
  [response]
  (when-not (:body response)
    (throw+ {:type     ::missing-body
             :response response}))

  (try+
    (let [body (filter-req (parse-json (:body response)))
          h    (:headers response)]
      (with-meta body
                 {:status           (:status response)}))
    (catch JsonParseException e
      (throw+ {:type     ::invalid-json-response
               :response response}))))

(defmacro parse
  "Parses regular responses using parse-resp, but also rewrites slingshot
  exceptions to have a little more useful structure; bringing the json error
  response up to the top level and merging in the http :status."
  [expr]
  `(try+
     (let [r# (parse-resp ~expr)]
       r#)
     (catch (and (:body ~'%) (:status ~'%)) {:keys [:body :status] :as e#}
       (try (let [body# (parse-json ~'body)]
              (throw+ (cond (string? body#) {:message body# :status ~'status}
                            (map? body#) (assoc body# :status ~'status)
                            :else {:body body# :status ~'status})))
            (catch JsonParseException _#
              (throw+ e#))))))

(defn get*
  ([client key]
   (get* client key {}))
  ([client key opts]
   (->> (assoc opts :accept :json)
        (merge-with + (http-opts client))
        (client/get (construct-url client key))
        parse)))

(defn get
  "Get the current value of a given key"
  ([client key]
   (get client key {}))
  ([client key opts]
   (try+
     (get* client key opts)
     (catch [:status 404] _ {:errorCode 100}))))


(defn reset!
  "Resets the current value of a given key to `value`"
  ([client key value]
   (reset! client key value {}))
  ([client key value opts]
   (->> (assoc opts :body (json/encode value))
        (merge-with + (http-opts client))
        (client/put (construct-url client key))
        parse
        )
   ))
