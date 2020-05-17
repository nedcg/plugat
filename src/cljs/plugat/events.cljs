(ns plugat.events
  (:require
    [re-frame.core :as re-frame]
    [plugat.db :as db]
    [plugat.auth :as auth]
    [reitit.frontend.controllers :as rfc]
    [reitit.frontend.easy :as rfe]
    ))

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

(re-frame/reg-event-fx
  ::navigate
  (fn [_ [_ & route]]
    {::navigate! route}))

(re-frame/reg-event-fx
  ::logout
  (fn [{:keys [db]} _]
    {::logout! (:auth db)}))

(re-frame/reg-fx
  ::navigate!
  (fn [route]
    (apply rfe/push-state route)))

(re-frame/reg-fx
  ::logout!
  (fn [auth]
    (auth/logout auth)))