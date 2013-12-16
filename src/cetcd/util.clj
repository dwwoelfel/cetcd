(ns cetcd.util)

(defn apply-map
  "Takes a fn and any number of arguments. Applies the arguments like
  apply, except that the last argument is converted into keyword
  pairs, for functions that keyword arguments.

  (apply-map foo :a :b {:c 1 :d 2}) => (foo :a :b :c 1 :d 2)"
  [f & args*]
  (let [normal-args (butlast args*)
        m (last args*)]
    (when m
      (assert (map? m) "last argument must be a map"))
    (apply f (concat normal-args (apply concat (seq m))))))
