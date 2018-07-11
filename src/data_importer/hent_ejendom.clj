(ns data-importer.hent-ejendom
  (:gen-class
   :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler])
  (:require [amazonica.aws.dynamodbv2 :as ddb]
            [clojure.java.io :as io]
            [cheshire.core :refer :all]
            [clj-uuid :as uuid]
            [clj-time.coerce :as c]
            [clojure.string :as s]
            [com.rpl.specter :refer :all]
           ; [amazonica.aws.sns :as sns]
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


(def schema
  {:vurderingsejendom
   {:adresse       {:afstand_skov []
                    :afstand_soe  []
                    :afstand_hoejspaending []
                    :afstand_jernbane []
                    :afstand_kystlinie []
                    :afstand_samletskov []
                    :afstand_togstation []
                    :afstand_vandloeb []
                    :afstand_vej []
                    :afstand_vindmoelle []}
    :bfe {:sfe [
                {:jordstykke [
                              {:tekniskanlaeg []
                               :plandata []
                               :strandbeskyttelse []
                               :klitfredning []
                               :majoratskov []
                               :stormfald []
                               :fredskov []
                               :bygning [
                                         {:tekniskanlaeg []
                                          :etage [
                                                  {:enhed []}]}]}]}]
          :bfg [
                {:bygning [
                           {:tekniskanlaeg []
                            :etage [
                                    {:enhed []}]}]
                 :tekniskanlaeg []}]
          :ejerlejlighed [
                          {:bygning []
                           :tekniskanlaeg []
                           :enhed []}]}}})

(def kf (memoize #(keyword (str % "_id_ice"))))

(defn hent-ejendom [vurid ts]
  (let [l (:items
           (ddb/query
            :table-name "vurejendomme"
            :select "ALL_ATTRIBUTES"
            :scan-index-forward true
            :key-conditions {:vurderingsejendom_id_ice  {:attribute-value-list [vurid] :comparison-operator "EQ"}}
            :filter-expression "#vf < :vf and #vt > :vt"
            :expression-attribute-names {"#vf" "virkning_fra" "#vt" "virkning_til"}
            :expression-attribute-values {":vf" ts ":vt" ts}))]
    (map #(last (sort-by :db_indsat (val %))) (group-by (juxt :table #((kf (:table %)) %)) l))))

(def table-key (memoize #(keyword (str (name %) "_id_ice"))))

(defn normalize-schema [x]
      (let [[multi x] (if (vector? x)
                        [true (first x)]
                        [nil x])]
           (merge (when multi {:multi multi})
                  (when (seq x)
                        {:v (into {} (map (fn [[k v]] {k (normalize-schema v)}) x))}))))

(defn table-rows [data table join-fn]
      (->> (when table data)
           (filter (comp #{table} keyword :table))
           (filter join-fn)))

(defn assemble' [data {:keys [v]} context]
      (let [join-fn #(every? (fn [[k v]]
                                 (let [v0 (get % k)]
                                      (or (nil? v0)
                                          (= v v0))))
                             context)]
           (into {}
                 (map (fn [[t schema]]
                          (let [multi (:multi schema)
                                tbl-key (table-key t)
                                f (fn [row]
                                      (merge row
                                             (assemble' data schema (assoc context tbl-key (tbl-key row)))))
                                rows (table-rows data t join-fn)
                                xs (if multi
                                     (mapv f rows)
                                     (when-let [row (first rows)] (f row)))]
                               (when (seq xs) {t xs})))
                      v))))

(defn assemble [data]
  (prn "IN" data)
  (let [res  (encode (assemble' (hent-ejendom (data :vurid) (data :virknings-tid)) (normalize-schema schema) {}))]
    res))

(def -handleRequest (mk-req-handler assemble))
