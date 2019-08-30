(defproject io.replikativ/konserve-rocksdb "0.1.0"
  :description "A RocksDB backend for konserve."
  :url "https://github.com/purrgrammer/konserve-rocksdb"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [io.replikativ/konserve "0.5.0"]
                 [byte-streams "0.2.2"]
;;                [org.rocksdb/rocksdbjni "5.5.1"]
                 [kotyo/clj-rocksdb "0.1.3" :exclusions [byte-streams]]])

