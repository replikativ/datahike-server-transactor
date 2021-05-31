(ns datahike-server-transactor.core
  (:require [datahike-client.api :as dc]
            [datahike.transactor :refer [PTransactor create-transactor]]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [promise-chan put!]]))


(defrecord DatahikeServerTransactor [connection client client-config]
  PTransactor
  (send-transaction! [_ tx-data _]
    (let [p (promise-chan)]
      (log/debug "Sending transaction to datahike-server" client-config tx-data)
      (put! p (dc/transact connection {:db-name (:db-name client-config)
                                       :tx-data tx-data}))
      p))
  (shutdown [_]
      ;; TODO shutdown client here
    )
  (streaming? [_] false))

(defmethod create-transactor :datahike-server
  [config _ _]
  (log/debug "Creating datahike-server transactor for " config)
  (let [client-config (:client-config config)
        client (dc/client client-config)
        connection (dc/connect client (:db-name client-config))]
    (->DatahikeServerTransactor connection client client-config)))

