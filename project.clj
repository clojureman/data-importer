(defproject data-importer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.7.6"]
                 [com.microsoft.sqlserver/mssql-jdbc "6.5.4.jre10-preview" :exclusions [commons-codec]]
                 [com.amazonaws/aws-lambda-java-core "1.2.0"]
                 [cheshire "5.6.3"]
                 [com.rpl/specter "1.1.1"]
                 [clj-time "0.14.4"]
                 [danlentz/clj-uuid "0.1.7"]
                 [clojure.jdbc/clojure.jdbc-c3p0 "0.3.3"]
                 [com.microsoft.sqlserver/mssql-jdbc "6.3.6.jre8-preview"]
                 [amazonica "0.3.128" :exclusions [com.amazonaws/aws-java-sdk
                                                   com.amazonaws/amazon-kinesis-client]]
                 [com.amazonaws/aws-java-sdk-core "1.11.361"]
                 [com.amazonaws/aws-java-sdk-sqs "1.11.361"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.361"]
                 [aero "1.1.3"]
                 [com.amazonaws/aws-java-sdk-ssm "1.11.361"]]
  :uberjar-exclusions [#".*-model\.json" #".*-intermediate\.json"]
  :aot :all)
