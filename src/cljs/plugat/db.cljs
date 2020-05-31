(ns plugat.db)

(def default-db
  {:auth                  nil                               ;; keycloak object
   :current-route         nil
   :current-location      nil
   :current-plug          nil
   :post-payload          nil
   :current-plug-messages []
   :plugs-around          []
   :plugs-subscribed      []})