(ns components.app
  "Main app component"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-com.core :as rc]
            [store.subs :as subs]
            [store.events :as events]
            [components.trace-list :refer [trace-list]]
            [components.tree-view :refer [tree-view]]
            [components.node-details :refer [node-details]]
            [components.timeline-bar :refer [timeline-bar]]
            [styles.core :as s]))

;; Global hover state atoms
(defonce hover-state (r/atom {}))

(defn set-hover [id hovering?]
  (swap! hover-state assoc id hovering?))

(defn is-hovering? [id]
  (get @hover-state id false))

(defn error-banner
  []
  (let [error @(rf/subscribe [::subs/error])
        close-hover? (is-hovering? :error-close)]
    (when error
      [rc/h-box
       :style (merge s/fixed {:top 0 :left 0 :right 0}
                    s/bg-red-900 s/text-red-100 s/px-4 s/py-3
                    s/flex s/items-center s/justify-between s/z-50)
       :children
       [[:span error]
        [:button {:style (merge s/text-red-100 s/font-bold
                               (when close-hover? {:color "#ffffff"}))
                  :on-mouse-enter #(set-hover :error-close true)
                  :on-mouse-leave #(set-hover :error-close false)
                  :on-click #(rf/dispatch [::subs/clear-error])}
         "×"]]])))

(defn view-mode-toggle
  []
  (let [current-mode @(rf/subscribe [::subs/view-mode])
        control-hover? (is-hovering? :btn-control)
        event-hover? (is-hovering? :btn-event)
        data-hover? (is-hovering? :btn-data)
        hybrid-hover? (is-hovering? :btn-hybrid)

        button-base (merge s/px-3 s/py-1-5 s/rounded s/text-xs s/font-medium s/transition-all s/cursor-pointer)
        button-active {:color "#ffffff" :box-shadow "0 10px 15px -3px rgba(0, 0, 0, 0.1)"}
        button-inactive (merge s/text-gray-400 {:background-color (:gray-800 s/colors)})
        button-inactive-hover (merge {:background-color (:gray-700 s/colors)
                                      :color (:gray-300 s/colors)})]
    [rc/h-box
     :style (merge s/px-4 s/py-3   
                  s/flex s/items-center s/gap-3)
     :children
     [[:span {:style (merge s/text-gray-400 s/font-semibold s/text-sm)} "View:"]

      ;; Control Flow button
      [:button {:style (merge button-base
                             (if (= current-mode :control-flow)
                               (merge button-active {:background-color (:blue-600 s/colors)})
                               (if control-hover?
                                 button-inactive-hover
                                 button-inactive)))
                :on-mouse-enter #(set-hover :btn-control true)
                :on-mouse-leave #(set-hover :btn-control false)
                :on-click #(rf/dispatch [::events/set-view-mode :control-flow])}
       "Control Flow"]

      ;; Event Model button
      [:button {:style (merge button-base
                             (if (= current-mode :event-model)
                               (merge button-active {:background-color (:purple-600 s/colors)})
                               (if event-hover?
                                 button-inactive-hover
                                 button-inactive)))
                :on-mouse-enter #(set-hover :btn-event true)
                :on-mouse-leave #(set-hover :btn-event false)
                :on-click #(rf/dispatch [::events/set-view-mode :event-model])}
       "Event Model"]

      ;; Data Flow button
      [:button {:style (merge button-base
                             (if (= current-mode :data-flow)
                               (merge button-active {:background-color (:cyan-600 s/colors)})
                               (if data-hover?
                                 button-inactive-hover
                                 button-inactive)))
                :on-mouse-enter #(set-hover :btn-data true)
                :on-mouse-leave #(set-hover :btn-data false)
                :on-click #(rf/dispatch [::events/set-view-mode :data-flow])}
       "Data Flow"]

      ;; Hybrid button
      [:button {:style (merge button-base
                             (if (= current-mode :hybrid)
                               (merge button-active {:background-color "#16a34a"})
                               (if hybrid-hover?
                                 button-inactive-hover
                                 button-inactive)))
                :on-mouse-enter #(set-hover :btn-hybrid true)
                :on-mouse-leave #(set-hover :btn-hybrid false)
                :on-click #(rf/dispatch [::events/set-view-mode :hybrid])}
       "Hybrid"]]]))

(defn node-type-legend
  []
  [rc/h-box
   :style (merge s/px-4 s/py-2  
                s/flex s/items-center s/gap-6 s/text-xs)
   :children
   [[:span {:style (merge s/text-gray-400 s/font-semibold)} "Node Types:"]

    ;; Command
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          s/border-blue-400
                          {:background-color (:blue-900 s/colors)})}]
      [:span {:style s/text-blue-400} "Command"]
      [:span {:style {:color (:gray-500 s/colors)}} "(emits events)"]]]

    ;; Query
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          s/border-green-400
                          {:background-color (:green-900 s/colors)})}]
      [:span {:style s/text-green-400} "Query"]
      [:span {:style {:color (:gray-500 s/colors)}} "(read-only)"]]]

    ;; Computation
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          s/border-orange-400
                          {:background-color (:orange-900 s/colors)})}]
      [:span {:style s/text-orange-400} "Computation"]
      [:span {:style {:color (:gray-500 s/colors)}} "(transform)"]]]

    ;; Conditional
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          s/border-yellow-300
                          {:background-color "#fef3c7"})}]
      [:span {:style s/text-yellow-300} "Conditional"]
      [:span {:style {:color (:gray-500 s/colors)}} "(decision)"]]]

    [:span {:style {:color (:gray-600 s/colors)}} "|"]

    ;; Read Model Annotation
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          s/border-cyan-400
                          {:background-color (:cyan-900 s/colors)})}]
      [:span {:style s/text-cyan-400} "Read Model"]
      [:span {:style {:color (:gray-500 s/colors)}} "(queries)"]]]

    ;; DSPy Annotation
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          {:border-color "#059669"
                           :background-color "#064e3b"})}]
      [:span {:style {:color "#6ee7b7"}} "DSPy"]
      [:span {:style {:color (:gray-500 s/colors)}} "(AI reasoning)"]]]

    ;; Event Annotation
    [rc/h-box
     :style (merge s/flex s/items-center s/gap-2)
     :children
     [[:div {:style (merge s/w-4 s/h-4 s/rounded s/border-2
                          s/border-purple-400
                          {:background-color (:purple-900 s/colors)})}]
      [:span {:style s/text-purple-400} "Events"]
      [:span {:style {:color (:gray-500 s/colors)}} "(emitted)"]]]]])

