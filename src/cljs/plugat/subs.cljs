(ns plugat.subs
  (:require
    [plugat.events :as events]
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::events/current-route
  (fn [db]
    (:current-route db)))