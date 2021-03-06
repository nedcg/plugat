(ns plugat.core
	(:require
		[ring.adapter.jetty :refer [run-jetty]]
		[ring.util.response :refer [response not-found content-type]]
		[reitit.ring :as ring]
		[next.jdbc :as jdbc]
		[next.jdbc.sql :as sql]
		[next.jdbc.quoted :as quoted]
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
		[ring.middleware.cors :refer [wrap-cors] :as cors]
		[environ.core :refer [env]]
		[clojure.set :as set]
		[muuntaja.core :as m]
		[buddy.core.keys :as keys]
		[buddy.sign.jwt :as jwt]
		[cheshire.core :as json]
		[buddy.core.codecs.base64 :as b64]
		[taoensso.carmine :as car :refer (wcar)]
		[clojure.spec.gen.alpha :as gen]
		[clojure.string :as string]
		[clojure.string :as str]
		[clojure.tools.logging :as log])
	(:import (org.bson.types ObjectId)
					 (com.zaxxer.hikari HikariDataSource)))

(def oid-hex-str-regex #"^[0-9a-f]{24}$")
(defn hex-str-gen [char-count]
	(gen/fmap #(clojure.string/join %)
		(gen/vector
			(gen/elements
				[0 1 2 3 4 5 6 7 8 9 'a 'b 'c 'd 'e 'f 'g])
			char-count)))

(s/def ::allow-origin string?)
(s/def ::allow-methods (s/coll-of keyword? :kind set?))
(s/def ::allow-credentials boolean?)
(s/def ::allow-headers (s/coll-of string? :kind set?))
(s/def ::expose-headers (s/coll-of string? :kind set?))
(s/def ::max-age nat-int?)
(s/def ::access-control
	(s/keys :opt-un [::allow-origin
									 ::allow-methods
									 ::allow-credentials
									 ::allow-headers
									 ::expose-headers
									 ::max-age]))

(s/def ::cors-interceptor
	(s/keys :opt-un [::access-control]))

(s/def ::oid (s/with-gen
							 (s/and string? #(re-matches oid-hex-str-regex %))
							 #(hex-str-gen 24)))
(s/def ::id ::oid)
(s/def :plug/type #{:user :search :place})
(s/def ::type :plug/type)
(s/def ::payload string?)
(s/def ::longitude (s/double-in :min -180.0 :max 180.0 :NaN false :infinite? false))
(s/def ::latitude (s/double-in :min -85.05112878 :max 85.05112878 :NaN false :infinite? false))
(s/def ::coordinates (s/tuple ::longitude ::latitude))
(s/def ::created-by uuid?)
(s/def ::reply_to ::oid)
(s/def ::title string?)
(s/def ::description string?)
(s/def ::radius (s/int-in 500 10000))

(s/def ::plug-new (s/keys :req-un [::title ::description ::coordinates]))
(s/def ::message-new (s/keys :req-un [::payload]
											 :req-opt [::reply_to]))
(s/def ::plugs-query (s/keys :req-un [::latitude ::longitude]
											 :req-opt [::radius]))

(defn gen-oid [] (.toHexString (ObjectId.)))

(def ^:private datasource-options {:jdbcUrl (env :database-url)})
(defonce ds (delay (connection/->pool HikariDataSource datasource-options)))
(defonce redis-conn {:pool {} :spec {:uri (env :redis-url)}})

(defn decode-b64 [str] (String. (b64/decode (.getBytes str))))
(defn parse-json [s]
	(let [clean-str (if (string/ends-with? s "}") s (str s "}"))]
		(json/parse-string clean-str keyword)))

(defn decode [token]
	(let [[header payload _] (clojure.string/split token #"\.")]
		{:header  (parse-json (decode-b64 header))
		 :payload (parse-json (decode-b64 payload))}))

(def db-interceptor
	{:name  ::datasource
	 :enter #(update % :request assoc
						 :datasource @ds
						 :redis-conn redis-conn)})

(defn add-access-control-methods [access-control methods]
	(if (nil? (-> access-control :access-control-allow-methods seq))
		(assoc access-control :access-control-allow-methods methods)
		access-control))

(def custom-options-handler
	(let [handle (fn [request]
								 (let [methods        (->> request reitit.ring/get-match :result (keep (fn [[k v]] (if v k))))
											 request-method (-> request
																				:headers
																				(get "access-control-request-method" "none")
																				string/lower-case
																				keyword)
											 access-control (-> request
																				reitit.ring/get-match
																				:result
																				(get request-method)
																				:data
																				:access-control)
											 access-control (cors/normalize-config (mapcat identity access-control))
											 allow          (->> methods (map (comp str/upper-case name)) (str/join ","))
											 resp           {:status 204 :body "" :headers {"Allow" allow}}]
									 (if (cors/allow-request? request access-control)
										 (cors/add-access-control request access-control resp)
										 resp)))]
		(fn
			([request]
			 (handle request))
			([request respond _]
			 (respond (handle request))))))

(def cors-interceptor
	{:name    ::cors
	 :spec    ::access-control
	 :compile (fn [{:keys [access-control]} _]
							(when access-control
								(let [access-control (cors/normalize-config (mapcat identity access-control))]
									{
									 ;:enter
									 ;(fn cors-interceptor-enter
									 ;  [{:keys [request] :as ctx}]
									 ;  (if (and (cors/allow-request? request access-control))
									 ;    (assoc ctx :queue nil
									 ;               :response (cors/add-access-control
									 ;                           request
									 ;                           access-control
									 ;                           cors/preflight-complete-response))
									 ;    ctx))
									 :leave
									 (fn cors-interceptor-leave
										 [{:keys [request response] :as ctx}]
										 (cond-> ctx
											 (and (cors/origin request)
												 (cors/allow-request? request access-control)
												 response)
											 (assoc :response
															(cors/add-access-control
																request
																access-control
																response))))})))})

(def auth-interceptor
	{:name ::auth
	 :enter
				 (fn [{{:keys [headers request-method]} :request :as ctx}]
					 (if (= request-method :options)
						 ctx
						 (try
							 (let [token-str  (get headers "authorization")
										 [_ token] (clojure.string/split token-str #" ")
										 jwk-url    (env :jwk-url)
										 public-key (-> jwk-url slurp (json/parse-string keyword) :keys first keys/jwk->public-key)
										 {{:keys [sub name upn groups]} :payload} (decode token)]
								 (jwt/unsign token public-key {:alg :rs256})
								 (update ctx :request
									 assoc :user {:id       sub
																:name     name
																:username upn
																:roles    (into #{} (map keyword groups))}))
							 (catch Throwable exception
								 (throw
									 (ex-info "unauthorized"
										 {:type     :reitit.ring/response
											:response {:status 401 :body "unauthorized"}}
										 exception))))))})

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
								 :enter        (fn [{{{user-roles :roles} :user} :request :as ctx}]
																 (when-not (set/subset? roles user-roles)
																	 (throw
																		 (ex-info "forbidden"
																			 {:type     :reitit.ring/response
																				:response {:status 403
																									 :body   "forbidden"}})))
																 ctx)}))})

(def create-plug
	{:name ::create-plug
	 :enter
				 (fn [{{:keys [parameters datasource]} :request :as ctx}]
					 (let [id       (gen-oid)
								 new-plug (merge (:body parameters) {:id id})
								 {:keys [latitude longitude]} new-plug]
						 (with-open [conn (jdbc/get-connection datasource)]
							 (sql/insert! conn :plugs new-plug)
							 (assoc ctx :response (response new-plug)))))})

(def get-plug
	{:name ::get-plug-by-id
	 :enter
				 (fn [{{:keys [parameters datasource]} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (let [result (sql/get-by-id conn :plugs (get-in parameters [:path :plug-id]))]
							 (when-not (any? result)
								 (throw
									 (ex-info "not found"
										 {:type     :reitit.ring/response
											:response {:status 404
																 :body   "not found"}})))
							 (assoc ctx :response (response result)))))})

(def find-plugs
	{:name ::find-plugs
	 :enter
				 (fn [{{:keys [user datasource]} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (assoc ctx :response
												(response
													(sql/find-by-keys conn :plugs {:created_by_id (:id user)}
														{:table-fn (quoted/schema quoted/ansi)})))))})

(def find-plugs-by-subscriptions
	{:name ::find-plug-subscriptionsfind-plugs
	 :enter
				 (fn [{{:keys [user datasource]} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (let [result (sql/query conn [(str "SELECT plugs.* FROM plugs "
																						 "INNER JOIN plug_subscriptions AS s "
																						 "ON s.plug_id=id AND s.user_id=?") (:id user)]
														{:table-fn (quoted/schema quoted/ansi)})]
							 (assoc ctx :response (response result)))))})

(def query-plugs
	{:name ::query-plugs
	 :enter
				 (fn [{{:keys                               [datasource redis-conn]
								{:strs [radius latitude longitude]} :params} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (if-let [plug-ids (seq
																 (wcar redis-conn
																	 (car/georadius
																		 (str :plugs) longitude latitude (if (some? radius) radius 3000) :m)))]
							 (assoc ctx :response
													(response
														(sql/query
															conn
															(into []
																(concat [(str "SELECT * FROM plugs "
																					 "WHERE id IN ("
																					 (string/join "," (repeat (count plug-ids) "?"))
																					 ")")]
																	plug-ids)))))
							 (assoc ctx :response (response [])))))})

(def find-messages-by-plug
	{:name ::find-messages-by-plug
	 :enter
				 (fn [{{:keys             [datasource]
								{:keys [plug-id]} :path-params} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (let [result (sql/find-by-keys conn :messages {:plug_id plug-id})]
							 (assoc ctx :response (response result)))))})

(def find-messages-by-user
	{:name ::find-messages-by-user
	 :enter
				 (fn [{{:keys [datasource user]} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (let [result (sql/find-by-keys conn :messages {:created_by_id (:id user)})]
							 (assoc ctx :response (response result)))))})

(def put-message
	{:name ::put-message
	 :enter
				 (fn [{{:keys              [datasource user]
								{plug-id :plug-id} :path-params
								body               :body-params} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (let [id          (gen-oid)
									 new-message (merge body {:id              id
																						:plug_id         plug-id
																						:created_by_id   (:id user)
																						:created_by_name (:name user)})
									 _           (sql/insert! conn :messages new-message)]
							 (assoc ctx :response (response new-message)))))})

(def put-plug-subscription
	{:name ::put-plug-subscribe
	 :enter
				 (fn [{{:keys             [datasource user]
								{:keys [plug-id]} :path-params} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (sql/insert! conn :plug_subscriptions {:user_id (:id user)
																										:plug_id plug-id}))
					 (assoc ctx :response {:status 204 :body ""}))})

(def delete-plug-subscription
	{:name ::delete-plug-subscribe
	 :enter
				 (fn [{{:keys             [datasource user]
								{:keys [plug-id]} :path-params} :request :as ctx}]
					 (with-open [conn (jdbc/get-connection datasource)]
						 (sql/delete! conn :plug_subscriptions {:user_id (:id user)
																										:plug_id plug-id}))
					 (assoc ctx :response {:status 204 :body ""}))})

(defn access-control-default [& {:keys [allow-methods]
																 :or   {allow-methods [:options]}}]
	{:access-control-allow-origin      #"http://localhost:8280"
	 :access-control-allow-headers     ["authorization" "cookie" "content-type"]
	 :access-control-allow-methods     allow-methods
	 :access-control-allow-credentials "true"})

(def app
	(http/ring-handler
		(http/router

			["/api"
			 ["/plugs/:plug-id" {:get {:interceptors [get-plug]
																 :roles        #{:basic}
																 :parameters   {:path {:plug-id ::oid}}}}]
			 ["/plugs" {:get {:interceptors   [query-plugs]
												:roles          #{:basic}
												:parameters     {:query ::plugs-query}
												:access-control (access-control-default
																					:allow-methods [:options :get])}}]
			 ["/plugs/:plug-id/subscription" {:put    {:interceptors   [put-plug-subscription]
																								 :roles          #{:basic}
																								 :access-control (access-control-default
																																	 :allow-methods [:options :put])
																								 :parameters     {:path {:plug-id ::oid}}}
																				:delete {:interceptors   [delete-plug-subscription]
																								 :roles          #{:basic}
																								 :access-control (access-control-default
																																	 :allow-methods [:options :delete])
																								 :parameters     {:path {:plug-id ::oid}}}}]
			 ["/plugs/:plug-id/messages" {:get {:interceptors   [find-messages-by-plug]
																					:roles          #{:basic}
																					:access-control (access-control-default
																														:allow-methods [:options :get])
																					:parameters     {:path {:plug-id ::oid}}}
																		:put {:interceptors   [put-message]
																					:roles          #{:basic}
																					:access-control (access-control-default
																														:allow-methods [:options :put])
																					:parameters     {:path {:plug-id ::oid}
																													 :body ::message-new}}}]

			 ["/user/plugs" {:get {:interceptors [find-plugs]
														 :roles        #{:basic}}}]
			 ["/user/plug-subscriptions" {:get {:interceptors   [find-plugs-by-subscriptions]
																					:roles          #{:basic}
																					:access-control (access-control-default
																														:allow-methods [:options :get])}}]
			 ["/user/messages" {:get {:interceptors [find-messages-by-user]
																:roles        #{:basic}}}]]

			{:exception                     pretty/exception
			 :reitit.interceptor/transform  dev/print-context-diffs
			 :data                          {:coercion     reitit.coercion.spec/coercion
																			 :muuntaja     m/instance
																			 :interceptors [
																											cors-interceptor
																											(parameters/parameters-interceptor)
																											(muuntaja/format-negotiate-interceptor)
																											(muuntaja/format-response-interceptor)
																											(exception/exception-interceptor)
																											(muuntaja/format-request-interceptor)
																											auth-interceptor
																											authz-interceptor
																											(http-coercion/coerce-response-interceptor)
																											(http-coercion/coerce-request-interceptor)
																											(http-coercion/coerce-exceptions-interceptor)
																											db-interceptor
																											]}
			 ::http/default-options-handler custom-options-handler})
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

(defn index-plugs! []
	(with-open [conn (jdbc/get-connection @ds)]
		(let [plugs (sql/query conn [(str "SELECT * FROM " (str :plugs))])]
			(wcar redis-conn
				(mapv
					(fn [{id        :plugs/id
								latitude  :plugs/latitude
								longitude :plugs/longitude}]
						(car/geoadd (str :plugs)
							(.floatValue longitude)
							(.floatValue latitude)
							id))
					plugs)))))

(defn delete-index-plugs! []
	(with-open [conn (jdbc/get-connection @ds)]
		(let [plugs (sql/query conn [(str "SELECT * FROM " (str :plugs))])]
			(wcar redis-conn
				(mapv
					(fn [{id :plugs/id}]
						(car/zrem (str :plugs) id))
					plugs)))))

(comment
	(restart)
	(index-plugs!)
	(delete-index-plugs!))
