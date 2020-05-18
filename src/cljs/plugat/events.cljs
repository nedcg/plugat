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
  (fn [db [e plugs]]
    (println "plugs-around" e plugs)
    (assoc db :plugs-around plugs)))

(re-frame/reg-event-db
  ::error-report
  (fn [_ [_ failure]]
    (println "failure" failure)))

(re-frame/reg-event-fx
  ::fetch-plugs-around
  (fn [{{[lng lat] :current-location} :db} _]
    {:http-xhrio {:method          :get
                  :uri             "http://localhost:3000/api/plugs"
                  :params          {:latitude lat :longitude lng}
                  :timeout         8000                     ;; optional see API docs
                  :format          (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format {:keywords? true})
                  :on-success      [::set-plugs-around]
                  :on-failure      [::error-report]}}))

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