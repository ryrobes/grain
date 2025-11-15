(ns components.node-enrichment
  "Enrich nodes with execution data (duration, memory changes, etc.)")

(defn extract-node-durations
  "Build a map of node-id → duration-ms from execution events"
  [events]
  (reduce
   (fn [acc event]
     (if (and (= (:event-type event) :node-exit)
              (:duration-ms event))
       (assoc acc (:node-id event) (:duration-ms event))
       acc))
   {}
   events))

(defn extract-memory-changes-by-node
  "Build a map of node-id → memory changes.

   Finds memory-snapshot events and attributes them to the most recent node."
  [events]
  (loop [remaining events
         current-node nil
         changes-map {}]
    (if (empty? remaining)
      changes-map

      (let [event (first remaining)]
        (cond
          ;; Node entry - track current node
          (= (:event-type event) :node-enter)
          (recur (rest remaining)
                 (:node-id event)
                 changes-map)

          ;; Memory snapshot - attribute to current node
          (and (= (:event-type event) :memory-snapshot)
               current-node
               (seq (:changes event)))
          (recur (rest remaining)
                 current-node
                 (update changes-map current-node
                        (fnil into [])
                        (:changes event)))

          ;; Other events
          :else
          (recur (rest remaining)
                 current-node
                 changes-map))))))

(defn extract-events-by-node
  "Build a map of node-id → emitted events.

   Finds :events-appended events and groups by node-id."
  [events]
  (reduce
   (fn [acc event]
     (if (and (= (:event-type event) :events-appended)
              (:node-id event)
              (seq (:events event)))
       (update acc (:node-id event)
               (fnil into [])
               (:events event))
       acc))
   {}
   events))

(defn extract-read-models-by-node
  "Build a map of node-id → read model queries.

   Finds :read-model-queried events and groups by node-id."
  [events]
  (reduce
   (fn [acc event]
     (if (and (= (:event-type event) :read-model-queried)
              (:node-id event))
       (update acc (:node-id event)
               (fnil conj [])
               event)
       acc))
   {}
   events))

(defn extract-dspy-events-by-node
  "Build a map of node-id → DSPy reasoning events.

   Finds :events-appended events with :grain.agent/reasoned type."
  [events]
  (reduce
   (fn [acc event]
     (if (= (:event-type event) :events-appended)
       ;; Check if any of the events are DSPy reasoning events
       (let [dspy-event (first (filter #(= (:type %) :grain.agent/reasoned)
                                      (:events event)))]
         (if (and dspy-event (:node-id event))
           (assoc acc (:node-id event) dspy-event)
           acc))
       acc))
   {}
   events))

(defn format-value
  "Format a value for display with better detail"
  [val]
  (cond
    ;; Strings - show up to 120 chars with better truncation
    (string? val)
    (let [truncated (subs val 0 (min 120 (count val)))]
      (str "\"" truncated (when (> (count val) 120) "...") "\""))

    ;; Maps - show nested structure (up to 2 levels)
    (map? val)
    (let [entries (->> val
                      (take 5)
                      (map (fn [[k v]]
                            (let [key-str (name k)
                                  val-str (cond
                                           (string? v) (str "\"" (subs v 0 (min 30 (count v)))
                                                           (when (> (count v) 30) "...") "\"")
                                           (map? v) (str "{" (count v) " fields}")
                                           (coll? v) (str "[" (count v) "]")
                                           (nil? v) "null"
                                           :else (str v))]
                              (str key-str ": " val-str))))
                      (clojure.string/join "\n  "))]
      (str "{\n  " entries (when (> (count val) 5) "\n  ...") "\n}"))

    ;; Vectors/lists - show first few items
    (vector? val)
    (if (empty? val)
      "[]"
      (str "[" (count val) " items]"))

    ;; Null
    (nil? val)
    "null"

    ;; Numbers, booleans, keywords
    (or (number? val) (boolean? val) (keyword? val))
    (str val)

    ;; Other
    :else
    (pr-str val)))

(defn format-memory-diff
  "Format memory changes for display in annotation.

   Shows actual structure with indentation."
  [changes]
  (when (seq changes)
    (->> changes
         (take 4)  ; Show up to 4 changes
         (map (fn [change]
               (let [key-name (name (:key change))
                     old-val (:old-value change)
                     new-val (:new-value change)
                     formatted-val (format-value new-val)]
                 ;; Show key name in bold-ish style with formatted value below
                 (if (nil? old-val)
                   ;; New value (no old value)
                   (str "➕ " key-name "\n" formatted-val)
                   ;; Modified value (show old → new for simple types)
                   (if (or (string? new-val) (number? new-val) (boolean? new-val) (keyword? new-val))
                     (str "📝 " key-name "\n" (format-value old-val) " → " formatted-val)
                     (str "📝 " key-name "\n" formatted-val))))))
         (clojure.string/join "\n\n"))))

