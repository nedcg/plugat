(ns plugat.core
  (:require
    [ring.adapter.jetty :refer [run-jetty]]
    [ring.util.response :refer [response content-type]]
    [reitit.ring :as ring]
    [hikari-cp.core :refer :all]
    [next.jdbc :as jdbc]
    [next.jdbc.connection :as connection]
    [spec-tools.spec :as spec]
    [clojure.spec.alpha :as s]
    [reitit.http :as http]
    [reitit.coercion.spec]
    [reitit.http.coercion :as http-coercion]
    [reitit.interceptor.sieppari :as sieppari]
    [reitit.http.interceptors.parameters :as parameters]
    [reitit.http.interceptors.exception :as exception]
    [environ.core :refer [env]])
  (:gen-class))

(def ^:private datasource-options {:jdbc-url (env :database-url)})

(defonce datasource
         (delay (make-datasource datasource-options)))

(defn interceptor [number]
  {:enter (fn [ctx] (update-in ctx [:request :number] (fnil + 0) number))})

(def oid-hex-str-regex #"^[0-9a-f]{24}$")
(s/def ::id (s/and string? #(re-matches oid-hex-str-regex %)))
(s/def ::path-params (s/keys :req-un [::id]))

(defn ok [body]
  (-> body
      pr-str
      response
      (content-type "application/edn")))

(defn handler [request]
  (ok {:saludo (:params request)}))

(defn handler-post-plug [request]
  (let [])
  (ok request))

(def app
  (http/ring-handler
    (http/router
      ["/api"
       {:interceptors [(parameters/parameters-interceptor)
                       (exception/exception-interceptor)]}
       ["/plugs" {:get  {:handler handler}
                  :post {:handler handler-post-plug}}]
       ["/plugs/:id" {:get        {:handler handler}
                      :coercion   reitit.coercion.spec/coercion
                      :parameters {:path ::path-params}}]])
    (ring/create-default-handler)
    {:executor     sieppari/executor
     :compile      reitit.coercion/compile-request-coercers
     :interceptors [(http-coercion/coerce-exceptions-interceptor)
                    (http-coercion/coerce-request-interceptor)
                    (http-coercion/coerce-response-interceptor)]}))

(defn start []
  (run-jetty #'app {:port 3000, :join? false, :async true})
  ;(aleph/start-server (aleph/wrap-ring-async-handler #'app) {:port 3000})
  (println "server running in port 3000"))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
