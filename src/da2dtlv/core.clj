(ns da2dtlv.core
  (:require [datomic.api :as da]
            [datalevin.core :as dtlv]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log])
  (:gen-class))

(def ^:private schema-query '[:find ?ident ?attr ?value
                              :where
                              [?e :db/ident ?ident]
                              [(namespace ?ident) ?ns]
                              [(clojure.string/starts-with? ?ns "db.") ?is-db]
                              [(not ?is-db)]
                              [(not= ?ns "db")]
                              [(not= ?ns "fressian")]
                              [?e ?attr ?value]])

(defn- get-schema
  [da-db cnt]
  (let [data (da/q '[:find [(pull ?e [*]) ...]
                     :in $ ?cnt
                     :where
                     [?e :db/ident]
                     [(< ?cnt ?e)]]
                   da-db cnt)
        schema (reduce (fn [acc e]
                         (let [k (:db/ident e)
                               v (as-> e $
                                   (dissoc $
                                           :db/ident
                                           :db.entity/attrs
                                           :db.entity/preds
                                           :db/ensure
                                           :db/id)
                                   (map (fn [[k' v']]
                                          (if (and (map? v') (contains? v' :db/id))
                                            [k' (ffirst (da/q '[:find ?i
                                                                :in $ ?e
                                                                :where
                                                                [?e :db/ident ?i]]
                                                              da-db (:db/id v')))]
                                            [k' v'])) $)
                                   (into {} $))]
                           (if (or (= (name k) "validate")
                                   (empty? v))
                             acc (assoc acc k v)))) {} data)]
    (log/info "Schema loaded:\t" (with-out-str (pprint schema)))
    schema))

(defn- create-data-query
  [attrs]
  `[:find [(~'pull ~'?e [~'*]) ...]
    :where
    (~'or ~@(map (fn [a] `[~'?e ~a]) attrs))])

(defn- get-datomic-connection
  [da-uri]
  (let [da-conn (da/connect da-uri)]
    (log/info "Datomic connected:]\t" (with-out-str (pprint da-conn)))
    da-conn))

(defn- -prepare-data
  [acc num cnt data]
  (loop [a acc
         n num
         d data]
    (if (empty? d)
      a
      (let [a' (conj a (->> (first d)
                            (map (fn [[k v]]
                                   (cond
                                     (and (map? v) (contains? v :db/id))
                                     [k (:db/id v)]

                                     (vector? v)
                                     [k (mapv #(if (nil? %) (prn-str %) %) v)]
                                     
                                     :else [k v])))
                            (into {})))]
        (print (format "%d / %d\r" (inc n) cnt))
        (recur a' (inc n) (rest d))))))

(defn- prepare-data
  [data]
  (let [data' (-prepare-data [] 0 (count data) data)]
    (log/info "Data prepared:\t" (with-out-str (pprint data')))
    data'))

(defn- get-data
  [da-db schema]
  (let [data-query (create-data-query (keys schema))
        data       (da/q data-query da-db)]
    (log/info "Data loaded:\t" (with-out-str (pprint data)))
    data))

(defn- get-datalevin-connection
  [dtlv-uri schema options]
  (let [dtlv-conn (dtlv/get-conn dtlv-uri schema options)]
    (log/info "Datalevin connected:\t" (with-out-str (pprint dtlv-conn)))
    dtlv-conn))

(defn datomic->datalevin

  "Commment"

  {:added "0.1.0"}

  ([da-uri dtlv-uri] (datomic->datalevin da-uri dtlv-uri {}))
  ([da-uri dtlv-uri optm]
   (let [da-conn       (get-datomic-connection da-uri)
         optm'         (as-> optm $
                         (merge {:validate-data? true
                                 :closed-schema? true
                                 :system-datoms-count 71} $)
                         (select-keys $ [:validate-data? :closed-schema? :system-datoms-count]))
         [schema data] (try
                         (let [da-db   (da/db da-conn)
                               schema' (get-schema da-db (:system-datoms-count optm'))
                               data'   (get-data da-db schema')]
                           [schema' data'])
                         (finally (da/release da-conn)))]
     (let [data'     (prepare-data data)
           options   (dissoc optm' :system-datoms-count)
           dtlv-conn (get-datalevin-connection dtlv-uri schema options)]
       (dtlv/transact! dtlv-conn data')
       (log/infof "Transfer complete: %d datoms" (count (dtlv/datoms (dtlv/db dtlv-conn) :eav)))))))

(def ^:private cli-options
  [["-d" "--datoms DATOMS" "Count of system datoms in Datomic database"
    :default 71
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 0 %) "Must be not a negative number"]]
   [nil "--no-validate-data" "Set Datalevin database :validate-data? option to false" :id :no-validate-data]
   [nil "--no-closed-schema" "Set Datalevin database :closed-schema? option to false" :id :no-closed-schema]
   ["-h" "--help" "Show this help" :id :help]])

(defmacro ^:private print-help
  [summary]
  (let [command (System/getProperty "da2dtlv.exec-command")]
    `(println (format "Usage: %s [options] <Datomic URI> <Datalevin URI>\n\n%s" ~command ~summary))))

(defn ^:no-doc -main
  [& args]
  (let [{:keys [options arguments errors summary] :as m} (parse-opts args cli-options)]
    (cond
      (:help options)            (print-help summary)
      errors                     (do (println "Errors:" errors)
                                     (System/exit 1))
      (not= 2 (count arguments)) (do (println "Error: provide exactly two arguments!\n")
                                     (print-help summary)
                                     (System/exit 1))
      :else (datomic->datalevin (first arguments)
                                (second arguments)
                                {:validate-data? (not (:no-validate-data options))
                                 :closed-schema? (not (:no-closed-schema options))
                                 :system-datoms-count (:datoms options)}))))
