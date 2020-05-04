(ns plugat.core
  (:require
    [ring.adapter.jetty :refer [run-jetty]]
    [ring.util.response :refer [response not-found content-type]]
    [reitit.ring :as ring]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]
    [next.jdbc.connection :as connection]
    [clojure.spec.alpha :as s]
    [reitit.dev.pretty :as pretty]
    [reitit.http :as http]
    [reitit.coercion.spec]
    [reitit.interceptor.sieppari :as sieppari]
    [reitit.http.coercion :as http-coercion]
    [reitit.http.interceptors.dev :as dev]
    [reitit.http.interceptors.muuntaja :as muuntaja]
    [reitit.http.interceptors.parameters :as parameters]
    [reitit.http.interceptors.exception :as exception]
    [ring.middleware.reload :refer [wrap-reload]]
    [environ.core :refer [env]]
    [clojure.set :as set]
    [muuntaja.core :as m])
  (:import (org.bson.types ObjectId)
           (com.zaxxer.hikari HikariDataSource)))

(def oid-hex-str-regex #"^[0-9a-f]{24}$")
(s/def ::oid (s/and string? #(re-matches oid-hex-str-regex %)))
(s/def ::id ::oid)

(s/def :plug/type #{:user :search :place})
(s/def ::type :plug/type)
(s/def ::longitude (s/double-in :min -180.0 :max 180 :NaN false :infinite? false))
(s/def ::latitude (s/double-in :min -90.0 :max 90 :NaN false :infinite? false))
(s/def ::coordinates (s/tuple ::longitude ::latitude))
(s/def ::plug-new (s/keys :req-un [::title ::type ::description ::coordinates]))
(s/def ::path-param (s/keys :req-un [::id]))

(defn gen-oid [] (.toHexString (ObjectId.)))

(def ^:private datasource-options {:jdbcUrl (env :database-url)})
(defonce ds (delay (connection/->pool HikariDataSource datasource-options)))

(def db-interceptor
  {:name ::db
   :enter
         (fn [context]
           (update context :request assoc :datasource @ds))
   ;:leave
   ;      (fn [context]
   ;        (when-not (empty? (:tx-data context))
   ;          (with-open [tx (jdbc/get-connection (get-in context [:request :datasource]))]
   ;            (jdbc/with-transaction tx (doseq [stm (:tx-data context)]
   ;                                        (jdbc/execute! tx stm))))
   ;          context))
   })

(def authz-interceptor
  "Interceptor that mounts itself if route has `:roles` data. Expects `:roles`
  to be a set of keyword and the context to have `[:user :roles]` with user roles.
  responds with HTTP 403 if user doesn't have the roles defined, otherwise no-op."
  {:name    ::authz
   :compile (fn [{:keys [roles]} _]
              (if (seq roles)
                {:description  (str "requires roles " roles)
                 :spec         {:roles #{keyword?}}
                 :context-spec {:user {:roles #{keyword}}}
                 :enter        (fn [{{user-roles :roles} :user :as ctx}]
                                 (if (not (set/subset? roles user-roles))
                                   (assoc ctx :response {:status 403, :body "forbidden"})
                                   ctx))}))})

(def interceptor-create-plug
  {:name ::create-plug
   :enter
         (fn [{{:keys [parameters datasource]} :request :as ctx}]
           (let [new-plug (merge (:body parameters) {:id (gen-oid)})]
             (with-open [conn (jdbc/get-connection datasource)]
               (sql/insert! conn :plugs new-plug))
             (assoc ctx :response (response new-plug))))})

(def interceptor-get-plug
  {:name ::get-plug-by-id
   :enter
         (fn [{{:keys [parameters datasource]} :request :as ctx}]
           (with-open [conn (jdbc/get-connection datasource)]
             (if-let [result (sql/get-by-id conn :plugs (get-in parameters [:path :id]))]
               (assoc ctx :response (if (some? result)
                                      (response result)
                                      (not-found nil))))))})

(def interceptor-find-plugs
  {:name ::find-plugs
   :enter
         (fn [{{:keys [datasource]} :request :as ctx}]
           (with-open [conn (jdbc/get-connection datasource)]
             (assoc ctx :response (sql/find-by-keys :plugs conn {}))))})

(def app
  (http/ring-handler
    (http/router

      ["/api"
       ["/plugs/:id" {:get {:interceptors [interceptor-get-plug]
                            :roles        #{:basic}
                            :parameters   {:path ::path-param}}}]
       ["/plugs" {:get  {:interceptors [interceptor-find-plugs]}
                  :post {:interceptors [interceptor-create-plug]
                         :roles        #{:basic}
                         :parameters   {:body ::plug-new}}}]]

      {:exception                    pretty/exception
       :reitit.interceptor/transform dev/print-context-diffs
       :data                         {:coercion     reitit.coercion.spec/coercion
                                      :muuntaja     m/instance
                                      :interceptors [
                                                     (parameters/parameters-interceptor)
                                                     (muuntaja/format-negotiate-interceptor)
                                                     (muuntaja/format-response-interceptor)
                                                     (exception/exception-interceptor)
                                                     (muuntaja/format-request-interceptor)
                                                     (http-coercion/coerce-response-interceptor)
                                                     (http-coercion/coerce-request-interceptor)
                                                     (http-coercion/coerce-exceptions-interceptor)
                                                     db-interceptor
                                                     ]}})
    (ring/routes
      (ring/create-default-handler))
    {:executor sieppari/executor}))

(defonce server (atom nil))

(defn restart []
  (when (some? @server)
    (.stop @server)
    (reset! server nil))
  (reset! server (run-jetty #'app {:port 3000, :join? false, :async true}))
  (println "server running in port 3000"))

(comment
  (restart))
