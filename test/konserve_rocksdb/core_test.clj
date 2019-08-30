(ns konserve-rocksdb.core-test
  (:require [clojure.test :refer :all]
            [konserve.core :as k]
            [konserve-rocksdb.core :refer :all]
            [clj-rocksdb :as rocks]
            [clojure.core.async :refer [<!!]]))

(deftest rocksdb-store-test
  (testing "Test the rocksdb store functionality."
    (let [path "/tmp/konserve-rocksdb-test"
          _ (rocks/destroy-db path)
          store (<!! (new-rocksdb-store path))]
      (is (= (<!! (k/exists? store :foo))
             false))
      (<!! (k/assoc-in store [:foo] nil))
      (is (= (<!! (k/get-in store [:foo]))
             nil))
      (<!! (k/assoc-in store [:foo] :bar))
      (is (= (<!! (k/exists? store :foo))
             true))
      (is (= (<!! (k/get-in store [:foo]))
             :bar))
      (<!! (k/dissoc store :foo))
      (is (= (<!! (k/get-in store [:foo]))
             nil))
      (<!! (k/bassoc store :binbar (byte-array (range 10))))
      (<!! (k/bget store :binbar (fn [{:keys [input-stream]}]
                                   (is (= (map byte (slurp input-stream))
                                          (range 10))))))
      (release store))))


(comment
  (def path "/tmp/rocksdb-test2")

  (when store
    (release store))
  (rocks/destroy-db path)
  (def store (<!! (new-rocksdb-store path)))



  (<!! (k/exists? store :foo)))

