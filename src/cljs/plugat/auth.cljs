(ns plugat.auth
  (:require [keycloak-js :as keycloak]))

(def auth-options #js {:url      "http://localhost:8080/auth",
                       :realm    "plugat",
                       :clientId "plugat-api"})

(defn init [on-load]
  (let [kc (new keycloak auth-options)]
    (.then
      (.init kc
             #js {:onLoad "login-required"})
      #(on-load kc))))

(defn token [auth]
  (.-token auth))

(defn logout [auth]
  (when auth
    (.logout auth)))