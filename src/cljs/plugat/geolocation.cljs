(ns plugat.geolocation)

(defn get-current-location [callback]
  (.getCurrentPosition
    js/navigator.geolocation.
    (fn [position]
      (let [longitude (.-longitude js/position.coords)
            latitude (.-latitude js/position.coords)]
        (callback longitude latitude)))))