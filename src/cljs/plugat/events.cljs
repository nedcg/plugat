(ns plugat.events
  (:require
    [re-frame.core :as re-frame]
    [plugat.db :as db]
    [plugat.auth :as auth]
    [plugat.geolocation :as geolocation]
    [reitit.frontend.controllers :as rfc]
    [reitit.frontend.easy :as rfe]
    [day8.re-frame.http-fx]
    [ajax.core :as ajax]))

(re-frame/reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(re-frame/reg-event-db
  ::navigated
  (fn [db [_ new-match]]
    (let [old-match (:current-route db)
          controllers (rfc/apply-controllers (:controllers old-match) new-match)]
      (assoc db :current-route (assoc new-match :controllers controllers)))))

(re-frame/reg-event-db
  ::initialize-auth
  (fn [db [_ kc]]
    (assoc db :auth kc)))

(re-frame/reg-event-db
  ::set-current-location
  (fn [db [_ lng lat]]
    (assoc db :current-location [lng lat])))

(re-frame/reg-event-db
  ::set-plugs-around
  (fn [db [_ plugs]]
    (assoc db :plugs-around plugs)))

(re-frame/reg-event-db
  ::set-current-plug-messages
  (fn [db [_ messages]]
    (assoc db :current-plug-messages messages)))

(re-frame/reg-event-db
  ::set-plugs-subscribed
  (fn [db [_ plugs]]
    (assoc db :plugs-subscribed plugs)))

(re-frame/reg-event-db
  ::set-current-plug
  (fn [db [_ plugs]]
    (assoc db :current-plug plugs)))

(re-frame/reg-event-fx
  ::print-error
  (fn [{db :db} [_ {:keys [status] :as failure}]]
    (*print-err-fn* status failure)
    (if (= status 401)
      {::logout! (:auth db)}
      {})))

(re-frame/reg-event-fx
  ::fetch-plugs-around
  (fn [{{[lng lat] :current-location
         auth      :auth} :db} _]
    {:http-xhrio {:method           :get
                  :uri              "http://localhost:3000/api/plugs"
                  :params           {:latitude lat :longitude lng}
                  :headers          {"authorization" (str "Bearer " (auth/token auth))}
                  :timeout          8000
                  :format           (ajax/transit-request-format)
                  :response-format  (ajax/transit-response-format {:keywords? true})
                  :with-credentials true
                  :on-success       [::set-plugs-around]
                  :on-failure       [::print-error]}}))

(re-frame/reg-event-fx
  ::fetch-current-plug-messages
  (fn [{{auth :auth} :db} [_ {id :plugs/id}]]
    {:http-xhrio {:method           :get
                  :uri              (str "http://localhost:3000/api/plugs/" id "/messages")
                  :headers          {"authorization" (str "Bearer " (auth/token auth))}
                  :timeout          8000
                  :format           (ajax/transit-request-format)
                  :response-format  (ajax/transit-response-format {:keywords? true})
                  :with-credentials true
                  :on-success       [::set-current-plug-messages]
                  :on-failure       [::print-error]}}))

(re-frame/reg-event-fx
  ::fetch-plugs-subscribed
  (fn [{{auth :auth} :db} _]
    {:http-xhrio {:method           :get
                  :uri              "http://localhost:3000/api/user/plug-subscriptions"
                  :headers          {"authorization" (str "Bearer " (auth/token auth))}
                  :timeout          8000
                  :format           (ajax/transit-request-format)
                  :response-format  (ajax/transit-response-format {:keywords? true})
                  :with-credentials true
                  :on-success       [::set-plugs-subscribed]
                  :on-failure       [::print-error]}}))

(re-frame/reg-event-fx
  ::put-plugs-subscribe
  (fn [{{auth :auth} :db} [_ plug-id]]
    {:http-xhrio {:method           :put
                  :uri              (str "http://localhost:3000/api/plugs/" plug-id "/subscription")
                  :headers          {"authorization" (str "Bearer " (auth/token auth))}
                  :timeout          8000
                  :format           (ajax/transit-request-format)
                  :response-format  (ajax/transit-response-format {:keywords? true})
                  :with-credentials true
                  :on-success       [::fetch-plugs-subscribed]
                  :on-failure       [::print-error]}}))

(re-frame/reg-event-fx
  ::put-plugs-unsubscribe
  (fn [{{auth :auth} :db} [_ plug-id]]
    {:http-xhrio {:method           :delete
                  :uri              (str "http://localhost:3000/api/plugs/" plug-id "/subscription")
                  :headers          {"authorization" (str "Bearer " (auth/token auth))}
                  :timeout          8000
                  :format           (ajax/transit-request-format)
                  :response-format  (ajax/transit-response-format {:keywords? true})
                  :with-credentials true
                  :on-success       [::fetch-plugs-subscribed]
                  :on-failure       [::print-error]}}))

(re-frame/reg-event-fx
  ::navigate
  (fn [_ [_ & route]]
    {::navigate! route}))

(re-frame/reg-event-fx
  ::logout
  (fn [{:keys [db]} _]
    {::logout! (:auth db)}))

;; Effects

(re-frame/reg-fx ::navigate! #(apply rfe/push-state %))
(re-frame/reg-fx ::logout! #(auth/logout %))