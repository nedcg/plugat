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
  [re-com/v-box
   :class "list-group rounded-0"
   :style {:width "100%"}
   :children (for [c (range 0 35)]
               [re-com/button
                :class "list-group-item list-group-item-action border-0 rounded-0"
                :label (str "Plug " c)])])

(defn simple-post [{:keys [content]}]
  [re-com/box
   :class "card mb-2"
   :child [:div.card-body content]])

(defn post-feed []
  [re-com/scroller
   :v-scroll :auto
   :child [re-com/v-box
           :class "p-4"
           :size "1"
           :children (for [c (range 10)]
                       [simple-post {:content "text 1"}])]])

(defn toolbar []
  [re-com/h-box
   :class "my-2"
   :children [[re-com/button
               :class "btn-secondary"
               :label "Subscribe"]
              [re-com/gap :size "0.5rem"]
              [re-com/button
               :class "btn-primary"
               :label "Post"]]])

(defn post-view []
  [re-com/v-box
   :size "4"
   :class "bg-light px-2"
   :children [[toolbar]
              [re-com/box
               :size "1"
               :class "border rounded bg-white"
               :child [post-feed]]]])

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

(defn plug-card [{:keys [description]}]
  [:div.card.mt-2
   [:div.card-body
    [:p.card-text
     description]]])

(defn explore-section [& {:keys [title]}]
  [re-com/scroller
   :v-scroll :auto
   :child [re-com/box
           :size "1"
           :class "p-4"
           :child (let [plugs @(re-frame/subscribe [::events/plugs-around])]
                    [:div.card.w-100
                     [:div.card-header title]
                     [:div.card-body
                      [re-com/v-box
                       :children (for [plug plugs]
                                   [plug-card {:description (:plugs/description plug)}])]]])]])

(defn explorer-views [location]
  [re-com/h-box
   :children [[explore-section
               :title "Around you"]
              [explore-section
               :title "Trending"]]])

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
                           (js/console.log "Entering explore"))
                  :stop  (fn [& params] (js/console.log "Leaving explore"))}]}]
   ["console"
    {:name      ::console
     :view      console-page
     :link-text "Console"
     :controllers
                [{:start (fn [& params] (js/console.log "Entering console"))
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