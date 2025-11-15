(ns components.trace-list
  "Trace list sidebar component"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-com.core :as rc]
            [store.subs :as subs]
            [store.events :as events]
            [styles.core :as s]))

;; Global hover state
(defonce hover-state (r/atom {}))

(defn set-hover [id hovering?]
  (swap! hover-state assoc id hovering?))

(defn is-hovering? [id]
  (get @hover-state id false))

(defn format-timestamp
  "Format instant to readable time"
  [instant]
  (when instant
    (let [date (js/Date. instant)]
      (.toLocaleTimeString date "en-US" #js {:hour "2-digit" :minute "2-digit"}))))

(defn status-badge
  "Render status badge with color"
  [status]
  (let [badge-style (case status
                      :running (merge s/px-2 s/py-0-5 s/text-xs s/rounded s/border
                                     {:background-color (:yellow-900 s/colors)
                                      :color (:yellow-300 s/colors)
                                      :border-color (:yellow-700 s/colors)
                                      :opacity 0.5})
                      :success (merge s/px-2 s/py-0-5 s/text-xs s/rounded s/border
                                     {:background-color (:light-green-900 s/colors)
                                      :color (:light-green-300 s/colors)
                                      :border-color (:light-green-700 s/colors)
                                      :opacity 0.5})
                      :failure (merge s/px-2 s/py-0-5 s/text-xs s/rounded s/border
                                     {:background-color (:red-900 s/colors)
                                      :color "#fca5a5"
                                      :border-color (:red-700 s/colors)
                                      :opacity 0.5})
                      :error (merge s/px-2 s/py-0-5 s/text-xs s/rounded s/border
                                   {:background-color (:red-900 s/colors)
                                    :color "#fca5a5"
                                    :border-color (:red-700 s/colors)
                                    :opacity 0.5})
                      ;; default
                      (merge s/px-2 s/py-0-5 s/text-xs s/rounded s/border
                            {:background-color (:gray-800 s/colors)
                             :color (:gray-300 s/colors)
                             :border-color (:gray-600 s/colors)
                             :opacity 0.5}))
        animated? (= status :running)
        final-style (if animated?
                     (merge badge-style s/animate-pulse)
                     badge-style)]
    [:span {:style final-style}
     (if (= status :running)
       "LIVE"
       (name status))]))

(defn command-name-short
  "Shorten command name for display"
  [cmd-name]
  (when cmd-name
    (let [parts (clojure.string/split (name cmd-name) #"/")]
      (last parts))))

(defn trace-item
  [{:keys [trace on-select selected?]}]
  (let [{:keys [trace-id command-name started-at duration-ms status]} trace
        base-style (merge s/p-3   s/cursor-pointer s/transition-colors)
        item-style (if selected?
                    (merge base-style
                          s/bg-blue-900-30
                          {:border-left (str "4px solid " (:blue-500 s/colors))})
                    base-style)]
    [rc/v-box
     :style item-style
     :attr {:on-click #(on-select trace-id)}
     :children
     [;; Top row: command name and status
      [rc/h-box
       :style (merge s/flex s/items-start s/justify-between s/mb-1)
       :children
       [[:span {:style (merge s/text-sm s/font-medium s/text-gray-200)}
         (command-name-short command-name)]
        [status-badge status]]]

      ;; Bottom row: timestamp and duration
      [rc/h-box
       :style (merge s/flex s/items-center s/justify-between)
       :children
       [[:span {:style (merge s/text-xs s/text-gray-400)} (format-timestamp started-at)]
        [:span {:style (merge s/text-xs s/text-gray-400)} (str duration-ms "ms")]]]]]))

(defn trace-list []
  (let [traces @(rf/subscribe [::subs/traces])
        loading @(rf/subscribe [::subs/loading])
        current-trace @(rf/subscribe [::subs/current-trace])
        ;; sse-connected @(rf/subscribe [::subs/sse-connected])
        ;; sse-reconnecting @(rf/subscribe [::subs/sse-reconnecting])
        ;refresh-hover? (is-hovering? :refresh-btn)
        on-select (fn [trace-id] (rf/dispatch [::events/select-trace trace-id]))
        ;on-refresh (fn [] (rf/dispatch [::events/fetch-traces]))
        ]

    [rc/v-box
     :style (merge s/h-full s/flex s/flex-col s/bg-gray-900 )
     :children
     [;; Header
      ;; [rc/v-box
      ;;  :style (merge s/p-4  )
      ;;  :children
      ;;  [;; Title and refresh button
      ;;   ;; [rc/h-box
      ;;   ;;  :style (merge s/flex s/items-center s/justify-between s/mb-3)
      ;;   ;;  :children
      ;;   ;;  [[:h2 {:style (merge s/text-lg s/font-bold s/text-gray-100)} "traces"]
      ;;   ;;   [:button {:style (merge s/px-2 s/py-1 s/text-xs s/rounded s/border
      ;;   ;;                          {:background-color (if refresh-hover?
      ;;   ;;                                               (:gray-700 s/colors)
      ;;   ;;                                               (:gray-800 s/colors))
      ;;   ;;                           :color (:gray-300 s/colors)
      ;;   ;;                           :border-color (:gray-600 s/colors)
      ;;   ;;                           :cursor "pointer"})
      ;;   ;;             :on-mouse-enter #(set-hover :refresh-btn true)
      ;;   ;;             :on-mouse-leave #(set-hover :refresh-btn false)
      ;;   ;;             :on-click on-refresh}
      ;;   ;;    "↻ Refresh"]]]

      ;;   ;; SSE status indicator
      ;;   ;; [rc/h-box
      ;;   ;;  :style (merge s/flex s/items-center s/text-xs)
      ;;   ;;  :children
      ;;   ;;  [(cond
      ;;   ;;    sse-connected
      ;;   ;;    [rc/h-box
      ;;   ;;     :style (merge s/flex s/items-center)
      ;;   ;;     :children
      ;;   ;;     [[:span {:style (merge s/w-2 s/h-2 s/bg-green-400 s/rounded-full s/mr-2 s/animate-pulse)} ""]
      ;;   ;;      [:span {:style s/text-green-400} "Live"]]]

      ;;   ;;    sse-reconnecting
      ;;   ;;    [rc/h-box
      ;;   ;;     :style (merge s/flex s/items-center)
      ;;   ;;     :children
      ;;   ;;     [[:span {:style (merge s/w-2 s/h-2 s/bg-yellow-400 s/rounded-full s/mr-2 s/animate-pulse)} ""]
      ;;   ;;      [:span {:style (merge s/text-xs {:color (:yellow-400 s/colors)})} "Reconnecting..."]]]

      ;;   ;;    :else
      ;;   ;;    [:span {:style (merge s/text-xs {:color (:gray-500 s/colors)})} "Offline"])]]
      ;;   ]]

      ;; Trace count
      [rc/box
       :style (merge s/px-4 s/py-2  )
       :child [:div {:style (merge s/text-sm s/text-gray-400)}
               (str (count traces) " trace" (when (not= 1 (count traces)) "s"))]]

      ;; Trace list (scrollable)
      [rc/v-box
       :style (merge s/flex-1 s/overflow-y-auto)
       :children
       (cond
         loading
         [[rc/box
           :style s/p-4
           :child [:div {:style (merge s/text-center s/text-gray-400)} "Loading..."]]]
      
         (seq traces)
         (into []
               (for [trace traces]
                 ^{:key (:trace-id trace)}
                 [trace-item
                  {:trace trace
                   :on-select on-select
                   :selected? (= (:trace-id trace) (:trace-id current-trace))}]))
      
         :else
         [[rc/box
           :style s/p-4
           :child [:div {:style (merge s/text-center {:color (:gray-500 s/colors)})}
                   "No traces yet. Trigger an AI command to start tracing."]]])]]]))
