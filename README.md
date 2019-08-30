# konserve-rocksdb

A RocksDB backend for [konserve](https://github.com/replikativ/konserve).

## Usage

The purpose of konserve is to have a unified associative key-value interface for
edn datastructures and binary blobs. Use the standard interface functions of konserve.

You can provide rocksdb configuration options to the `new-rocksdb-store`
constructor as an `:config` argument. We do not require additional settings
beyond the konserve serialization protocol for the store, so you can still
access the store through `clj-rocksdb` directly wherever you need (e.g. for
store deletion).

~~~clojure
  (require '[konserve-rocksdb.core :refer :all]
           '[konserve.core :as k)
  (def rocksdb-store (<!! (new-rocksdb-store "/tmp/konserve-rocksdb-test")))

  (<!! (k/exists? rocksdb-store  "john"))
  (<!! (k/get-in rocksdb-store ["john"]))
  (<!! (k/assoc-in rocksdb-store ["john"] 42))
  (<!! (k/update-in rocksdb-store ["john"] inc))
  (<!! (k/get-in rocksdb-store ["john"]))
  
  (<!! (release store)) ;; there is only one instance per store allowed at a time
~~~


## License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
