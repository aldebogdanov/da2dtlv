(ns da2dtlv.core-test
  (:require [clojure.test :refer :all]
            [da2dtlv.core :refer :all]
            [datomic.api :as da]))

(def datomic-uri "datomic:mem://test")

(da/create-database datomic-uri)

(def uuid1 (java.util.UUID/randomUUID))
(def uuid2 (java.util.UUID/randomUUID))

(def schema
  [{:db/ident :ent1/atr1
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Doc-1-1"}
   
   {:db/ident :ent1/atr2
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Doc-1-2"}
   
   {:db/ident :ent1/atr3
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Doc-1-3"}
   
   {:db/ident :ent1/atr4
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/many
    :db/doc "Doc-1-4"}
   
   {:db/ident :ent1/validate
    :db.entity/attrs [:ent1/atr1 :ent1/atr3 :ent1/atr4]}
   
   {:db/ident :ent2/atr1
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Doc-2-1"}
   
   {:db/ident :ent2/atr2
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "Doc-2-2"}])

(def data-1
  [{:ent2/atr1 uuid1
    :ent2/atr2 42.01M}

   {:ent2/atr1 uuid2}])

(def data-2  
  [{:ent1/atr1 "A"
    :ent1/atr2 42
    :ent1/atr3 [:ent2/atr1 uuid1]
    :ent1/atr4 [:a :b :c]}

   {:ent1/atr1 "B"
    :ent1/atr3 [:ent2/atr1 uuid2]
    :ent1/atr4 [:d]}

   {:ent1/atr1 "C"
    :ent1/atr3 [:ent2/atr1 uuid2]
    :ent1/atr4 []}])

(def schema-query '[:find ?ident ?attr ?value
                    :where
                    [?e :db/ident ?ident]
                    [(namespace ?ident) ?ns]
                    [(clojure.string/starts-with? ?ns "db.") ?is-db]
                    [(not ?is-db)]
                    [(not= ?ns "db")]
                    [(not= ?ns "fressian")]
                    [?e ?attr ?value]])

(deftest a-test
  (testing "Research"
    (let [conn (da/connect datomic-uri)]
      (da/transact conn schema)
      (da/transact conn data-1)
      (da/transact conn data-2))
    (->> datomic-uri
        da/connect
        da/db
        #_(da/q '[:find (pull ?e [*])
                :in $
                  :where [?e :db/ident]])
        (da/q schema-query)
        clojure.pprint/pprint)))
