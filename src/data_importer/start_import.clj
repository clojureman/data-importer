(ns data-importer.start-import
  (:gen-class
   :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler])
  (:require [amazonica.aws.dynamodbv2 :as ddb]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [clj-uuid :as uuid]
            [clj-time.coerce :as c]
            [clojure.string :as s]
            [com.rpl.specter :refer :all]
            [amazonica.aws.sqs :as sqs]
            [clojure.java.jdbc :as j]))

(defn mk-req-handler
  "Makes a request handler"
  [f & [wrt]]
  (fn [this is os context]
    (let [w (io/writer os)
             res (-> (parse-stream (io/reader is) keyword)
                  f)]
      (prn "R" res)
      ((or wrt
           (fn [res w] (.write w (prn-str res))))
        res w)
      (.flush w))))

(def db-spec {:dbtype "mssql"
              :host ""
              :dbname "Nord"
              :user "W19807Read"
              :password ""})

(defn get-queue []
  (sqs/find-queue "import-queue-iceeog-dev"))

(defn start []
  (let [vurids (j/query db-spec ["select distinct(vurderingsejendom_id_ice) from vurderingsejendom"])
        queue (get-queue)]
    (map #(sqs/send-message queue (:vurderingsejendom %)) vurids)))

(def -handleRequest (mk-req-handler start))
