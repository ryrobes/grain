(ns components.timeline-bar
  "Log-scale timeline visualization for execution duration"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-com.core :as rc]
            [store.events :as events]
            [styles.core :as s]))

;; Global hover state for timeline segments
(defonce segment-hover (r/atom #{}))

(defn add-hover [seg-id]
  (swap! segment-hover conj seg-id))

(defn remove-hover [seg-id]
  (swap! segment-hover disj seg-id))

(defn is-segment-hovering? [seg-id]
  (contains? @segment-hover seg-id))

(def spectral-colors
  "Spectral temperature scale from hot (red) to cold (blue/purple)"
  ["#9e0142"  ; Deep red (hottest)
   "#d53e4f"  ; Red
   "#f46d43"  ; Orange-red
   "#fdae61"  ; Orange
   "#fee08b"  ; Yellow-orange
   "#ffffbf"  ; Pale yellow (neutral)
   "#e6f598"  ; Yellow-green
   "#abdda4"  ; Light green
   "#66c2a5"  ; Teal
   "#3288bd"  ; Blue
   "#5e4fa2"  ; Purple (coldest)
   ])

(defn duration-to-color
  "Map duration to spectral color - longer durations are hotter"
  [duration min-duration max-duration]
  (if (= min-duration max-duration)
    (nth spectral-colors (quot (count spectral-colors) 2))  ; Middle color if all same
    (let [;; Normalize to 0-1 range
          normalized (/ (- duration min-duration)
                        (- max-duration min-duration))
          ;; Map to color index (invert so long = hot = index 0)
          color-idx (Math/round (* (- 1 normalized)
                                   (dec (count spectral-colors))))]
      (nth spectral-colors color-idx))))

(defn extract-node-durations
  "Extract durations from execution events, matching enter/exit pairs"
  [events]
  (let [;; Build map of node-id -> {:enter-time, :exit-time, :label, :node-id}
        node-map (reduce
                  (fn [acc event]
                    (let [node-id (:node-id event)
                          event-type (:event-type event)]
                      (cond
                        ;; Node entry - record start time and label
                        (= event-type :node-enter)
                        (assoc acc node-id
                               {:enter-time (:relative-ms event)
                                :label (or (:action-label event) node-id)
                                :node-id node-id})  ; Keep node-id for selection

                        ;; Node exit - record end time and duration
                        (= event-type :node-exit)
                        (if-let [node-data (get acc node-id)]
                          (assoc acc node-id
                                 (assoc node-data
                                        :exit-time (:relative-ms event)
                                        :duration (:duration-ms event)))
                          acc)

                        :else acc)))
                  {}
                  events)]

    ;; Convert to sorted vector of durations
    (->> node-map
         vals
         (filter :duration)
         (sort-by :enter-time)
         ;(sort-by :exit-time)
         vec)))

(defn smart-scale
  "Normalize durations for fair visual representation.
   Uses a hybrid approach: equal base + proportional bonus.
   This ensures tiny steps are visible while showing relative differences."
  [value total-duration num-segments]
  (let [;; Base width: everyone gets an equal share (40% of total space)
        base-share (/ 0.4 num-segments)

        ;; Bonus width: proportional to actual duration (60% of total space)
        proportion (if (zero? total-duration)
                     (/ 1.0 num-segments)
                     (/ value total-duration))
        bonus-share (* 0.6 proportion)

        ;; Total width for this segment
        total-share (+ base-share bonus-share)]

    ;; Ensure minimum visibility
    (max 0.05 total-share)))

(defn create-stripe-background
  "Create a diagonal stripe pattern for selected segments"
  [color]
  (str "repeating-linear-gradient("
       "45deg, "
       color ", "
       color " 18px, "
       ;"rgba(0, 0, 0, 0.09) 10px, "
       "rgba(0, 0, 0, 0.09) 2px, "
       ;"rgba(0,0,0,0.03) 20px)"
       color " 20px"
       ))

(defn timeline-bar
  [{:keys [events max-width selected-node-id trace-id is-executing]}]
  (let [trace-id-stable (str trace-id)
        durations (extract-node-durations events)
        prefix-node-id (fn [node-id] (str trace-id-stable "-" node-id))
        total-duration (reduce + 0 (map :duration durations))
        ;; Calculate min/max for color mapping
        duration-values (map :duration durations)
        min-duration (if (seq duration-values) (apply min duration-values) 0)
        max-duration (if (seq duration-values) (apply max duration-values) 0)]

    (if (seq durations)
      [rc/h-box
       :style (merge s/flex s/items-center s/gap-3 s/w-full)
       :children
       [;; Timeline bar container
        [:div
         {:style (merge s/flex s/h-10 s/rounded-lg
                        {:flex "1"
                         :overflow "hidden"
                         :background-color "#1e293b"
                         :box-shadow "inset 0 2px 4px rgba(0,0,0,0.3)"
                         :border "1px solid #334155"})}

         ;; Each segment
         (for [[idx {:keys [duration label node-id enter-time exit-time]}] (map-indexed vector durations)]
           (let [seg-id (str "seg-" idx)
                 hovering? (is-segment-hovering? seg-id)
                 color (duration-to-color duration min-duration max-duration)
                 width-pct (* 100 (smart-scale duration total-duration (count durations)))
                 prefixed-node-id (prefix-node-id node-id)
                 is-selected? (= prefixed-node-id selected-node-id)
                 is-segment-executing? (and is-executing
                                            enter-time
                                            (not exit-time))
                 is-first? (= idx 0)
                 is-last? (= idx (dec (count durations)))
                 ;; Only show duration text if segment is wide enough
                 show-duration? (> width-pct 5)]
             [:div
              {:key seg-id
               :style {:width (str width-pct "%")
                       :height "100%"
                       :background (if is-selected?
                                     (create-stripe-background color)
                                     color)
                       :background-color (when (not is-selected?) color)
                       :opacity (cond
                                  is-selected? 1.0
                                  is-segment-executing? 0.95
                                  hovering? 1.0
                                  :else 0.85)
                       :cursor "pointer"
                       :flex-shrink 0
                       :transition "all 0.2s"
                       :transform (if is-selected? "scaleY(1.1)" "scaleY(1)")
                       :box-shadow (cond
                                     is-selected? (str "0 0 20px " color ", inset 0 0 15px rgba(255,255,255,0.3)")
                                     is-segment-executing? (str "0 0 15px " color ", inset 0 0 10px rgba(255,255,255,0.2)")
                                     :else "none")
                       ;; Add borders between segments
                       :border-left (if is-first? "none" "1px solid rgba(0,0,0,0.4)")
                       :border-right (if is-last? "none" "1px solid rgba(0,0,0,0.4)")
                       :z-index (if is-selected? 10 1)
                       :animation (if is-segment-executing? "timeline-pulse 1s ease-in-out infinite" "none")
                       :position "relative"
                       :display "flex"
                       :align-items "center"
                       :justify-content "center"}
               :title (str label " - " duration "ms")
               :on-mouse-enter #(add-hover seg-id)
               :on-mouse-leave #(remove-hover seg-id)
               :on-click (fn [e]
                           (.preventDefault e)
                           (when prefixed-node-id
                             (rf/dispatch [::events/select-node prefixed-node-id])))}

              ;; Duration text inside segment (if space permits)
              (when show-duration?
                [:div
                 {:style {:color (if is-selected? "#ffffff" "rgba(0,0,0,0.7)")
                          :font-size "11px"
                          :font-weight "bold"
                          :text-shadow "1px 1px 2px rgba(0,0,0,0.4)"
                          :pointer-events "none"}}
                 (str duration "ms")])

              ;; Tooltip on hover
              [:div
               {:style (merge s/absolute s/pointer-events-none
                              {:bottom "100%"
                               :left "50%"
                               :transform "translateX(-50%)"
                               :margin-bottom "0.5rem"
                               :opacity (if hovering? 1 0)
                               :transition "opacity 0.2s"
                               :z-index 1000})}
               [:div
                {:style (merge s/bg-gray-900 s/text-white s/text-xs s/px-2 s/py-1
                               s/rounded s/whitespace-nowrap s/shadow-lg
                               {:border (str "1px solid " color)})}
                [:div {:style s/font-medium} label]
                [:div {:style s/text-gray-400} (str duration "ms")]]]]))]]]

      ;; No durations - return empty div
      [:div])))