(ns datahike-server-transactor.core
  (:require [clojure.edn :as edn]
            [datahike.transactor :refer [PTransactor create-transactor]]
            [clj-http.lite.client :as client]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [promise-chan put!]]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream]))

(def transit-fmt "application/transit+json")

(def MEGABYTE (* 1024 1024))

(def MAXPAYLOAD (* 4 MEGABYTE))

(defn api-request
  ([method url]
   (api-request method url nil))
  ([method url data]
   (api-request method url data nil))
  ([method url data opts]
   (let [out (ByteArrayOutputStream. MAXPAYLOAD)
         writer (transit/writer out :json)
         _ (transit/write writer data)
         response (client/request (merge {:url               url
                                          :method            method
                                          :throw-exceptions? false
                                          :content-type      transit-fmt
                                          :accept            transit-fmt
                                          :as                :stream}
                                         (when (or (= method :post) data)
                                           {:body (.toString out)})
                                         opts))]
     (transit/read (transit/reader (:body response) :json)))))

(defrecord DatahikeServerTransactor [client-config]
  PTransactor
  (send-transaction! [_ tx-data tx-meta _]
    (let [p (promise-chan)]
      (log/debug "Sending transaction to datahike-server" client-config (count tx-data))
      (log/trace "Transacting data " tx-data)
      (put! p
            (api-request "post"
                         (str (:endpoint client-config) "transact")
                         (merge
                          (when tx-meta
                            {:tx-meta tx-meta})
                          {:tx-data tx-data})
                         {:headers
                          (merge
                           {"db-name" (:db-name client-config)}
                           (when (:token client-config)
                             {"authorization" (:token client-config)}))}))
      p))
  (shutdown [_])
  (streaming? [_] false))

(defmethod create-transactor :datahike-server
  [config _ _]
  (log/debug "Creating datahike-server transactor for " config)
  (let [client-config (:client-config config)]
    (->DatahikeServerTransactor client-config)))

