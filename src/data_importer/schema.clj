(ns data-importer.schema)

(def schema1
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

(def schema2
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
    :sfe [
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
                     :enhed []}]}})

(def schema3
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
    :salg [{:salg_flag []}]
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

;; Just here to remind which tables are in play
(def tables ["vurderingsejendom"
             "adresse"
             "bfe"
             "bfg"
             "bygning"
             "bygning_flag"
             "ejerlejlighed"
             "enhed"
             "enhed_flag"
             "etage"
             "etage_flag"
             "jordstykke"
             "sfe"
             "tekniskanlaeg"
             "afstand_hoejspaending"
             "afstand_jernbane"
             "afstand_kystlinie"
             "afstand_samletskov"
             "afstand_skov"
             "afstand_soe"
             "afstand_togstation"
             "afstand_vandloeb"
             "afstand_vej"
             "afstand_vindmoelle"
             "plandata"
             "strandbeskyttelse"
             "klitfredning"
             "majoratskov"
             "stormfald"
             "fredskov"
             "salg"
             "salg_flag"])
