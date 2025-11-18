(ns components.trace-list
  "Trace list sidebar component with accordion grouping"
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
      (.toLocaleTimeString date "en-US" #js {:hour "2-digit" :minute "2-digit" :second "2-digit"}))))

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
                      ;; unknown/default - treat as running if we don't know
                      (merge s/px-2 s/py-0-5 s/text-xs s/rounded s/border
                            {:background-color (:yellow-900 s/colors)
                             :color (:yellow-300 s/colors)
                             :border-color (:yellow-700 s/colors)
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
  "Render individual trace item (nested under group)"
  [{:keys [trace on-select selected?]}]
  (let [{:keys [trace-id started-at duration-ms status live?]} trace
        ;; Live traces show as :running (green pulse) instead of :error
        display-status (if live? :running status)
        base-style (merge s/cursor-pointer s/transition-colors
                         {:padding "8px 12px 8px 28px"})  ; Left indent for nesting
        item-style (if selected?
                    (merge base-style
                          {:background-color "rgba(59, 130, 246, 0.15)"
                           :border-left (str "3px solid " (:blue-500 s/colors))})
                    (merge base-style
                          {:border-left "3px solid transparent"
                           :hover {:background-color (:gray-850 s/colors)}}))]
    [rc/v-box
     :style item-style
     :attr {:on-click #(on-select trace-id)}
     :children
     [;; Top row: timestamp and status
      [rc/h-box
       :style (merge s/flex s/items-start s/justify-between s/mb-1)
       :children
       [[:span {:style (merge s/text-xs s/text-gray-300)}
         (format-timestamp started-at)]
        [status-badge display-status]]]

      ;; Bottom row: duration or LIVE indicator
      (if live?
        [:span {:style (merge s/text-xs {:color "#10b981" :font-weight "600"})}
         "🟢 LIVE SESSION"]
        [:span {:style (merge s/text-xs s/text-gray-500)} (str duration-ms "ms")])]]))

(defn trace-group-header
  "Render accordion group header with command name, count, and chevron"
  [{:keys [command-name count expanded? on-toggle]}]
  (let [hovering? (r/atom false)]
    (fn [{:keys [command-name count expanded? on-toggle]}]
      (let [chevron (if expanded? "▼" "▶")]
        [:div {:style (merge s/px-3 s/py-2 s/cursor-pointer
                            {:background-color (if @hovering?
                                                (:gray-800 s/colors)
                                                "#0f172a")
                             :border-bottom (str "1px solid " (:gray-700 s/colors))
                             :transition "background-color 0.15s ease"})
               :on-mouse-enter #(reset! hovering? true)
               :on-mouse-leave #(reset! hovering? false)
               :on-click on-toggle}
         [rc/h-box
          :style (merge s/flex s/items-center s/justify-between)
          :children
          [;; Left: chevron and command name
           [rc/h-box
            :style (merge s/flex s/items-center s/gap-2)
            :children
            [[:span {:style (merge s/text-xs {:color (:gray-400 s/colors)
                                              :width "12px"
                                              :text-align "center"
                                              :transition "transform 0.2s ease"
                                              :display "inline-block"})} chevron]
             [:span {:style (merge s/text-sm s/font-medium s/text-gray-200)}
              (command-name-short command-name)]]]

           ;; Right: count badge
           [:span {:style (merge s/px-2 s/py-0-5 s/text-xs s/rounded
                                {:background-color (:blue-900 s/colors)
                                 :color (:blue-300 s/colors)
                                 :font-weight "600"})}
            count]]]]))))

(defn trace-group
  "Render a grouped set of traces with accordion"
  [{:keys [command-name traces count expanded? current-trace on-select]}]
  [rc/v-box
   :style {:margin-bottom "0px"}
   :children
   [;; Group header
    [trace-group-header
     {:command-name command-name
      :count count
      :expanded? expanded?
      :on-toggle #(rf/dispatch [::events/toggle-group command-name])}]

    ;; Trace items (only if expanded)
    (when expanded?
      [rc/v-box
       :style {:background-color (:gray-900 s/colors)}
       :children
       (into []
             (for [trace traces]
               ^{:key (:trace-id trace)}
               [trace-item
                {:trace trace
                 :on-select on-select
                 :selected? (= (:trace-id trace) (:trace-id current-trace))}]))])]])

(defn trace-list []
  (let [grouped-traces @(rf/subscribe [::subs/grouped-traces])
        loading @(rf/subscribe [::subs/loading])
        current-trace @(rf/subscribe [::subs/current-trace])
        on-select (fn [trace-id] (rf/dispatch [::events/select-trace trace-id]))]

    [rc/v-box
     :style (merge s/h-full s/flex s/flex-col s/bg-gray-900)
     :children
     [;; Trace list (scrollable)
      [rc/v-box
       :style (merge s/flex-1 s/overflow-y-auto)
       :children
       (cond
         loading
         [[rc/box
           :style s/p-4
           :child [:div {:style (merge s/text-center s/text-gray-400)} "Loading..."]]]

         (seq grouped-traces)
         (into []
               (for [group grouped-traces]
                 ^{:key (:command-name group)}
                 [trace-group
                  {:command-name (:command-name group)
                   :traces (:traces group)
                   :count (:count group)
                   :expanded? (:expanded? group)
                   :current-trace current-trace
                   :on-select on-select}]))

         :else
         [[rc/box
           :style s/p-4
           :child [:div {:style (merge s/text-center {:color (:gray-500 s/colors)})}
                   "No traces yet. Trigger a command to start tracing."]]])]]]))
