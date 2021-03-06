(ns konserve-rocksdb.core
  (:require [clj-rocksdb :as rocks :refer [create-db destroy-db]]
            [clojure.core.async :as async :refer [<!! chan close! go put!]]
            [clojure.java.io :as io]
            [byte-streams :as bs]
            [hasch.core :refer [uuid]]
            [konserve
             [protocols :refer [-bassoc -bget -deserialize -exists?
                                -get-in -serialize -update-in
                                PBinaryAsyncKeyValueStore PEDNAsyncKeyValueStore]]
             [serializers :as ser]]
            [konserve.core :as k])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defrecord RocksDBStore [rdb serializer read-handlers write-handlers locks]
  PEDNAsyncKeyValueStore
  (-exists? [this key]
    ;; trick with iterator to not read values
    (let [res (chan)]
      (try
        (let [id (str (uuid key))
              it (rocks/iterator rdb (bs/to-byte-array id))
              its (when-let [k (ffirst it)]
                    (String. k))]
          (.close it)
          (put! res (= its id)))
        (catch java.util.NoSuchElementException e
          (put! res false))
        (catch Exception e
          (put! res (ex-info "Could not search for key."
                             {:type :exist-error
                              :key key
                              :exception e})))
        (finally
          (close! res)))
      res))


  (-get-in [this key-vec]
    (let [[fkey & rkey] key-vec
          id (str (uuid fkey))
          val (rocks/get rdb id)]
      (if (= val nil)
        (go nil)
        (let [res-ch (chan)]
          (try
            (let [bais (ByteArrayInputStream. val)]
              (if-let [res (get-in
                              (second (-deserialize serializer read-handlers bais))
                              rkey)]
                (put! res-ch res)))
            (catch Exception e
              (put! res-ch (ex-info "Could not read key."
                                   {:type :read-error
                                    :key fkey
                                    :exception e})))
            (finally
              (close! res-ch)))
          res-ch))))

  (-update-in [this key-vec up-fn]
    (let [[fkey & rkey] key-vec
          id (str (uuid fkey))]
      (let [res-ch (chan)]
        (try
          (let [old-bin (rocks/get rdb id)
                old (when old-bin
                      (let [bais (ByteArrayInputStream. old-bin)]
                        (second (-deserialize serializer write-handlers bais))))
                new (if (empty? rkey)
                      (up-fn old)
                      (update-in old rkey up-fn))]
            (let [baos (ByteArrayOutputStream.)]
              (-serialize serializer baos write-handlers [key-vec new])
              (rocks/put rdb id (.toByteArray baos)))
            (put! res-ch [(get-in old rkey)
                          (get-in new rkey)]))
          (catch Exception e
            (put! res-ch (ex-info "Could not write key."
                                  {:type :write-error
                                   :key fkey
                                   :exception e})))
          (finally
            (close! res-ch)))
        res-ch)))

  (-assoc-in [this key-vec val] (-update-in this key-vec (fn [_] val)))

  (-dissoc [this key]
    (let [id (str (uuid key))
          res-ch (chan)]
      (try
        (rocks/delete rdb id)
        (catch Exception e
          (put! res-ch (ex-info "Could not delete key."
                                {:type :delete-error
                                 :key key
                                 :exception e})))
        (finally
          (close! res-ch)))
      res-ch))

  PBinaryAsyncKeyValueStore
  (-bget [this key locked-cb]
    (let [id (str (uuid key))
          val (rocks/get rdb id)]
      (if (nil? val)
        (go nil)
        (go
          (try
            (let [bais (ByteArrayInputStream. val)]
              (locked-cb {:input-stream bais
                          :size (count val)}))
            (catch Exception e
              (ex-info "Could not read key."
                       {:type :read-error
                        :key key
                        :exception e})))))))

  (-bassoc [this key input]
    (let [id (str (uuid key))]
      (go
        (try
          (rocks/put rdb id input)
          nil
          (catch Exception e
            (ex-info "Could not write key."
                     {:type :write-error
                      :key key
                      :exception e})))))))



(defn new-rocksdb-store
  [path & {:keys [rocksdb-opts serializer read-handlers write-handlers]
           :or {serializer (ser/fressian-serializer)
                read-handlers (atom {})
                write-handlers (atom {})
                rocksdb-opts {}}}]
  (go (try
        (let [db (create-db path rocksdb-opts)]
          (map->RocksDBStore {:rdb db
                              :read-handlers read-handlers
                              :write-handlers write-handlers
                              :serializer serializer
                              :locks (atom {})}))
        (catch Exception e
          e))))


(defn release
  "Close the underlying RocksDB store. This cleanup is necessary as only one
  instance of RocksDB is allowed to work on a store at a time."
  [store]
  (.close (-> store :rdb)))

(defn delete-store
  "Removes store from disk."
  [path]
  (destroy-db path))

(comment

  (def store (<!! (new-rocksdb-store "/tmp/rocksdb3")))

  (require '[konserve.core :as k])

  (get (:rdb store) (str (uuid "foo")))

  (:locks store)

  (<!! (k/get-in store ["foo"]))

  (<!! (k/exists? store "foo"))

  (<!! (k/assoc-in store ["foo"] {:foo 42}))

  (<!! (k/update-in store ["foo" :foo] inc))

  (<!! (k/bget store ["foo"] (fn [arg]
                               (let [baos (ByteArrayOutputStream.)]
                                 (io/copy (:input-stream arg) baos)
                                 (prn "cb" (vec (.toByteArray baos)))))))

  (<!! (k/bassoc store ["foo"] (byte-array [42 42 42 42 42])))


  (def db (create-db "/tmp/rocksdb" {}))

  (get db "foo")

  (put db "foo" 42)



  )
