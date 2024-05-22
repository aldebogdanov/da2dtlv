(ns da2dtlv.core-test
  (:require [clojure.test :refer :all]
            [da2dtlv.core :refer :all]
            [datomic.api :as da]
            [datalevin.core :as dtlv]))

(def datomic-uri "datomic:mem://test")
(def datalevin-uri (str "/tmp/da2dtlv-" (java.util.UUID/randomUUID)))

(da/create-database datomic-uri)

(def datomic-conn (da/connect datomic-uri))

(defn datomic-db
  []
  (da/db datomic-conn))

(def uuid-1 (java.util.UUID/randomUUID))
(def uuid-2 (java.util.UUID/randomUUID))

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
  [{:ent2/atr1 uuid-1
    :ent2/atr2 42.01M}

   {:ent2/atr1 uuid-2}])

(def data-2  
  [{:ent1/atr1 "A"
    :ent1/atr2 42
    :ent1/atr3 [:ent2/atr1 uuid-1]
    :ent1/atr4 [:a :b :c]}

   {:ent1/atr1 "B"
    :ent1/atr3 [:ent2/atr1 uuid-2]
    :ent1/atr4 [:d]}

   {:ent1/atr1 "C"
    :ent1/atr3 [:ent2/atr1 uuid-2]
    :ent1/atr4 []}])

(def test-query '[:find (pull ?e [* {:ent1/atr3 [*]}])
                  :where [?e :ent1/atr1]])

(def expected-value [[{:ent1/atr1 "A",
                       :ent1/atr2 42,
                       :ent1/atr3
                       {:ent2/atr1 uuid-1,
                        :ent2/atr2 42.01M},
                       :ent1/atr4 [:a :b :c]}]
                     [{:ent1/atr1 "B",
                       :ent1/atr3
                       {:ent2/atr1 uuid-2},
                       :ent1/atr4 [:d]}]
                     [{:ent1/atr1 "C",
                       :ent1/atr3
                       {:ent2/atr1 uuid-2}}]])


(defn- remove-key-recursively
  [k m]
  (clojure.walk/prewalk
   (fn [x]
     (cond
       (map? x) (dissoc x k)
       (seq? x) (mapv #(remove-key-recursively k %) x)
       :else x))
   m))

(deftest a-test
  (testing "Datomic to Datalevin migration"
    (da/transact datomic-conn schema)
    (da/transact datomic-conn data-1)
    (da/transact datomic-conn data-2)
    (let [result (remove-key-recursively :db/id (da/q test-query (datomic-db)))]
      (is (= result expected-value)))
    (datomic->datalevin datomic-uri datalevin-uri)
    (let [result (remove-key-recursively :db/id
                                         (dtlv/q test-query (dtlv/db (dtlv/get-conn datalevin-uri))))]
      (is (= result expected-value)))))
