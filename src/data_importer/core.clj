0(ns data-importer.core
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
            [clojure.java.jdbc :as j]
            [aero.core :as ac]
            [jdbc.pool.c3p0 :as pool]
            [amazonica.aws.simplesystemsmanagement :as ssm])
  (:import [com.amazonaws.services.simplesystemsmanagement AWSSimpleSystemsManagementClientBuilder]
           [com.amazonaws.services.simplesystemsmanagement.model GetParametersRequest]))

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

(defn get-param [p enc?]
  (get-in (ssm/get-parameter :name p :with-decryption enc?) [:parameter :value]))

(def spec (delay {:classname "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                  :subprotocol "sqlserver"
                  :user (get-param "lag4user" false)
                  :password (get-param "lag4pw" false)
                  :min-pool-size 10
                  :max-pool-size 40
                  :subname (str "//" (get-param "lag4host" false)  ";databaseName="  (get-param "lag4db" false))}))

(def db-spec
  (delay
   (pool/make-datasource-spec @spec)))

(defn update-map [m f]
  (reduce-kv (fn [m k v]
               (assoc m k (f v))) {} m))

(defn inst-to-long [val]
  (if (or (= java.sql.Date (type val))  (= java.sql.Timestamp (type val))) (c/to-long val) val))

(defn get-id [id l]
  (first (filter #(not (nil? %)) (set (map #(get-in % [:put-request :item id ]) l)))))

(defn insert-ejd [vurid]
  (let [vur (map #(hash-map :put-request (hash-map :item  (assoc (update-map % inst-to-long) :table "vurderingsejendom" :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from vurderingsejendom where vurderingsejendom_id_ice = ?" vurid]))
                                        ; adresse (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "adresse" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from adresse a1 where adresse_id_ice = ? and db_indsat = (select max(db_indsat) from adresse a2 where a1.virkning_fra = a2.virkning_fra and a2.adresse_id_ice = ?)" (get-id :adresse_id_ice vur) (get-id :adresse_id_ice vur)]))
        adresse (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "adresse" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from adresse where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        bfe (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "bfe" :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from bfe where vurderingsejendom_id_ice = ?" vurid]))
        salg (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "salg" :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from salg where vurderingsejendom_id_ice = ?" vurid]))
        salg-flag (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "salg_flag" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from salg_flag where salg_id_ice = ?" (get-id :salg_id_ice salg)]))
        sfe (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "sfe" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from sfe where bfe_id_ice = ?" (get-id :bfe_id_ice bfe)]))
        bfg (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "bfg" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from bfg where bfe_id_ice = ?" (get-id :bfe_id_ice bfe)]))
        ejerlejlighed (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "ejerlejlighed" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from ejerlejlighed where bfe_id_ice = ?" (get-id :bfe_id_ice bfe)]))
      ;  _ (prn "SFE" vur bfe sfe bfg ejerlejlighed)
        bygning (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "bygning" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                     (j/query @db-spec (cond
                                        (not (empty? sfe)) ["select * from bygning where sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]
                                        (not (empty? bfg)) ["select * from bygning where bfg_id_ice = ?" (get-id :bfg_id_ice bfg)]
                                        (not (empty? ejerlejlighed)) ["select * from bygning where ejerlejlighed_id_ice = ?" (get-id :ejerlejlighed_id_ice ejerlejlighed)])))
        etage (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "etage" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                   (j/query @db-spec ["select distinct(e.etage_id_ice) dummy,e.* from etage e, bygning b where e.bygning_id_ice = b.bygning_id_ice and b.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        enhed (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "enhed" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                   (j/query @db-spec (cond
                                      (not (empty? sfe)) ["select distinct(e.enhed_id_ice) dummy,e.* from enhed e, bygning b, etage et where et.bygning_id_ice = b.bygning_id_ice and et.etage_id_ice = e.etage_id_ice and b.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]
                                      (not (empty? bfg)) ["select distinct(e.enhed_id_ice) dummy,e.* from enhed e, bygning b, etage et where et.bygning_id_ice = b.bygning_id_ice and et.etage_id_ice = e.etage_id_ice and b.bfg_id_ice = ?" (get-id :bfg_id_ice bfg)]
                                      (not (empty? ejerlejlighed)) ["select distinct(e.enhed_id_ice) dummy,e.* from enhed e, ejerlejlighed el where el.ejerlejlighed_id_ice = e.ejerlejlighed_id_ice and el.ejerlejlighed_id_ice = ?" (get-id :ejerlejlighed_id_ice ejerlejlighed)])))
        jordstykke (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "jordstykke" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from jordstykke where sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        tekanl (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "tekniskanlaeg" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                    (j/query @db-spec (cond
                                       (not (empty? sfe)) ["select * from tekniskanlaeg where sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]
                                       (not (empty? bfg)) ["select * from tekniskanlaeg where bfg_id_ice = ?" (get-id :bfg_id_ice bfg)]
                                       (not (empty? ejerlejlighed)) ["select * from tekniskanlaeg where ejerlejlighed_id_ice = ?" (get-id :ejerlejlighed_id_ice ejerlejlighed)])))
        byg-flag (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "bygning_flag" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                      (j/query @db-spec
                               (cond
                                 (not (empty? sfe)) ["select distinct(bf.flag_id_ice) dummy,bf.* from bygning_flag bf, bygning b where bf.bygning_id_ice = b.bygning_id_ice and b.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]
                                 (not (empty? bfg)) ["select distinct(bf.flag_id_ice) dummy,bf.* from bygning_flag bf, bygning b where bf.bygning_id_ice = b.bygning_id_ice and b.bfg_id_ice = ?" (get-id :bfg_id_ice bfg)]
                                 (not (empty? ejerlejlighed)) ["select distinct(bf.flag_id_ice) dummy,bf.* from bygning_flag bf, bygning b where bf.bygning_id_ice = b.bygning_id_ice and b.ejerlejlighed_id_ice = ?" (get-id :ejerlejlighed_id_ice ejerlejlighed)])))
        etage-flag (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "etage_flag" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                        (j/query @db-spec ["select distinct(ef.flag_id_ice) dummy,e.* from etage e, etage_flag ef, bygning b where e.bygning_id_ice = b.bygning_id_ice and e.etage_id_ice = ef.etage_id_ice and b.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        enhed-flag (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "enhed_flag" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                        (j/query @db-spec (cond
                                           (not (empty? sfe)) ["select distinct(ef.flag_id_ice) dummy,e.* from enhed_flag ef,etage et, enhed e, bygning b where et.bygning_id_ice = b.bygning_id_ice and e.etage_id_ice = et.etage_id_ice and ef.enhed_id_ice = e.enhed_id_ice and b.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]
                                           (not (empty? bfg)) ["select distinct(ef.flag_id_ice) dummy,e.* from enhed_flag ef,etage et, enhed e, bygning b where et.bygning_id_ice = b.bygning_id_ice and e.etage_id_ice = et.etage_id_ice and ef.enhed_id_ice = e.enhed_id_ice and b.bfg_id_ice = ?" (get-id :bfg_id_ice bfg)]
                                           (not (empty? ejerlejlighed)) ["select distinct(ef.flag_id_ice) dummy,e.* from enhed_flag ef,etage et, enhed e, bygning b where et.bygning_id_ice = b.bygning_id_ice and e.etage_id_ice = et.etage_id_ice and ef.enhed_id_ice = e.enhed_id_ice and b.ejerlejlighed_id_ice = ?" (get-id :ejerlejlighed_id_ice ejerlejlighed)])))
        afs-hoej (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_hoejspaending" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_hoejspaending where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-jern (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_jernbane" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_jernbane where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-kyst (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_kystlinie" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_kystlinie where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-samskov (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_samletskov" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_samletskov where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-skov (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_skov" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_skov where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-soe (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_soe" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_soe where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-tog (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_togstation" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_togstation where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-vand (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_vandloeb" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_vandloeb where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-vej (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_vej" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_vej where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        afs-vind (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "afstand_vindmoelle" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1))))) (j/query @db-spec ["select * from afstand_vindmoelle where adresse_id_ice = ?" (get-id :adresse_id_ice vur)]))
        plandata (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "plandata" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                      (j/query @db-spec ["select distinct(p.plandata_id_ice) dummy,p.* from plandata p, jordstykke j where p.jordstykke_id_ice = j.jordstykke_id_ice and j.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        strandbeskyttelse (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "strandbeskyttelse" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                               (j/query @db-spec ["select distinct(p.strandbeskyttelse_id_ice) dummy,p.* from strandbeskyttelse p, jordstykke j where p.jordstykke_id_ice = j.jordstykke_id_ice and j.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        klitfredning (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "klitfredning" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                          (j/query @db-spec ["select distinct(p.klitfredning_id_ice) dummy,p.* from klitfredning p, jordstykke j where p.jordstykke_id_ice = j.jordstykke_id_ice and j.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        majoratskov (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "majoratskov" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                         (j/query @db-spec ["select distinct(p.majoratskov_id_ice) dummy,p.* from majoratskov p, jordstykke j where p.jordstykke_id_ice = j.jordstykke_id_ice and j.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        stormfald (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "stormfald" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                       (j/query @db-spec ["select distinct(p.stormfald_id_ice) dummy,p.* from stormfald p, jordstykke j where p.jordstykke_id_ice = j.jordstykke_id_ice and j.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        fredskov (map #(hash-map :put-request (hash-map :item (assoc (update-map % inst-to-long) :table "fredskov" :vurderingsejendom_id_ice vurid :uuid (str (uuid/v1)))))
                      (j/query @db-spec ["select distinct(p.fredskov_id_ice) dummy,p.* from fredskov p, jordstykke j where p.jordstykke_id_ice = j.jordstykke_id_ice and j.sfe_id_ice = ?" (get-id :sfe_id_ice sfe)]))
        items (vec (concat vur adresse bfe sfe bfg ejerlejlighed bygning etage enhed jordstykke tekanl byg-flag etage-flag enhed-flag afs-hoej afs-jern afs-kyst afs-samskov afs-skov afs-soe afs-tog afs-vand afs-vej afs-vind plandata strandbeskyttelse klitfredning majoratskov stormfald fredskov salg salg-flag))
        sitems (loop [i items s []]
                 (if (empty? i)
                   s
                   (recur (drop-last 25 i) (conj s (take-last 25 i)))))]
    ;(prn "ITEMS" sitems)
    ;(count res)
    (mapv #(ddb/batch-write-item :return-consumed-capacity "TOTAL"
                                 :return-item-collection-metrics "SIZE"
                                 :request-items {"vurejendomme" (vec %)})
                  sitems)))

(defn import-data [data]
  (doall (pmap #(insert-ejd (Integer/parseInt (:body %))) (:Records data))))


(def -handleRequest (mk-req-handler import-data))

;;(ddb/scan cred :tablename "vurejendomme" :projection-expression "vurderingsejendom_id_ice, #u" :expression-attribute-names {"#u" "uuid"})

;;(ddb/delete-item cred :tablename "vurejendomme" :key {:vurderingsejendom_id_ice 18570513 :uuid "b1305050-850e-11e8-b02f-2a61c6c19b33"})

;;(pmap #(ddb/delete-item cred :tablename "vurejendomme" :key {:vurderingsejendom_id_ice (:vurderingsejendom_id_ice %) :uuid (:uuid %)}) (:items x))
