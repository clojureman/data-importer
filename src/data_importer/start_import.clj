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
            [amazonica.aws.simplesystemsmanagement :as ssm]
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

(defn get-parameters-by-tag [t]
  (->> (ssm/describe-parameters {:parameter-filters [{:key t}]})
      :parameters
      (map :name)
      vec))

(def params (delay ((ssm/get-parameters {:names (get-parameters-by-tag :tag:db)}) :parameters)))

(defn get-param [key]
  ((first (filter #(= key (% :name)) @params)) :value))

(comment (def db-spec {:dbtype "mssql"
               :host (get-param "lag4host")
               :dbname (get-param "lag4db")
               :user (get-param "lag4user")
               :password (get-param "lag4pw")}))

(def queue (delay (sqs/find-queue "import-queue-iceeog-dev")))

(defn start [in]
  (let [vurids (j/query db-spec ["select distinct(vurderingsejendom_id_ice) from vurderingsejendom"])]
    (doall (pmap #(sqs/send-message @queue (:vurderingsejendom_id_ice %)) vurids))))

(def -handleRequest (mk-req-handler start))