(defn trace-info-bar
  []
  (let [current-trace @(rf/subscribe [::subs/current-trace])
        is-executing @(rf/subscribe [::subs/is-trace-executing])
        execution-events @(rf/subscribe [::subs/execution-events])
        selected-node @(rf/subscribe [::subs/selected-node])]
    (when current-trace
      (let [{:keys [command-name duration-ms status trace-id]} current-trace
            trace-id-str (str trace-id)]
        [rc/h-box
         :style (merge s/px-4 s/py-2 s/bg-gray-800 
                      s/flex s/items-center s/gap-4 s/text-sm)
         :children
         [[:span {:style s/text-gray-400} "Trace:"]
          [:span {:style (merge s/text-gray-200 s/font-medium)} (name command-name)]
          [:span {:style s/text-gray-400} "|"]

          ;; Show duration or "executing" indicator
          (if is-executing
            [rc/h-box
             :style (merge s/flex s/items-center s/gap-2 {:color (:yellow-400 s/colors)})
             :children
             [[:span {:style (merge s/w-2 s/h-2 s/bg-yellow-400 s/rounded-full s/animate-pulse)} ""]
              [:span "LIVE"]]]
            [:span {:style {:color (:gray-300 s/colors)}} (str duration-ms "ms")])

          [:span {:style s/text-gray-400} "|"]
          [:span {:style s/text-gray-400} "Status:"]
          [:span {:style (case status
                          :running {:color (:yellow-400 s/colors)}
                          :success {:color (:green-400 s/colors)}
                          :failure {:color "#f87171"}
                          :error {:color "#f87171"}
                          s/text-gray-400)}
           (name status)]

          ;; Timeline bar visualization
          (when (and execution-events (seq execution-events))
            [:div {:style (merge s/flex-1 s/mx-4)}
             [timeline-bar {:events execution-events
                           :selected-node-id selected-node
                           :trace-id trace-id-str
                           :is-executing is-executing}]])

          [:span {:style (merge s/ml-auto s/text-xs {:color (:gray-500 s/colors)} s/font-mono)}
           trace-id-str]]]))))

(defn app
  []
  [rc/v-box
   :style (merge s/h-screen s/w-screen s/flex s/flex-col s/overflow-hidden)
   :children
   [;; Error banner
    [error-banner]

    ;; Header with GRAIN SANDMAN title
    [:div
     {:style (merge s/bg-gray-900   s/px-6 s/py-4
                   s/flex s/items-center s/justify-end)}
     [:div
      {:style {:margin-left "auto"
               :height "45px"
               :display "flex"
               :flex-direction "column"
               :align-items "flex-end"
               :justify-content "center"
               :gap "0px"}}
      [:div
       {:style {:font-size "64px"
                :font-weight 900
                :font-family "Outfit, ui-sans-serif, system-ui"
                :box-shadow "0 12px 15px rgba(0,0,0,0.2)"
                :text-shadow "6px 0px 0px rgba(0, 242, 255, 1), -19px 0px 27px rgba(0, 242, 255, 0.22)"
                :line-height "0.8"
                :margin-top "5px"}}
       "GRAIN"]
      [:div
       {:style {:font-size "26px"
                :font-weight 900
                :font-family "Outfit, ui-sans-serif, system-ui"
                :text-shadow "5px 0px 0px rgba(0, 242, 255, 1), -4px 0px 6px rgba(0, 242, 255, 0.22)"
                :letter-spacing "0.1em"
                :margin-top "-8px"
                :margin-right "-16px"}}
       "\"SANDMAN\""]]]

    ;; View mode toggle
    [view-mode-toggle]

    ;; Node type legend
    [node-type-legend]

    ;; Trace info bar
    [trace-info-bar]

    ;; Main content
    [rc/h-box
     :style (merge s/flex-1 s/flex s/overflow-hidden)
     :children
     [;; Trace list sidebar (left)
      [:div {:style (merge s/w-80 s/flex-shrink-0)}
       [trace-list]]

      ;; Tree visualization (center)
      [:div {:style (merge s/flex-1 s/relative)}
       [tree-view]]

      ;; Node details (right)
      [:div {:style (merge s/w-96 s/flex-shrink-0)}
       [node-details]]]]]])
