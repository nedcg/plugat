(ns plugat.core
  (:require
    [reagent.core :as reagent]
    [reagent.dom :as rdom]
    [re-frame.core :as re-frame]
    [plugat.subs :as subs]
    [plugat.events :as events]
    [plugat.views :as views]
    [plugat.config :as config]
    [plugat.auth :as auth]
    [plugat.geolocation :as geolocation]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (views/init-routes!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-layout {:router views/router}] root-el)))

(defn ^:export init []
  (dev-setup)
  (auth/init
    (fn [auth]
      (re-frame/dispatch-sync [::events/initialize-db])
      (re-frame/dispatch-sync [::events/initialize-auth auth])
      (geolocation/get-current-location
        (fn [lng lat]
          (re-frame/dispatch [::events/set-current-location lng lat])
          (mount-root))))))