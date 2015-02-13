(ns cetcd.core
  (:require [cheshire.core :as json]
            [cetcd.util :refer (apply-map)]
            [cemerick.url :refer (url-encode)]
            [org.httpkit.client :as http]
            [clojure.string :as str]
            [slingshot.slingshot :refer [throw+ try+]]))

(def default-config {:protocol "http" :host "127.0.0.1" :port 4001})

(def ^{:dynamic true} *etcd-config* default-config)

(defn set-connection!
  "Blindly copied the approach from congomongo, but without most of the protections"
  [{:keys [protocol host port]}]
  (let [config (->> {:protocol protocol :host host :port port}
                    (filter second)
                    (into {})
                    (merge default-config))]
    (alter-var-root #'*etcd-config* (constantly config))
    (when (thread-bound? #'*etcd-config*)
      (set! *etcd-config* config))))

(defmacro with-connection [config & body]
  `(do
     (let [config# (merge default-config ~config)]
       (binding [*etcd-config* config#]
         ~@body))))

(defn make-url
  "Constructs url used for all api calls"
  [& parts]
  (str (java.net.URL. (:protocol *etcd-config*)
                      (:host *etcd-config*)
                      (:port *etcd-config*)
                      (str/join "/" (concat ["/v2"] parts)))))  

(defn parse-response [resp]
  (if-let [error (-> resp :error)]
    (throw+ error)
    (-> resp
        :body
        (cheshire.core/decode true))))

(defn wrap-callback [callback]
  (fn [resp]
    (-> resp
        parse-response
        callback)))

(defn condition-args
  [args & conditions]
  (let [conditions (or conditions [:prevExist :prevValue :prevIndex])]
    (into {} (for [k conditions
                   :when (contains? args k)]
               [k (get args k)]))))

(defn api-req [method path & {:keys [callback] :as opts}]
  (let [resp (http/request (merge {:method method
                                   :url (make-url path)}
                                  opts)
                           (when callback
                             (wrap-callback callback)))]
    (if callback
      resp
      (-> @resp
          parse-response))))

(defn set-key!
  "Sets key to value, optionally takes ttl in seconds as keyword argument"
  [key value & {:keys [ttl callback dir cas order] :as opts
                :or {dir false}}]
  (api-req (if order :post :put)
           (->> key url-encode (format "keys/%s"))
           :form-params (merge (if dir
                                 {:dir dir}
                                 {:value value})
                               cas
                               (condition-args opts)
                               (when (contains? opts :ttl)
                                 {:ttl ttl
                                  :dir dir}))
           :callback callback))

(defn get-key [key & {:keys [recursive wait waitIndex callback sorted]
                      :or {recursive false wait false}}]
  (api-req :get (->> key url-encode (format "keys/%s"))
           :query-params (merge {:recursive recursive
                                 :wait wait}
                                (filter second
                                        {:waitIndex waitIndex
                                         :sorted sorted}))
           :callback callback))

(defn delete-key! [key & {:keys [recursive callback cas dir]
                          :or {recursive false}
                          :as opts}]
  (api-req :delete (->> key url-encode (format "keys/%s"))
           :query-params (merge {:recursive recursive}
                                (filter second
                                        {:dir dir})
                                cas
                                (condition-args opts :prevValue :prevIndex))
           :callback callback))

(defn compare-and-swap! [key value conditions & {:keys [ttl callback dir] :as opts}]
  (apply-map set-key! key value :cas conditions opts))

(defn compare-and-delete! [key conditions & {:keys [recursive callback dir] :as opts}]
  (apply-map delete-key! key :cas conditions opts))

(defn watch-key [key & {:keys [waitIndex recursive callback] :as opts}]
  (apply-map get-key key :wait true opts))

(defn create-dir! [key & {:keys [ttl callback] :as opts}]
  (apply-map set-key! key nil :dir true opts))

(defn connected-machines []
  (api-req :get "keys/_etcd/machines"))

(defn stats-leader []
  (api-req :get "stats/leader"))

(defn stats-self []
  (api-req :get "stats/self"))

(defn stats-store []
  (api-req :get "stats/store"))
