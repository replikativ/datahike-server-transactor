(ns datahike-server-transactor.core
  (:require [clojure.edn :as edn]
            [datahike.transactor :refer [PTransactor create-transactor]]
            [clj-http.client :as client]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [promise-chan put!]]))

(def edn-fmt "application/edn")

(defn api-request
  ([method url]
   (api-request method url nil))
  ([method url data]
   (api-request method url data nil))
  ([method url data opts]
   (let [encode str]
     (-> (client/request (merge {:url url
                                 :method method
                                 :throw-exceptions? false
                                 :content-type edn-fmt
                                 :accept edn-fmt}
                                (when (or (= method :post) data)
                                  {:body (encode data)})
                                opts))
        :body
        edn/read-string ))))

(defrecord DatahikeServerTransactor [client-config]
  PTransactor
  (send-transaction! [_ tx-data tx-meta _]
    (let [p (promise-chan)]
      (log/debug "Sending transaction to datahike-server" client-config (count tx-data))
      (log/trace "Transacting data " tx-data)
      (put! p
            (api-request "post"
                         (str (:endpoint client-config) "transact")
                         {:tx-data tx-data
                          :tx-meta tx-meta}
                         {:headers
                          (merge
                           {:db-name (:db-name client-config)}
                           (when (:token client-config)
                             {:authorization (:token client-config)}))}))
      p))
  (shutdown [_])
  (streaming? [_] false))

(defmethod create-transactor :datahike-server
  [config _ _]
  (log/debug "Creating datahike-server transactor for " config)
  (let [client-config (:client-config config)]
    (->DatahikeServerTransactor client-config)))

