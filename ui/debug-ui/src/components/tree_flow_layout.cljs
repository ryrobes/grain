(ns components.tree-flow-layout
  "Flow-based layout for behavior trees - shows execution flow, not just structure")

(def pastel-colors
  "Timeline colors for node highlighting"
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

;; Forward declaration for mutual recursion
(declare layout-fallback)

;;
;; Layout Constants
;;

(def node-width 320)        ; Width for action/condition nodes (increased for readability)
(def node-height 60)        ; Height for nodes
(def h-spacing 300)         ; Horizontal spacing between nodes (room for annotations)
(def v-spacing 180)         ; Vertical spacing between sequence steps (more room for annotations)
(def branch-spacing 900)    ; Spacing between fallback branches (room for 2 annotations per side)
(def annotation-offset 360) ; How far to place annotations from main nodes (adjusted for wider nodes)

;;
;; Flow Layout Algorithm
;;

(defn create-flow-node
  "Create a React Flow node from a tree node"
  [tree-node x y status]
  {:id (:node-id tree-node)
   :type "default"
   :data {:label (:label tree-node)
          :node-type (:type tree-node)}
   :position {:x x :y y}
   :className (name (or status :pending))
   :style {:cursor "pointer"}})

(defn create-edge
  "Create a React Flow edge between two nodes"
  [from-id to-id & {:keys [animated label]}]
  {:id (str from-id "-" to-id)
   :source (str from-id)  ; Ensure string
   :target (str to-id)    ; Ensure string
   :type "default"        ; Explicit type
   :animated (boolean animated)
   :label (or label "")
   :style {:stroke "#94a3b8" :strokeWidth 2}})

(defn layout-sequence
  "Layout sequence nodes in a vertical flow (top to bottom).
   Conditions create branching: success path continues, failure path ends.
   Returns {:nodes [...] :edges [...] :start-node-id :end-node-id :width :height}"
  [children start-x start-y node-status-map]
  (loop [remaining children
         y start-y
         nodes []
         edges []
         prev-id nil]
    (if (empty? remaining)
      ;; Done - return accumulated nodes and edges
      {:nodes nodes
       :edges edges
       :start-node-id (when (seq nodes) (:id (first nodes)))
       :end-node-id prev-id
       :width node-width
       :height (- y start-y)}

      ;; Process next child
      (let [child (first remaining)
            child-type (:type child)]
        (cond
          ;; Condition - show as decision point with branching
          (= :condition child-type)
          (let [node-id (:node-id child)
                status (get node-status-map node-id :pending)
                ;; Create hexagonal decision node (simpler than rotation)
                condition-node (merge (create-flow-node child start-x y status)
                                    {:type "default"
                                     :style {:borderRadius "12px"
                                            :borderWidth "3px"
                                            :borderStyle "double"
                                            :minWidth "150px"}})

                ;; Edge from previous node (labeled YES if coming from condition)
                prev-is-condition? (and (seq nodes) (= :condition (:type (:data (peek nodes)))))
                edge-in (when prev-id
                         (create-edge prev-id node-id :label (when prev-is-condition? "YES")))

                ;; Create failure branch (grey unless actually taken)
                condition-failed? (= status :failure)
                fail-node-id (str node-id "-fail")
                fail-node {:id fail-node-id
                          :type "default"
                          :data {:label "✗"}
                          :position {:x (+ start-x 350) :y y}  ; Further right to avoid annotations
                          :className (if condition-failed? "failure" "skipped")  ; Use skipped for subtle
                          :style {:fontSize "12px"
                                 :padding "6px 10px"
                                 :minWidth "40px"
                                 :opacity (if condition-failed? 1 0.3)}}
                fail-edge (create-edge node-id fail-node-id :label "NO")

                ;; Success edge (to next node or to implicit success)
                ;; We'll add this when we process the next node
                ]
            (recur (rest remaining)
                   (+ y v-spacing)
                   (if fail-node
                     (conj nodes condition-node fail-node)
                     (conj nodes condition-node))
                   (cond-> edges
                     edge-in (conj edge-in)
                     fail-edge (conj fail-edge))
                   node-id))

          ;; Action - regular node
          (= :action child-type)
          (let [node-id (:node-id child)
                status (get node-status-map node-id :pending)
                node (create-flow-node child start-x y status)
                edge (when prev-id
                      (create-edge prev-id node-id :label (when (= :condition (:type (peek nodes)))
                                                            "YES")))]
            (recur (rest remaining)
                   (+ y v-spacing)
                   (conj nodes node)
                   (if edge (conj edges edge) edges)
                   node-id))

          ;; Nested sequence - flatten it
          (= :sequence child-type)
          (let [result (layout-sequence (:children child) start-x y node-status-map)
                edge (when prev-id
                      (create-edge prev-id (:start-node-id result)))]
            (recur (rest remaining)
                   (+ y (:height result) v-spacing)
                   (into nodes (:nodes result))
                   (into (if edge (conj edges edge) edges) (:edges result))
                   (:end-node-id result)))

          ;; Nested fallback - show branches
          (= :fallback child-type)
          (let [result (layout-fallback (:children child) start-x y node-status-map)
                edge (when prev-id
                      (create-edge prev-id (:start-node-id result)))]
            (recur (rest remaining)
                   (+ y (:height result) v-spacing)
                   (into nodes (:nodes result))
                   (into (if edge (conj edges edge) edges) (:edges result))
                   (:end-node-id result)))

          ;; Parallel - show side-by-side (similar to fallback but all execute)
          (= :parallel child-type)
          (let [result (layout-fallback (:children child) start-x y node-status-map)
                edge (when prev-id
                      (create-edge prev-id (:start-node-id result)))]
            (recur (rest remaining)
                   (+ y (:height result) v-spacing)
                   (into nodes (:nodes result))
                   (into (if edge (conj edges edge) edges) (:edges result))
                   (:end-node-id result)))

          ;; Unknown - skip
          :else
          (recur (rest remaining) y nodes edges prev-id))))))

(defn layout-fallback
  "Layout fallback branches side-by-side (fan-out, fan-in).
   Returns {:nodes [...] :edges [...] :start-node-id :end-node-id :width :height}"
  [branches start-x start-y node-status-map]
  (let [num-branches (count branches)
        total-width (* num-branches (+ node-width branch-spacing))
        start-x-offset (- start-x (/ total-width 2))

        ;; Create branch-start decision node (junction hub)
        branch-start-id (str "branch-" (random-uuid))
        branch-start-node {:id branch-start-id
                          :type "default"
                          :data {:label ""}  ; Empty label
                          :position {:x start-x :y start-y}
                          :className "branch-hub"
                          :style {:width "12px"
                                 :height "12px"
                                 :minWidth "12px"
                                 :padding "0"
                                 :borderRadius "50%"
                                 :backgroundColor "#64748b"
                                 :border "2px solid #94a3b8"
                                 :cursor "default"}}

        ;; Create branch-end merge node
        branch-end-y (atom start-y)

        ;; Layout each branch
        branch-results
        (map-indexed
         (fn [idx branch]
           (let [branch-x (+ start-x-offset (* idx (+ node-width branch-spacing)))
                 branch-y (+ start-y v-spacing)]
             (cond
               ;; Branch is a sequence - layout its children
               (= :sequence (:type branch))
               (layout-sequence (:children branch) branch-x branch-y node-status-map)

               ;; Branch is a fallback - nested fallback!
               (= :fallback (:type branch))
               (layout-fallback (:children branch) branch-x branch-y node-status-map)

               ;; Branch is a leaf node (action or condition)
               (#{:action :condition} (:type branch))
               (let [node-id (:node-id branch)
                     status (get node-status-map node-id :pending)
                     node (create-flow-node branch branch-x branch-y status)]
                 {:nodes [node]
                  :edges []
                  :start-node-id node-id
                  :end-node-id node-id
                  :width node-width
                  :height node-height})

               ;; Unknown - skip but log
               :else
               (do
                 (js/console.warn "Unknown branch type:" (:type branch) branch)
                 {:nodes [] :edges [] :start-node-id nil :end-node-id nil :width 0 :height 0}))))
         branches)

        ;; Find max height for branch-end positioning
        max-height (apply max (map :height branch-results))
        branch-end-y (+ start-y v-spacing max-height v-spacing)
        branch-end-id (str "merge-" (random-uuid))
        branch-end-node {:id branch-end-id
                        :type "default"
                        :data {:label ""}  ; Empty label
                        :position {:x start-x :y branch-end-y}
                        :className "branch-hub"
                        :style {:width "12px"
                               :height "12px"
                               :minWidth "12px"
                               :padding "0"
                               :borderRadius "50%"
                               :backgroundColor "#64748b"
                               :border "2px solid #94a3b8"
                               :cursor "default"}}

        ;; Create edges from branch-start to each branch
        start-edges (map-indexed
                     (fn [idx result]
                       (when (:start-node-id result)
                         (create-edge branch-start-id (:start-node-id result)
                                     :label (str "Try " (inc idx)))))
                     branch-results)

        ;; Create edges from each branch to branch-end
        end-edges (map
                   (fn [result]
                     (when (:end-node-id result)
                       (create-edge (:end-node-id result) branch-end-id)))
                   branch-results)

        ;; Combine all nodes and edges
        all-nodes (concat [branch-start-node]
                         (mapcat :nodes branch-results)
                         [branch-end-node])
        all-edges (concat start-edges
                         (mapcat :edges branch-results)
                         end-edges)
        all-edges-clean (filter some? all-edges)]

    {:nodes (vec all-nodes)
     :edges (vec all-edges-clean)
     :start-node-id branch-start-id
     :end-node-id branch-end-id
     :width total-width
     :height (+ v-spacing max-height v-spacing)}))

(defn tree-to-flow-diagram
  "Convert behavior tree to flow diagram (not tree structure).

   Flattens sequences into linear chains.
   Shows fallbacks as branch points.
   Hides composite meta-nodes."
  [tree node-status-map executing-nodes trace-id-prefix]
  (when tree
    ;; Prefix all node IDs with trace-id to make them globally unique
    (let [prefix-node-id (fn [node-id] (str trace-id-prefix "-" node-id))

          ;; Rewrite tree to have prefixed node IDs
          prefixed-tree (clojure.walk/postwalk
                         (fn [x]
                           (if (and (map? x) (:node-id x))
                             (update x :node-id prefix-node-id)
                             x))
                         tree)

          ;; Prefix keys in node-status-map
          prefixed-status-map (into {}
                                   (map (fn [[k v]]
                                         [(prefix-node-id k) v])
                                       node-status-map))

          tree-type (:type prefixed-tree)
          result (cond
                   ;; Root is a sequence - flatten it
                   (= :sequence tree-type)
                   (layout-sequence (:children prefixed-tree) 400 50 prefixed-status-map)

                   ;; Root is a fallback - show branches
                   (= :fallback tree-type)
                   (layout-fallback (:children prefixed-tree) 400 50 prefixed-status-map)

                   ;; Root is parallel - show concurrent branches
                   (= :parallel tree-type)
                   (layout-fallback (:children prefixed-tree) 400 50 prefixed-status-map)

                   ;; Root is a leaf - just show it
                   (#{:action :condition} tree-type)
                   (let [node (create-flow-node prefixed-tree 400 50 (get prefixed-status-map (:node-id prefixed-tree) :pending))]
                     {:nodes [node]
                      :edges []})

                   ;; Unknown
                   :else
                   {:nodes [] :edges []})]

      (select-keys result [:nodes :edges]))))
