(ns components.tree-view
  "Behavior tree visualization using React Flow"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [store.subs :as subs]
            [store.events :as events]
            [styles.core :as s]
            [components.tree-flow-layout :as flow-layout]
            [components.node-enrichment :as enrichment]
            [components.custom-node :refer [custom-flow-node]]
            [components.annotation-node :refer [annotation-node]]
            [components.event-annotation-node :refer [event-annotation-node]]
            [components.read-model-annotation-node :refer [read-model-annotation-node]]
            [components.dspy-annotation-node :refer [dspy-annotation-node]]
            ["reactflow" :as rf-lib :default ReactFlow]
            ["reactflow" :refer [Controls MiniMap Background]]))

;; Adapt React Flow components for Reagent
(def react-flow (r/adapt-react-class ReactFlow))
(def react-flow-controls (r/adapt-react-class Controls))
(def react-flow-background (r/adapt-react-class Background))
(def react-flow-minimap (r/adapt-react-class MiniMap))

;;
;; Tree to React Flow Conversion
;;

(defn node-status-class
  "Get CSS class for node status"
  [status]
  (case status
    :pending "pending"
    :executing "executing"
    :success "success"
    :failure "failure"
    :skipped "skipped"
    "pending"))

(defn tree-node->react-flow-node
  "Convert a tree node to React Flow node format"
  [node x y status executing?]
  (let [class-name (if executing?
                    "executing"
                    (node-status-class status))]
    {:id (:node-id node)
     :type "default"
     :data {:label (:label node)}
     :position {:x x :y y}
     :className class-name
     :style {:cursor "pointer"}}))

(defn build-react-flow-nodes
  "Recursively build React Flow nodes from tree structure with layout"
  [node x y node-status-map executing-nodes]
  (let [status (get node-status-map (:node-id node) :pending)
        executing? (contains? executing-nodes (:node-id node))
        this-node (tree-node->react-flow-node node x y status executing?)
        children (:children node)]

    (if (seq children)
      ;; Has children: layout horizontally with spacing
      (let [child-spacing 120
            child-y (+ y 100)
            total-width (* (count children) child-spacing)
            start-x (- x (/ total-width 2))

            child-results (mapv
                          (fn [idx child]
                            (let [child-x (+ start-x (* idx child-spacing))]
                              (build-react-flow-nodes child child-x child-y node-status-map executing-nodes)))
                          (range (count children))
                          children)

            all-child-nodes (vec (mapcat :nodes child-results))
            all-child-edges (vec (mapcat :edges child-results))

            ;; Create edges from this node to children
            edges-to-children (vec (map-indexed
                                   (fn [idx child]
                                     {:id (str (:node-id node) "-" (:node-id child))
                                      :source (:node-id node)
                                      :target (:node-id child)
                                      :animated (contains? executing-nodes (:node-id child))})
                                   children))]

        {:nodes (vec (cons this-node all-child-nodes))
         :edges (vec (concat edges-to-children all-child-edges))})

      ;; No children: just this node
      {:nodes [this-node]
       :edges []})))

