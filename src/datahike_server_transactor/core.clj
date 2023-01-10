(ns datahike-server-transactor.core
  (:require [datahike.writer :refer [PWriter create-writer create-database delete-database]]
            [clj-http.lite.client :as client]
            [taoensso.timbre :as log]
            [clojure.core.async :refer [promise-chan put!]]
            [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream]))

(def transit-fmt "application/transit+json")

(def MEGABYTE (* 1024 1024))

(def BUFFER_SIZE (* 4 MEGABYTE))

(defn api-request
  ([method url]
   (api-request method url nil))
  ([method url data]
   (api-request method url data nil))
  ([method url data opts]
   (let [out (ByteArrayOutputStream. BUFFER_SIZE)
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

(defrecord DatahikeServerWriter [client-config]
  PWriter
  (-dispatch! [_ arg-map]
    (let [{:keys [op args]} arg-map
          p (promise-chan)]
      (log/debug "Sending operation to datahike-server:" op)
      (log/debug "Arguments:" arg-map)
      (put! p
            (api-request "post"
                         (str (:endpoint client-config) "/" op)
                         (first args)
                         {:headers
                          (merge
                           {"db-name" (:db-name client-config)}
                           (when (:token client-config)
                             {"authorization" (:token client-config)}))}))
      p))
  (-shutdown [_])
  (-streaming? [_] false))

(defmethod create-writer :datahike-server
  [config _]
  (log/debug "Creating datahike-server writer for " config)
  (let [client-config (:client-config config)]
    (->DatahikeServerWriter client-config)))

(defmethod create-database :datahike-server
  [& args]
  (throw (ex-info "Not supported yet." {:type :not-supported-yet})))

(defmethod delete-database :datahike-server
  [& args]
  (throw (ex-info "Not supported yet." {:type :not-supported-yet})))
