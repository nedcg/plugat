(ns plugat.views
	(:require
		[re-frame.core :as re-frame]
		[re-com.core :as re-com]
		[plugat.events :as events]
		[reitit.core :as r]
		[reitit.frontend.easy :as rfe]
		[reitit.coercion.spec :as rss]
		[reitit.frontend :as rf]))

(defn center-message [child]
	[re-com/box
	 :size "1"
	 :align :center
	 :justify :center
	 :child child])

(defn post-editor [current-plug]
	(let [post-payload @(re-frame/subscribe [::events/post-payload])]
		[re-com/v-box
		 :class "card card-body"
		 :gap "1em"
		 :children [[re-com/input-textarea
								 :class "form-control"
								 :width "100%"
								 :placeholder "Write your post here"
								 :rows "3"
								 :model (get post-payload (:plugs/id current-plug))
								 :on-change (fn [payload]
															(println payload)
															(re-frame/dispatch [::events/set-post-payload (:plugs/id current-plug) payload]))]
								[re-com/button
								 :label "Post"
								 :class "btn-primary"
								 :on-click #(re-frame/dispatch [::events/post-payload!])]]]))

(defn button-subscribe [& {:keys [plug-id]}]
	(let [subscriptions    @(re-frame/subscribe [::events/plugs-subscribed])
				subscription-ids (->> subscriptions (map :plugs/id) (into #{}))
				subscribed?      (contains? subscription-ids plug-id)]
		[:button.btn.btn-primary
		 {:class    (when subscribed? :active)
			:on-click (fn [_]
									(if subscribed?
										(re-frame/dispatch [::events/put-plugs-unsubscribe plug-id])
										(re-frame/dispatch [::events/put-plugs-subscribe plug-id])))}
		 (if subscribed? "Unsubscribe" "Subscribe")]))

(defn simple-post [message]
	[re-com/box
	 :class "card mb-2"
	 :child [:div.card-body (:messages/payload message)]])

(defn post-feed [messages]
	(re-com/scroller
		:v-scroll :auto
		:class "p-4 border rounded bg-white"
		:child [re-com/v-box
						:size "1"
						:children (for [message messages]
												[simple-post message])]))

(defn sidebar-plugs [plugs current-plug]
	[re-com/v-box
	 :size "1"
	 :class "list-group rounded-0"
	 :children (for [plug plugs]
							 [re-com/button
								:class (str "list-group-item list-group-item-action border-0 rounded-0"
												 (when (= (:plugs/id current-plug) (:plugs/id plug)) " active"))
								:on-click (fn [_]
														(re-frame/dispatch [::events/set-current-plug plug])
														(re-frame/dispatch [::events/fetch-current-plug-messages plug]))
								:label (:plugs/description plug)])])

(defn post-view [current-plug]
	(let [messages @(re-frame/subscribe [::events/current-plug-messages])]
		(if (seq messages)
			[re-com/v-box
			 :size "1"
			 :gap "2em"
			 :class "bg-light p-4"
			 :children [[post-editor current-plug]
									[post-feed messages]]]
			[center-message
			 [:div.text-center
				[:p "There is no posts yet"]
				[:button.btn.btn-primary
				 "Post"]]])))

(defn console-page []
	(let [plugs-subscribed @(re-frame/subscribe [::events/plugs-subscribed])
				current-plug     @(re-frame/subscribe [::events/current-plug])]
		(if (seq plugs-subscribed)
			[re-com/h-box
			 :size "1"
			 :height "100%"
			 :children [[sidebar-plugs plugs-subscribed current-plug]
									[re-com/box
									 :size "4"
									 :child (if (some? current-plug)
														[post-view current-plug]
														[center-message "Please select a plug from the side panel"])]]]
			[center-message
			 [:div.text-center
				[:p "Please subscribe to any plug of your interest"]
				[:button.btn.btn-primary
				 {:on-click #(re-frame/dispatch [::events/navigate ::explore])}
				 "Explore"]]])))

(defn settings-page []
	[:div
	 [:h1 "settings"]])

(defn plug-card [plug]
	[:div.card {:key (:plugs/id plug)}
	 [:div.card-body
		[:p.card-text
		 (:plugs/description plug)]
		[button-subscribe :plug-id (:plugs/id plug)]]])

(defn explore-section [& {:keys [title]}]
	(let [plugs @(re-frame/subscribe [::events/plugs-around])]
		[:div.card-columns.p-4
		 [:<>
			(for [plug plugs]
				[plug-card plug])]]))

(defn explorer-views []
	[re-com/box
	 :size "1"
	 :child [explore-section :title "Around you"]])

(defn explore-page []
	(let [location @(re-frame/subscribe [::events/current-location])]
		(if (some? location)
			[explorer-views]
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
								text  (-> route :data :link-text)]]
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
	(let [current-location @(re-frame/subscribe [::events/current-location])]
		(if (some? current-location)
			(let [current-route @(re-frame/subscribe [::events/current-route])]
				[re-com/v-box
				 :height "inherit"
				 :children [[re-com/box
										 :size "0 1 auto"
										 :child [navbar {:router router :current-route current-route}]]
										(when current-route [(-> current-route :data :view)])]])
			[re-com/box
			 :size "1"
			 :height "100vh"
			 :justify :center
			 :align :center
			 :child [re-com/throbber :color "007bff" :size :large]])))