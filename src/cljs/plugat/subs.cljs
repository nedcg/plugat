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
  ::events/current-plug
  (fn [db]
    (:current-plug db)))

(re-frame/reg-sub
  ::events/current-plug-messages
  (fn [db]
    (:current-plug-messages db)))

(re-frame/reg-sub
  ::events/plugs-around
  (fn [db]
    (:plugs-around db)))

(re-frame/reg-sub
  ::events/plugs-subscribed
  (fn [db]
    (:plugs-subscribed db)))