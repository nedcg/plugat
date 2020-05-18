(ns plugat.subs
  (:require
    [plugat.events :as events]
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::events/current-route
  (fn [db]
    (:current-route db)))

(re-frame/reg-sub
  ::events/current-location
  (fn [db]
    (:current-location db)))

(re-frame/reg-sub
  ::events/plugs-around
  (fn [db]
    (:plugs-around db)))