(defn enrich-node-with-data
  "Enrich a node with duration and memory diff for display"
  [node duration-map changes-map]
  (let [node-id (:id node)
        ;; Strip trace-id prefix from node-id (format: "uuid-0.2" -> "0.2")
        unprefixed-id (last (clojure.string/split node-id #"-(?=\d)"))
        duration (get duration-map unprefixed-id)
        changes (get changes-map unprefixed-id)
        current-label (get-in node [:data :label])
        node-type (get-in node [:data :node-type])]

    ;; Use custom node type and structure data for rich display
    (-> node
        (assoc :type "custom")
        (update :data merge
                {:label current-label
                 :duration duration
                 :memory-diff (when (seq changes) (format-memory-diff changes))
                 :node-type node-type}))))

(defn create-annotation-node
  "Create an annotation node positioned to the LEFT of a main node.
   Now uses custom 'annotation' node type with Prism highlighting.
   y-offset allows smart vertical positioning to avoid collisions."
  [main-node-id position changes trace-id-str y-offset]
  {:id (str main-node-id "-annotation")
   :data {:changes changes
          :trace-id trace-id-str}  ; Include trace-id to force refresh
   :type "annotation"
   :position {:x (- (:x position) 460)  ; 460px to the LEFT (accounting for wider nodes)
              :y (+ (:y position) y-offset)}
   :className "annotation-node"  ; Special class for styling
   :style {:fontSize "11px"            ; Slightly larger font for readability
          :padding "12px 14px"         ; More padding
          :backgroundColor "#0B1629FF"   ; Darker background
          :border "1px dashed #64748b" ; Lighter dashed border
          :borderRadius "6px"          ; Rounded corners
          :minWidth "280px"            ; Wider box
          :maxWidth "360px"            ; Allow even wider for nested structures
          :whiteSpace "pre-wrap"
          :textAlign "left"
          :lineHeight "1.5"            ; Better readability
          :fontFamily "ui-monospace, 'Fira Code', monospace"  ; Monospace for data
          :color "#cbd5e1"             ; Lighter text color
          :boxShadow "0 2px 8px rgba(0,0,0,0.3)"}  ; Subtle shadow
   :draggable false})

(defn create-event-annotation-node
  "Create an event annotation node positioned to the RIGHT of a main node.
   Uses custom 'event-annotation' node type with purple styling."
  [main-node-id position events trace-id-str y-offset]
  {:id (str main-node-id "-event-annotation")
   :data {:events events
          :trace-id trace-id-str}  ; Include trace-id to force refresh
   :type "event-annotation"
   :position {:x (+ (:x position) 460)  ; 460px to the RIGHT
              :y (+ (:y position) y-offset)}
   :draggable false})

(defn create-dspy-annotation-node
  "Create a DSPy annotation node positioned to the RIGHT of a main node.
   Uses custom 'dspy-annotation' node type with amber styling."
  [main-node-id position dspy-event trace-id-str y-offset]
  {:id (str main-node-id "-dspy-annotation")
   :data {:dspyEvent dspy-event
          :trace-id trace-id-str}
   :type "dspy-annotation"
   :position {:x (+ (:x position) 460)  ; 460px to the RIGHT (same lane as events)
              :y (+ (:y position) y-offset)}
   :draggable false})

(defn create-read-model-annotation-node
  "Create a read model annotation node on the left.
   Uses custom 'read-model-annotation' node type with cyan styling.
   y-offset allows smart vertical positioning to avoid collisions."
  [main-node-id position reads trace-id-str y-offset]
  {:id (str main-node-id "-read-model-annotation")
   :data {:reads reads
          :trace-id trace-id-str}
   :type "read-model-annotation"
   :position {:x (- (:x position) 460)  ; 460px to the LEFT (same as memory lane)
              :y (+ (:y position) y-offset)}  ; Smart vertical offset
   :draggable false})

(defn create-annotation-edge
  "Create a dashed edge from main node to annotation"
  [main-node-id annotation-id]
  {:id (str annotation-id "-edge")
   :source (str main-node-id)     ; From main node
   :target (str annotation-id)     ; To annotation
   :sourceHandle "left"            ; Use left handle on main node
   :targetHandle "right"           ; Use right handle on annotation
   :type "default"
   :animated false
   :style {:stroke "#a78bfa"        ; Purple/lavender - more visible
          :strokeWidth 2           ; camelCase for React
          :strokeDasharray "5,5"}  ; camelCase for React
   :label ""
   :labelBgStyle {:fill "#1e293b"}
   :labelStyle {:fill "#a78bfa" :fontSize "12px"}})

(defn create-event-annotation-edge
  "Create a dashed purple edge from main node to event annotation"
  [main-node-id annotation-id]
  {:id (str annotation-id "-edge")
   :source (str main-node-id)     ; From main node
   :target (str annotation-id)     ; To event annotation
   :sourceHandle "right"           ; Use right handle on main node
   :targetHandle "left"            ; Use left handle on event annotation
   :type "default"
   :animated false
   :style {:stroke "#a78bfa"        ; Purple/lavender
          :strokeWidth 2           ; camelCase for React
          :strokeDasharray "8,4"}  ; Different dash pattern for events
   :label ""
   :labelBgStyle {:fill "#1e293b"}
   :labelStyle {:fill "#a78bfa" :fontSize "12px"}})

(defn create-dspy-annotation-edge
  "Create a dashed emerald green edge from main node to DSPy annotation"
  [main-node-id annotation-id]
  {:id (str annotation-id "-edge")
   :source (str main-node-id)     ; From main node
   :target (str annotation-id)     ; To DSPy annotation
   :sourceHandle "right"           ; Use right handle on main node
   :targetHandle "left"            ; Use left handle on annotation
   :type "default"
   :animated false
   :style {:stroke "#6ee7b7"        ; Emerald green
          :strokeWidth 2           ; camelCase for React
          :strokeDasharray "10,5"} ; Different dash pattern for DSPy
   :label ""
   :labelBgStyle {:fill "#1e293b"}
   :labelStyle {:fill "#6ee7b7" :fontSize "12px"}})

(defn create-read-model-annotation-edge
  "Create a dashed cyan edge from main node to read model annotation"
  [main-node-id annotation-id]
  {:id (str annotation-id "-edge")
   :source (str main-node-id)     ; From main node
   :target (str annotation-id)     ; To read model annotation
   :sourceHandle "left"            ; Use left handle on main node
   :targetHandle "right"           ; Use right handle on annotation
   :type "default"
   :animated false
   :style {:stroke "#22d3ee"        ; Cyan
          :strokeWidth 2           ; camelCase for React
          :strokeDasharray "5,3"}  ; Different dash pattern
   :label ""
   :labelBgStyle {:fill "#1e293b"}
   :labelStyle {:fill "#22d3ee" :fontSize "12px"}})

(defn enrich-nodes-and-edges
  "Enrich nodes with execution data and create annotation edges.
   Filters annotations based on view-mode:
   - \"control-flow\" - No annotations (clean tree only)
   - \"event-model\" - Events + Read Models only
   - \"data-flow\" - Memory only
   - \"hybrid\" - Everything (default)
   Returns {:nodes [...] :edges [...]}"
  [nodes edges events trace-id-str view-mode-str]
  (let [duration-map (extract-node-durations events)
        changes-map (extract-memory-changes-by-node events)
        events-map (extract-events-by-node events)
        read-models-map (extract-read-models-by-node events)
        dspy-events-map (extract-dspy-events-by-node events)

        ;; Determine what to show based on view mode (use string comparison)
        show-read-models? (or (= view-mode-str "hybrid")
                             (= view-mode-str "event-model"))
        show-memory? (or (= view-mode-str "hybrid")
                        (= view-mode-str "data-flow"))
        show-events? (or (= view-mode-str "hybrid")
                        (= view-mode-str "event-model"))
        show-dspy? (or (= view-mode-str "hybrid")
                      (= view-mode-str "event-model"))]

    ;; Debug logging
    (js/console.log "enrich-nodes-and-edges called")
    (js/console.log "  dspy-events-map:" (clj->js dspy-events-map))
    (js/console.log "  dspy-events-map keys:" (keys dspy-events-map))
    (js/console.log "  show-dspy?:" show-dspy?)

    (let [;; Process nodes and collect annotation edges
          result (reduce
                (fn [acc node]
                  (let [node-id (:id node)
                        unprefixed-id (last (clojure.string/split node-id #"-(?=\d)"))
                        duration (get duration-map unprefixed-id)
                        changes (get changes-map unprefixed-id)
                        emitted-events (get events-map unprefixed-id)
                        read-models (get read-models-map unprefixed-id)
                        dspy-event (get dspy-events-map unprefixed-id)
                        enriched-node node

                        ;; Smart vertical positioning for left-side annotations
                        has-read-model? (seq read-models)
                        has-memory? (seq changes)

                        ;; Smart vertical positioning for right-side annotations (DSPy + Events)
                        has-dspy? (and dspy-event show-dspy?)
                        has-events? (seq emitted-events)

                        _ (when dspy-event
                            (js/console.log "Found DSPy event for node" unprefixed-id ":" (clj->js dspy-event)))
                        _ (when has-dspy?
                            (js/console.log "Will create DSPy annotation for node" unprefixed-id))

                        ;; Calculate Y offsets to avoid collisions
                        ;; Increased spacing to account for padding/borders on annotation boxes
                        read-model-y-offset (cond
                                             ;; Both exist - position read model above
                                             (and has-read-model? has-memory?) -180
                                             ;; Only read model - center it
                                             has-read-model? 0
                                             ;; No read model
                                             :else 0)

                        memory-y-offset (cond
                                         ;; Both exist - position memory below
                                         (and has-read-model? has-memory?) 180
                                         ;; Only memory - center it
                                         has-memory? 0
                                         ;; No memory
                                         :else 0)

                        ;; Right-side stacking: DSPy above, Events below
                        dspy-y-offset (cond
                                       ;; Both exist - DSPy goes above
                                       (and has-dspy? has-events?) -150
                                       ;; Only DSPy - center it
                                       has-dspy? 0
                                       :else 0)

                        event-y-offset (cond
                                        ;; Both exist - Events go below
                                        (and has-dspy? has-events?) 150
                                        ;; Only events - center it
                                        has-events? 0
                                        :else 0)

                        ;; Add read model annotation first (if present AND view mode allows)
                        acc-with-read-models (if (and has-read-model? show-read-models?)
                                              (let [read-model-node (create-read-model-annotation-node
                                                                     node-id
                                                                     (:position node)
                                                                     read-models
                                                                     trace-id-str
                                                                     read-model-y-offset)
                                                    read-model-edge (create-read-model-annotation-edge
                                                                     node-id
                                                                     (:id read-model-node))]
                                                (-> acc
                                                    (update :nodes conj enriched-node read-model-node)
                                                    (update :edges conj read-model-edge)))
                                              ;; No read models - just add the node
                                              (update acc :nodes conj enriched-node))

                        ;; Add memory annotation (if present AND view mode allows)
                        acc-with-memory (if (and has-memory? show-memory?)
                                         (let [annotation-node (create-annotation-node
                                                                node-id
                                                                (:position node)
                                                                changes
                                                                trace-id-str
                                                                memory-y-offset)
                                               annotation-edge (create-annotation-edge node-id (:id annotation-node))]
                                           (-> acc-with-read-models
                                               (update :nodes conj annotation-node)
                                               (update :edges conj annotation-edge)))
                                         ;; No changes - keep as-is
                                         acc-with-read-models)

                        ;; Add DSPy annotation (if present AND view mode allows)
                        acc-with-dspy (if has-dspy?
                                       (do
                                         (js/console.log "Creating DSPy annotation node for" node-id)
                                         (let [dspy-annotation-node (create-dspy-annotation-node
                                                                     node-id
                                                                     (:position node)
                                                                     dspy-event
                                                                     trace-id-str
                                                                     dspy-y-offset)
                                               dspy-annotation-edge (create-dspy-annotation-edge
                                                                     node-id
                                                                     (:id dspy-annotation-node))]
                                           (js/console.log "DSPy annotation node created:" (clj->js dspy-annotation-node))
                                           (-> acc-with-memory
                                               (update :nodes conj dspy-annotation-node)
                                               (update :edges conj dspy-annotation-edge))))
                                       ;; No DSPy - keep as-is
                                       acc-with-memory)

                        ;; Add event annotation (if present AND view mode allows)
                        acc-with-events (if (and (seq emitted-events) show-events?)
                                         (let [event-annotation-node (create-event-annotation-node
                                                                      node-id
                                                                      (:position node)
                                                                      emitted-events
                                                                      trace-id-str
                                                                      event-y-offset)
                                               event-annotation-edge (create-event-annotation-edge node-id (:id event-annotation-node))]
                                           (-> acc-with-dspy
                                               (update :nodes conj event-annotation-node)
                                               (update :edges conj event-annotation-edge)))
                                         ;; No events - keep as-is
                                         acc-with-dspy)]
                    acc-with-events))
                {:nodes [] :edges edges}
                nodes)]

      result)))
