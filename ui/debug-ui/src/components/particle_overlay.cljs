(ns components.particle-overlay
  "Canvas-based particle effect overlay with physics and node collisions.

   Particles persist across React Flow updates and continue to fall/fade naturally."
  (:require [reagent.core :as r]))

;;
;; Persistent State (survives component re-mounts)
;;

(defonce particles-state (r/atom []))
(defonce spawn-counter-state (r/atom 0))
(defonce last-spawn-times-state (r/atom {}))
(defonce global-canvas-ref (r/atom nil))
(defonce global-animation-id (r/atom nil))
(defonce current-trace-id (r/atom nil))
(defonce executing-nodes-state (r/atom #{}))  ; Single persistent atom for executing nodes

;; Public API to clear particles (called when switching traces)
(defn clear-particles! []
  (reset! particles-state [])
  (reset! spawn-counter-state 0)
  (reset! last-spawn-times-state {}))

;;
;; Particle Physics
;;

(defn create-particle [x y color]
  {:x x :y y
   :vx (- (rand 1.5) 0.75)
   :vy (+ 0.5 (rand 1.0))
   :life 1.0
   :color color
   :size (+ 2 (rand 2))})

(defn check-collision [particle node-bounds]
  (let [{:keys [x y size]} particle
        {:keys [left top right bottom]} node-bounds]
    (and (>= (+ x size) left)
         (<= (- x size) right)
         (>= (+ y size) top)
         (<= (- y size) bottom))))

(defn bounce-particle [particle node-bounds]
  (let [{:keys [y vy vx size]} particle
        {:keys [top center-y]} node-bounds
        damping 0.6
        hitting-top? (< y center-y)]
    (if hitting-top?
      (-> particle
          (assoc :vy (* (Math/abs vy) (- damping)))
          (update :vx + (- (rand 0.6) 0.3))
          (assoc :y (- top size 2)))
      (assoc particle :vx (* vx -0.5)))))

(defn update-particle [particle node-bounds-list]
  (let [updated (-> particle
                    (update :vy + 0.3)
                    (update :vx + (- (rand 0.2) 0.1))
                    (update :vx * 0.998)
                    (update :y + (:vy particle))
                    (update :x + (:vx particle))
                    (update :life - 0.0012))
        collision (some #(when (check-collision updated %) %) node-bounds-list)]
    (if collision
      (bounce-particle updated collision)
      updated)))

(defn particle-alive? [particle]
  (> (:life particle) 0))

(defn draw-particle [ctx particle]
  (let [{:keys [x y life color size]} particle]
    (set! (.-globalAlpha ctx) (* life 0.85))
    (set! (.-fillStyle ctx) color)
    (.beginPath ctx)
    (.arc ctx x y size 0 (* 2 js/Math.PI))
    (.fill ctx)))

;;
;; Node Position Helpers
;;

(defn get-node-bounds [node-id react-flow-instance canvas]
  "Get node's actual rendered bounding box in CANVAS coordinates"
  (when (and react-flow-instance canvas)
    (try
      (when-let [dom-element (.querySelector js/document (str "[data-id='" node-id "']"))]
        (let [node-rect (.getBoundingClientRect dom-element)
              canvas-rect (.getBoundingClientRect canvas)

              ;; Convert from viewport coords to canvas coords
              left (- (.-left node-rect) (.-left canvas-rect))
              top (- (.-top node-rect) (.-top canvas-rect))
              width (.-width node-rect)
              height (.-height node-rect)
              right (+ left width)
              bottom (+ top height)]
          {:left left
           :top top
           :right right
           :bottom bottom
           :center-x (+ left (/ width 2))
           :center-y (+ top (/ height 2))
           :width width
           :height height}))
      (catch js/Error e
        nil))))

(defn get-node-bottom-center [node-id rf-instance canvas]
  (when-let [bounds (get-node-bounds node-id rf-instance canvas)]
    {:x (:center-x bounds) :y (:bottom bounds)}))

(defn get-all-node-ids [rf-instance]
  (when rf-instance
    (try (mapv #(.-id %) (.getNodes rf-instance))
         (catch js/Error _ []))))

;;
;; Global Animation Loop (never stops)
;;

(defn start-global-loop! [rf-instance-atom]
  (when-not @global-animation-id
    (let [spawn-interval 1
          render-loop
          (fn render-loop []
            (when-let [canvas @global-canvas-ref]
              (let [ctx (.getContext canvas "2d")
                    width (.-width canvas)
                    height (.-height canvas)
                    now (js/Date.now)
                    rf-instance @rf-instance-atom
                    executing-nodes @executing-nodes-state  ; Read from global state

                    all-node-ids (when rf-instance (get-all-node-ids rf-instance))
                    node-bounds-list (when rf-instance
                                      (keep #(get-node-bounds % rf-instance canvas) all-node-ids))]

                (.clearRect ctx 0 0 width height)

                ;; Spawn from executing nodes
                (when (and rf-instance (seq executing-nodes))
                  (doseq [node-id executing-nodes]
                    (when-let [bounds (get-node-bounds node-id rf-instance canvas)]
                      (when (> (- now (get @last-spawn-times-state node-id 0)) spawn-interval)
                        (let [;; Use ACTUAL node width from DOM
                              node-width (:width bounds)
                              center-x (:center-x bounds)
                              bottom-y (:bottom bounds)
                              ;; Spawn across full actual width
                              spawn-x (+ center-x (- (rand node-width) (/ node-width 2)))]
                          (swap! particles-state conj (create-particle spawn-x bottom-y "#fcd34d"))
                          (swap! spawn-counter-state inc)
                          (swap! last-spawn-times-state assoc node-id now))))))

                ;; Update and draw particles
                (let [updated (mapv #(update-particle % node-bounds-list) @particles-state)
                      alive (filterv particle-alive? updated)]
                  (reset! particles-state alive)
                  (doseq [particle alive]
                    (draw-particle ctx particle)))

                (set! (.-globalAlpha ctx) 1.0)

                ;; Next frame
                (reset! global-animation-id (js/requestAnimationFrame render-loop)))))]
      (render-loop))))

;;
;; Component
;;

(defn particle-canvas [{:keys [react-flow-instance-atom executing-nodes get-node-color-fn trace-id]}]
  ;; Update global executing nodes state on each render
  (reset! executing-nodes-state (set executing-nodes))

  ;; Clear particles when trace changes (but not when same trace finishes)
  (when (and trace-id (not= @current-trace-id trace-id))
    (clear-particles!)
    (reset! current-trace-id trace-id))

  (r/create-class
   {:component-did-mount
    (fn [_]
      ;; Wait for canvas ref to be set, then initialize
      (js/setTimeout
       #(when-let [canvas @global-canvas-ref]
          (let [parent (.-parentElement canvas)]
            (set! (.-width canvas) (.-clientWidth parent))
            (set! (.-height canvas) (.-clientHeight parent)))
          ;; Only start loop if not already running
          (start-global-loop! react-flow-instance-atom))
       200))

      :component-will-unmount
      (fn [_]
        ;; Don't stop animation - let particles continue to fall naturally
        nil)

    :reagent-render
    (fn [{:keys [_react-flow-instance-atom _executing-nodes _get-node-color-fn _trace-id]}]
      [:canvas
       {:ref #(when % (reset! global-canvas-ref %))
        :style {:position "absolute"
                :top 0 :left 0
                :width "100%" :height "100%"
                :pointer-events "none"
                :z-index 5}}])}))  ; Close map, close create-class, close defn
