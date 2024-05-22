(ns da2dtlv.core
  (:require [datomic.api :as da]
            [datalevin.core :as dtlv]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.pprint :refer [pprint]])
  (:import [java.util.logging Level Logger])
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
  [da-db]
  (let [data (da/q '[:find [(pull ?e [*]) ...]
                     :where
                     [?e :db/ident]
                     [(< 71 ?e)]]
                   da-db)
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
    (log/info  "Schema loaded")
    (log/debug "Schema:\t" (with-out-str (pprint schema)))
    schema))

(defn- create-data-query
  [attrs]
  `[:find [(~'pull ~'?e [~'*]) ...]
    :where
    (~'or ~@(map (fn [a] `[~'?e ~a]) attrs))])

(defn- get-datomic-connection
  [da-uri]
  (let [da-conn (da/connect da-uri)]
    (log/info  "Datomic connected")
    (log/debug "Datomic connection:]\t" (with-out-str (pprint da-conn)))
    da-conn))

(defn- get-data
  [da-db schema]
  (let [data-query (create-data-query (keys schema))
        data       (da/q data-query da-db)]
    (log/info  "Data loaded")
    (log/debug "Data:\t" (with-out-str (pprint data)))
    data))

(defn- get-datalevin-connection
  [dtlv-uri schema options]
  (let [dtlv-conn (dtlv/get-conn dtlv-uri schema options)]
    (log/info  "Datalevin connected")
    (log/debug "Datalevin connection:\t" (with-out-str (pprint dtlv-conn)))
    dtlv-conn))

(defn datomic->datalevin

  "Commment"

  {:added "0.1.0"}

  ([da-uri dtlv-uri] (datomic->datalevin da-uri dtlv-uri {}))
  ([da-uri dtlv-uri optm]
   (let [da-conn    (get-datomic-connection da-uri)
         da-db      (da/db da-conn)
         schema     (get-schema da-db)
         data       (get-data da-db schema)
         options    (merge {:validate-data? true :closed-schema? true} optm)
         dtlv-conn  (get-datalevin-connection dtlv-uri schema options)]
     (dtlv/transact! dtlv-conn data))))

(def ^:private cli-options
  [[nil "--no-validate-data" "Set Datalevin database :validate-data? option to false" :id :no-validate-data]
   [nil "--no-closed-schema" "Set Datalevin database :closed-schema? option to false" :id :no-closed-schema]
   ["-v" "--verbose" "Show detailed log messages" :id :verbose]
   ["-q" "--quite" "Don't show any output" :id :quite]
   ["-h" "--help" "Show this help" :id :help]])

(defmacro ^:private print-help
  [summary]
  (let [command (System/getProperty "da2dtlv.exec-command")]
    `(println (format "Usage: %s [options] <Datomic URI> <Datalevin URI>\n\n%s" ~command ~summary))))

(defn ^:no-doc -main
  [& args]
  (let [logger (Logger/getLogger "da2dtlv.core")
        {:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)            (print-help summary)
      errors                     (do (println "Errors:" errors)
                                     (System/exit 1))
      (not= 2 (count arguments)) (do (println "Error: provide exactly two arguments!\n")
                                     (print-help summary)
                                     (System/exit 1))
      :else (do
              (when (:verbose options)
                (.setLevel logger Level/ALL))
              (when (:quite options)
                (.setLevel logger Level/SEVERE))
              (datomic->datalevin (first arguments)
                                  (second arguments)
                                  {:validate-data? (not (:no-validate-data options))
                                   :closed-schema? (not (:no-closed-schema options))})))))
