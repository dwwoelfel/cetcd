# cetcd

A Clojure wrapper for etcd

## Usage

### Set the value to a key

```clojure
user> (require '[cetcd.core :as etcd])
nil
user> (etcd/set-key! :a 1)
{:action "set",
 :node
 {:key "/:a",
  :modifiedIndex 10,
  :createdIndex 10,
  :value "1",
  :prevValue "1"}}
```

### Get the value of a key

```clojure
user> (etcd/get-key :a)
{:action "get",
 :node {:key "/:a", :modifiedIndex 10, :createdIndex 10, :value "1"}}
```

Let's get the actual value:

```clojure
user> (-> (etcd/get-key :a) :node :value)
"1"
```

Notice that cetcd didn't preserve the type of the key's value. That's a job I left to the caller:

```clojure
user> (etcd/set-key! :clojure-key (pr-str 1))
{:action "set", :node {:key "/:clojure-key", :value "1", :modifiedIndex 14, :createdIndex 14}
user> (-> (etcd/get-key :clojure-key)
          :node
          :value
          (clojure.edn/read-string))
1

```

cetcd also doesn't do much to help you if the key doesn't exist:

```clojure
user> (etcd/get-key :b)
{:errorCode 100, :message "Key Not Found", :cause "/:b", :index 22}
```

But sane callers should be fine:
```clojure
user> (-> (etcd/get-key :b) :node :value) ;; return nil if key doesn't exist
nil
```

### Delete a key

```clojure
user> (etcd/delete-key :a)
{:action "delete",
 :node
 {:key "/:a", :modifiedIndex 11, :createdIndex 10, :prevValue "1"}}
```

### Compare and swap

Everything that etcd does can be accomplished with the three functions above, by passing in the proper keyword args. There are also a couple of helper functions to keep things a bit cleaner.

```clojure
user> (etcd/compare-and-swap! :a 2 {:prevValue 1})
{:action "compareAndSwap", :node {:key "/:a", :prevValue "1", :value "2", :modifiedIndex 15, :createdIndex 13}}
```

You have to check manually if the condition failed:

```clojure
user> (etcd/compare-and-swap! :a 2 {:prevValue 1})
{:errorCode 101, :message "Test Failed", :cause "[1 != 5] [0 != 22]", :index 22}
```

### Waiting for a value

```clojure
user> (future (println "new value is:" (-> (etcd/watch-key :a) :node :value)))
#<core$future_call$reify__6267@ddd23bc: :pending>
user> (etcd/set-key! :a 3)
new value is: 3
{:action "set", :node {:key "/:a", :prevValue "2", :value "3", :modifiedIndex 16, :createdIndex 16}}
```

If you provide a callback function, then it will return immediately with a promise:

```clojure
user> (def watchvalue (atom nil)) ;; give us a place store the resp in the callback
#'user/watchvalue
user> (etcd/watch-key :a :callback (fn [resp]
                                       (reset! watchvalue resp)))
#<core$promise$reify__6310@144d3f4b: :pending>
user> (etcd/set-key! :a 4)
{:action "set", :node {:key "/:a", :prevValue "3", :value "4", :modifiedIndex 20, :createdIndex 20}}
user> watchvalue
#<Atom@69bcc736: {:action "set", :node {:key "/:a", :prevValue "3", :value "4", :modifiedIndex 20, :createdIndex 20}}>
```









As you may have noticed from the return values of the functions, I haven't tried to hide the etcd response. The reason is that nodes are r

There are three main functions that do all of the work `set-key!`, `get-key`, and `delete-key`.

## License

Copyright © 2013

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