(defn tree->react-flow
  "Convert tree structure to React Flow nodes and edges" 
  [tree node-status-map executing-nodes]
  (when tree
    (build-react-flow-nodes tree 0 0 node-status-map (or executing-nodes #{}))))

;;
;; React Flow Component
;;

;; Track last execution event count to avoid excessive fitView calls
(defonce last-event-count (r/atom nil))

(defn tree-view-inner
  [{:keys [tree node-status-map executing-nodes selected-node current-trace execution-events]}]
  (let [trace-id (:trace-id current-trace)
        is-executing @(rf/subscribe [::subs/is-trace-executing])
        reactFlowInstance (r/atom nil)
        video-ref (r/atom nil)
        is-muted (r/atom true)
        view-mode @(rf/subscribe [::subs/view-mode])
        view-mode-str (when view-mode (.-name view-mode))
        trace-id-str (str trace-id)

        ;; Calculate nodes/edges
        result (when (and tree trace-id)
                 (let [base-result (flow-layout/tree-to-flow-diagram tree node-status-map executing-nodes trace-id-str)]
                   (enrichment/enrich-nodes-and-edges
                    (:nodes base-result)
                    (:edges base-result)
                    execution-events
                    trace-id-str
                    view-mode-str)))
        {:keys [nodes edges]} result

        on-node-click (fn [_event node]
                       (let [node-id (.-id node)]
                         ;; Toggle: if already selected, deselect; otherwise select
                         (if (= node-id selected-node)
                           (rf/dispatch [::events/clear-node-selection])
                           (rf/dispatch [::events/select-node node-id]))))

        has-data (and tree nodes edges (seq nodes))
        classifications @(rf/subscribe [::subs/node-classifications])

        ;; Track event count to trigger fitView only when it changes
        current-event-count (count execution-events)]

    ;; Auto-fit view ONLY when execution event count changes (not every render)
    (when (and is-executing
               @reactFlowInstance
               (not= @last-event-count current-event-count))
      (reset! last-event-count current-event-count)
      (js/requestAnimationFrame
       (fn []
         (js/requestAnimationFrame
          (fn []
            (when @reactFlowInstance
              (.fitView @reactFlowInstance
                       #js {:padding 0.2
                           :duration 150})))))))

    ;; Apply status classes and glow to nodes
    (let [executing-set (set executing-nodes)
          trace-still-executing is-executing  ; Use the existing is-executing variable

          ;; Build map of node-id to execution order (timeline index)
          node-to-timeline-idx (when selected-node
                                (let [node-durations (reduce
                                                     (fn [acc event]
                                                       (if (= (:event-type event) :node-enter)
                                                         (conj acc (:node-id event))
                                                         acc))
                                                     []
                                                     execution-events)
                                      unprefixed-selected (last (clojure.string/split selected-node #"-(?=\d)"))]
                                  (into {}
                                        (map-indexed (fn [idx node-id]
                                                      [node-id idx])
                                                    node-durations))))

          ;; Helper: Get node's primary color based on status and classification
          get-node-color (fn [status classification]
                          (cond
                            ;; Classification colors (when succeeded)
                            (and (= status "success") (= classification "command")) "#3b82f6"
                            (and (= status "success") (= classification "query")) "#10b981"
                            (and (= status "success") (= classification "computation")) "#f59e0b"
                            (and (= status "success") (= classification "conditional")) "#fde047"
                            ;; Status colors (default)
                            (= status "success") "#6ee7b7"
                            (= status "failure") "#fca5a5"
                            (= status "executing") "#fcd34d"
                            (= status "skipped") "#475569"
                            :else "#64748b"))  ; pending

          nodes-enhanced (mapv
                         (fn [node]
                           (let [node-id (:id node)
                                 unprefixed-id (last (clojure.string/split node-id #"-(?=\d)"))
                                 is-executing? (contains? executing-set unprefixed-id)
                                 is-selected? (= node-id selected-node)
                                 current-class (:className node)
                                 status (get node-status-map unprefixed-id :pending)

                                 ;; Determine node classification
                                 node-classification (cond
                                                      (contains? (:commands classifications) unprefixed-id) "command"
                                                      (contains? (:queries classifications) unprefixed-id) "query"
                                                      (contains? (:computations classifications) unprefixed-id) "computation"
                                                      (contains? (:conditionals classifications) unprefixed-id) "conditional"
                                                      :else nil)

                                 ;; Combine status class with classification
                                 combined-class (if node-classification
                                                 (str current-class " " node-classification)
                                                 current-class)

                                 ;; Dim unexecuted nodes after trace completes
                                 is-unexecuted? (and (not is-executing?)
                                                    (= status :pending)
                                                    (not trace-still-executing))
                                 opacity (if is-unexecuted? 0.3 1.0)

                                 ;; Get node's color for selection glow
                                 node-color (get-node-color current-class node-classification)]

                             (cond
                               ;; Selected node - add glow with node's own color
                               is-selected?
                               (-> node
                                   (assoc :className combined-class)
                                   (assoc :style (merge (:style node)
                                                       {:box-shadow (str "0 0 20px " node-color ", 0 0 40px " node-color)
                                                        :border-width "3px"
                                                        :opacity opacity})))

                               ;; Executing node - add executing class for animation
                               is-executing?
                               (assoc node :className (str "executing " (or node-classification ""))
                                      :style (merge (:style node) {:opacity 1.0}))

                               ;; Unexecuted node - dim it
                               is-unexecuted?
                               (-> node
                                   (assoc :className combined-class)
                                   (assoc :style (merge (:style node) {:opacity opacity})))

                               ;; Default - apply classification if present
                               :else
                               (-> node
                                   (assoc :className combined-class)
                                   (assoc :style (merge (:style node) {:opacity 1.0}))))))
                         nodes)

          ;; Dim edges to unexecuted nodes
          edges-enhanced (mapv
                         (fn [edge]
                           (let [source-id (:source edge)
                                 target-id (:target edge)

                                 ;; Check if source or target is a hub node (synthetic)
                                 is-source-hub? (or (clojure.string/starts-with? source-id "branch-")
                                                   (clojure.string/starts-with? source-id "merge-"))
                                 is-target-hub? (or (clojure.string/starts-with? target-id "branch-")
                                                   (clojure.string/starts-with? target-id "merge-"))

                                 ;; For non-hub nodes, check execution status
                                 unprefixed-target (when-not is-target-hub?
                                                    (last (clojure.string/split target-id #"-(?=\d)")))
                                 target-status (when unprefixed-target
                                                (get node-status-map unprefixed-target :pending))

                                 ;; Dim if: target is real node (not hub), never executed, and trace complete
                                 is-unexecuted? (and (not is-target-hub?)  ; Real node, not hub
                                                    unprefixed-target
                                                    (= target-status :pending)
                                                    (not trace-still-executing))]
                             (if is-unexecuted?
                               (assoc edge :style {:opacity 0.3})
                               edge)))
                         edges)]

      (if has-data
        [:div {:style {:width "100%" :height "100%"}}
         [react-flow
          {:key (str "reactflow-" trace-id)  ; Unique key per trace
           :nodes (clj->js nodes-enhanced)
           :edges (clj->js edges-enhanced)
           :nodeTypes #js {:annotation annotation-node
                          :event-annotation event-annotation-node
                          :read-model-annotation read-model-annotation-node
                          :dspy-annotation dspy-annotation-node}
           :onInit #(reset! reactFlowInstance %)
           :onNodeClick on-node-click
           :fitView true
           :fitViewOptions #js {:padding 0.2
                               :includeHiddenNodes false
                               :minZoom 0.3
                               :maxZoom 1.5}
           :attributionPosition "bottom-left"
           :nodesDraggable true
           :nodesConnectable true
           :elementsSelectable true}
          [react-flow-controls]
          [react-flow-background {:gap 20 :size 1 :color "#334155"}]
          [react-flow-minimap
           {:nodeColor (fn [node]
                        (let [class-name (.-className node)]
                          (case class-name
                            "pending" "#64748b"
                            "executing" "#fbbf24"
                            "success" "#10b981"
                            "failure" "#ef4444"
                            "skipped" "#475569"
                            "#64748b")))
            :maskColor "rgba(15, 23, 42, 0.8)"
            :style {:backgroundColor "#1e293b"}}]]]

        ;; No tree loaded - show video
        [:div {:style (merge s/flex s/items-center s/justify-center s/h-full s/text-gray-400)}
         [:video {:ref #(reset! video-ref %)
                 :src "grain-fall.mp4"
                 :autoPlay true
                 :loop true
                 :muted @is-muted
                 :playsInline true
                 :controls false
                 :on-click (fn []
                            (swap! is-muted not)
                            (when @video-ref
                              (set! (.-volume @video-ref) 0.3)))
                 :style {:max-width "600px"
                        :opacity 0.4
                        :border "none"
                        :cursor "pointer"}
                 :title (if @is-muted "Click to unmute" "Click to mute")}]]))))

(defn tree-view
  []
  (let [tree @(rf/subscribe [::subs/tree-structure])
        node-status-map @(rf/subscribe [::subs/node-status-map])
        executing-nodes @(rf/subscribe [::subs/executing-nodes])
        selected-node @(rf/subscribe [::subs/selected-node])
        current-trace @(rf/subscribe [::subs/current-trace])
        execution-events @(rf/subscribe [::subs/execution-events])]
    [tree-view-inner {:tree tree
                     :node-status-map node-status-map
                     :executing-nodes executing-nodes
                     :selected-node selected-node
                     :current-trace current-trace
                     :execution-events execution-events}]))
