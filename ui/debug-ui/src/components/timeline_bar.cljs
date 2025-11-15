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

(def pastel-colors
  "Subdued pastel colors that harmonize with the dark theme"
  ["#7dd3fc"  ; Sky blue
   "#a7f3d0"  ; Mint green
   "#fde68a"  ; Soft yellow
   "#d8b4fe"  ; Lavender
   "#fca5a5"  ; Soft pink
   "#a5f3fc"  ; Cyan
   "#c4b5fd"  ; Purple
   "#fcd34d"  ; Amber
   "#86efac"  ; Green
   "#f9a8d4"  ; Pink
   "#93c5fd"  ; Blue
   "#bef264"  ; Lime
   ])

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

(defn timeline-bar
  [{:keys [events max-width selected-node-id trace-id is-executing]}]
  (let [trace-id-stable (str trace-id)
        durations (extract-node-durations events)
        prefix-node-id (fn [node-id] (str trace-id-stable "-" node-id))
        total-duration (reduce + 0 (map :duration durations))
        min-duration (apply min 1 (map :duration durations))
        max-duration (apply max 1 (map :duration durations))]

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
                 color (nth pastel-colors (mod idx (count pastel-colors)))
                 width-pct (* 100 (smart-scale duration total-duration (count durations)))
                 prefixed-node-id (prefix-node-id node-id)
                 is-selected? (= prefixed-node-id selected-node-id)
                 is-segment-executing? (and is-executing
                                           enter-time
                                           (not exit-time))]
             [:div
              {:key seg-id
               :style {:width (str width-pct "%")
                       :height "100%"
                       :background-color color
                       :opacity (cond
                                 is-selected? 1.0
                                 is-segment-executing? 0.95
                                 hovering? 1.0
                                 :else 0.8)
                       :cursor "pointer"
                       :flex-shrink 0
                       :transition "all 0.2s"
                       :transform (if is-selected? "scaleY(1.15)" "scaleY(1)")
                       :box-shadow (cond
                                    is-selected? (str "0 0 10px " color)
                                    is-segment-executing? (str "0 0 15px " color ", inset 0 0 10px rgba(255,255,255,0.2)")
                                    :else "none")
                       :outline (if is-selected? "2px solid #0B1629FF" "none")
                       :outline-offset (if is-selected? "-2px" "0")
                       :z-index (if is-selected? 10 1)
                       :animation (if is-segment-executing? "timeline-pulse 1s ease-in-out infinite" "none")
                       :position "relative"}
               :title (str label " - " duration "ms")
               :on-mouse-enter #(add-hover seg-id)
               :on-mouse-leave #(remove-hover seg-id)
               :on-click (fn [e]
                          (.preventDefault e)
                          (when prefixed-node-id
                            (rf/dispatch [::events/select-node prefixed-node-id])))}

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
                [:div {:style s/text-gray-400} (str duration "ms")]]]]))]

        ;; Total duration label
        [:div
         {:style (merge s/text-xs s/text-gray-400 s/font-mono s/whitespace-nowrap)}
         (str total-duration "ms")]]]

      ;; No durations - return empty div
      [:div])))
