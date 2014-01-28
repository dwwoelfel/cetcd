(ns cetcd.core-test
  (:require [clojure.test :refer :all]
            [cetcd.core :as etcd]))

(deftest set-key!-works
  (is (= "value"
         (-> (etcd/set-key! "key" "value")
             :node
             :value)))
  (testing "ttl works"
    (etcd/set-key! "key" "value" :ttl 1)
    (is (= "value"
           (-> (etcd/get-key "key") :node :value)))
    (Thread/sleep 2000)
    (is (nil? (-> (etcd/get-key "key") :node :value)))))

(deftest get-key-works
  (etcd/set-key! "key" "value")
  (is (= "value" (-> (etcd/get-key "key") :node :value)))
  (testing "directories work"
    (etcd/set-key! "test/bar" 1)
    (etcd/set-key! "test/baz" 2)
    (is (= #{"1" "2"} (->> (etcd/get-key "test")
                           :node
                           :nodes
                           (map :value)
                           set))))

  (testing "recursive directories work"
    (etcd/set-key! "recursive/test/bar" "value")
    (= "value" (-> (etcd/get-key "recursive" :recursive true)
                   :node
                   :nodes
                   first
                   :nodes
                   first
                   :value))))

(deftest delete-key!-works
  (etcd/set-key! :new-key "value")
  (is (-> (etcd/get-key :new-key) :node :value))
  (etcd/delete-key! :new-key)
  (is (nil? (-> (etcd/get-key :new-key) :node :value)))

  (testing "deleting directories works"
    (etcd/set-key! "a/b/c/d" "value")
    (is (-> (etcd/get-key "a/b/c/d") :node :value))
    (etcd/delete-key! "a" :recursive true)
    (is (nil? (-> (etcd/get-key "a/b/c/d") :node :value)))))

(deftest compare-and-swap!-works
  (etcd/delete-key! :unique-key)
  (is (-> (etcd/compare-and-swap! :unique-key "value" {:prevExist false})
          :node
          :value))
  (is (-> (etcd/compare-and-swap! :unique-key "new value" {:prevValue "value"})
          :node
          :value))
  (is (-> (etcd/compare-and-swap! :unique-key "new value" {:prevValue "value"})
          :errorCode))
  (is (-> (etcd/compare-and-swap! :unique-key "new value" {:prevExist false})
          :errorCode)))

(deftest watch-key-works
  (etcd/delete-key! "new-key")
  (let [wait-future (future (etcd/watch-key "new-key"))]
    (is (nil? (-> (etcd/get-key "new-key")
                  :node
                  :value)))

    (etcd/set-key! "new-key" "value")
    (is (= "value"
           (-> wait-future
               (deref 1000 nil)
               :node
               :value))))

  (testing "callbacks work"
    (let [result-atom (atom nil)
          watch-promise (etcd/watch-key "new-key" :callback (fn [result]
                                                              (reset! result-atom result)))]
      (etcd/set-key! "new-key" "new value")
      @watch-promise
      (is (= "new value" (-> @result-atom :node :value))))))

(deftest exceptional-errors-throw-exceptions
  (is (thrown? java.net.ConnectException
        (etcd/with-connection {:port 4002}
          (etcd/get-key "key")))))

(deftest keys-are-url-encoded
  (is (= "my value"
         (-> (etcd/set-key! "my key" "my value")
             :node
             :value)))
  (is (= "my value" (-> (etcd/get-key "my key") :node :value)))
  (etcd/delete-key! "my key")
  (is (nil? (-> (etcd/get-key "my key") :node :value))))
