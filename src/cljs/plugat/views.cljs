(ns plugat.views
  (:require
    [re-frame.core :as re-frame]
    [re-com.core :as re-com]
    [plugat.events :as events]
    [reitit.core :as r]
    [reitit.frontend.easy :as rfe]
    [reitit.coercion.spec :as rss]
    [reitit.frontend :as rf]))

(defn sidebar-plugs []
  (let [plugs @(re-frame/subscribe [::events/plugs-subscribed])]
    [re-com/v-box
     :class "list-group rounded-0"
     :style {:width "100%"}
     :children (for [plug plugs]
                 [re-com/button
                  :class "list-group-item list-group-item-action border-0 rounded-0"
                  :on-click #(re-frame/dispatch [::events/fetch-current-plug-messages plug])
                  :label (:plugs/description plug)])]))

(defn button-subscribe [& {:keys [plug-id]}]
  (let [subscriptions @(re-frame/subscribe [::events/plugs-subscribed])
        subscription-ids (->> subscriptions (map :plugs/id) (into #{}))
        subscribed? (contains? subscription-ids plug-id)]
    [:button.btn.btn-primary
     {:class    (when subscribed? :active)
      :on-click #(if subscribed?
                   (re-frame/dispatch [::events/put-plugs-unsubscribe plug-id])
                   (re-frame/dispatch [::events/put-plugs-subscribe plug-id]))}
     (if subscribed? "Unsubscribe" "Subscribe")]))

(defn simple-post [message]
  [re-com/box
   :class "card mb-2"
   :child [:div.card-body (:messages/payload message)]])

(defn post-feed [plug]
  (let [messages @(re-frame/subscribe [::events/current-plug-messages])]
    [re-com/scroller
     :v-scroll :auto
     :child [re-com/v-box
             :class "p-4"
             :size "1"
             :children (for [message messages]
                         [simple-post message])]]))

(defn toolbar [plug]
  [re-com/h-box
   :class "my-2"
   :children [[button-subscribe :plug-id (:plugs/id plug)]
              [re-com/gap :size "0.5rem"]
              [re-com/button
               :class "btn-primary"
               :label "Post"]]])

(defn post-view []
  (let [plug @(re-frame/subscribe [::events/current-plug])]
    [re-com/v-box
     :size "4"
     :class "bg-light px-2"
     :children [[toolbar plug]
                [re-com/box
                 :size "1"
                 :class "border rounded bg-white"
                 :child [post-feed plug]]]]))

(defn console-page []
  [re-com/h-box
   :children [[re-com/box
               :size "1"
               :child [sidebar-plugs]]
              [re-com/box
               :size "3"
               :child [post-view]]]])

(defn settings-page []
  [:div
   [:h1 "settings"]])

(defn plug-card [plug]
  [:div.card.mt-2
   [:div.card-body
    [:p.card-text
     (:plugs/description plug)]
    [button-subscribe :plug-id (:plugs/id plug)]]])

(defn explore-section [& {:keys [title]}]
  [re-com/box
   :size "1"
   :class "p-4"
   :child (let [plugs @(re-frame/subscribe [::events/plugs-around])]
            [:div.card.w-100
             [:div.card-header title]
             [re-com/scroller
              :class "card-body"
              :child [re-com/v-box
                      :children (for [plug plugs]
                                  [plug-card plug])]]])])

(defn explorer-views [location]
  [re-com/h-box
   :children [[explore-section :title "Around you"]]])

(defn explore-page []
  (let [location @(re-frame/subscribe [::events/current-location])]
    (if (some? location)
      [explorer-views location]
      [re-com/modal-panel
       :wrap-nicely? false
       :child [:div.card
               [:div.card-body
                [:h5.card-title
                 "Location required"]
                [:p.card-text
                 "Please provide a location"]
                [:button.btn.btn-primary
                 {:on-click #(re-frame/dispatch [::events/navigate ::settings])}
                 "Go to settings"]]]])))

;;; Routes ;;;

(defn href
  "Return relative url for given route. Url can be used in HTML links."
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfe/href k params query)))

(def routes
  ["/"
   [""
    {:name      ::explore
     :view      explore-page
     :link-text "Explore"
     :controllers
                [{:start (fn [& params]
                           (re-frame/dispatch [::events/fetch-plugs-around])
                           (re-frame/dispatch [::events/fetch-plugs-subscribed])
                           (js/console.log "Entering explore"))
                  :stop  (fn [& params] (js/console.log "Leaving explore"))}]}]
   ["console"
    {:name      ::console
     :view      console-page
     :link-text "Console"
     :controllers
                [{:start (fn [& params]
                           (re-frame/dispatch [::events/fetch-plugs-subscribed])
                           (js/console.log "Entering console"))
                  :stop  (fn [& params] (js/console.log "Leaving console"))}]}]
   ["settings"
    {:name      ::settings
     :view      settings-page
     :link-text "Settings"
     :controllers
                [{:start (fn [& params] (js/console.log "Entering settings"))
                  :stop  (fn [& params] (js/console.log "Leaving settings"))}]}]])

(defn on-navigate [new-match]
  (when new-match
    (re-frame/dispatch [::events/navigated new-match])))

(def router
  (rf/router
    routes
    {:data {:coercion rss/coercion}}))

(defn init-routes! []
  (js/console.log "initializing routes")
  (rfe/start!
    router
    on-navigate
    {:use-fragment true}))

(defn navbar [{:keys [router current-route]}]
  [:nav.navbar.navbar-expand-lg.navbar-light.bg-light
   [:a.navbar-brand "Plugat"]
   [:div.navbar-nav
    (for [route-name (r/route-names router)
          :let [route (r/match-by-name router route-name)
                text (-> route :data :link-text)]]
      [:a.nav-item.nav-link
       {:key   route-name
        :href  (href route-name)
        :class (when (= route-name (-> current-route :data :name))
                 :active)}
       text])]
   [:button.nav-item.nav-link.btn.btn-link.ml-md-auto
    {:on-click #(re-frame/dispatch [::events/logout])}
    "Logout"]])

(defn main-layout [{:keys [router]}]
  (let [current-route @(re-frame/subscribe [::events/current-route])]
    [re-com/v-box
     :children [[navbar {:router router :current-route current-route}]
                (when current-route [(-> current-route :data :view)])]]))