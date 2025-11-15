(ns components.node-details
  "Node details panel component"
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [store.subs :as subs]
            [store.events :as events]
            [utils.hooks :refer [use-subscribe]]))

(defn format-memory
  "Format memory state as pretty JSON"
  [memory]
  (when memory
    (js/JSON.stringify (clj->js memory) nil 2)))

(defn highlight-json
  "Apply Prism syntax highlighting to JSON"
  [json-str]
  (when (and json-str js/Prism)
    (try
      (js/Prism.highlight json-str
                         (aget (.-languages js/Prism) "json")
                         "json")
      (catch js/Error _
        ;; Fallback to plain JSON if Prism fails
        json-str))))

(defui event-item
  [{:keys [event]}]
  (let [{:keys [event-type relative-ms duration-ms status action-label node-id changes]} event
        color (case event-type
                :node-enter "text-blue-400"
                :node-exit "text-green-400"
                :memory-snapshot "text-purple-400"
                :dspy-call-start "text-yellow-400"
                :dspy-call-complete "text-yellow-400"
                "text-gray-400")]
    ($ :div.py-1
       ;; Event header line
       ($ :div.flex.items-start.gap-2.text-sm
          ($ :span {:class (str "font-mono " color)}
             (str "[" relative-ms "ms]"))
          ($ :span.text-gray-300 (name event-type))
          (when action-label
            ($ :span.text-blue-300 (str ": " action-label)))
          (when (and (not action-label) node-id)
            ($ :span.text-gray-500 (str " (" node-id ")")))
          (when duration-ms
            ($ :span.text-gray-500 (str " (" duration-ms "ms)")))
          (when status
            ($ :span.text-gray-400 (str " → " (name status)))))

       ;; Memory changes (if this is a memory-snapshot event)
       (when (and (= event-type :memory-snapshot) (seq changes))
         ($ :div.ml-6.mt-2.space-y-2
            (for [change changes]
              (let [key-name (if (keyword? (:key change))
                              (name (:key change))
                              (str (:key change)))
                    old-val (:old-value change)
                    new-val (:new-value change)
                    json-str (format-memory new-val)
                    highlighted (highlight-json json-str)]
                ($ :div.bg-gray-800.rounded.p-2.border-l-2.border-purple-500
                   {:key key-name}

                   ;; Change header
                   ($ :div.flex.items-center.gap-2.mb-1
                      ($ :span.text-xs.font-semibold.text-purple-400
                         (if (nil? old-val) "➕ " "📝 ")
                         key-name)
                      (when (some? old-val)
                        ($ :span.text-xs.text-gray-500 "modified")))

                   ;; Value (syntax highlighted)
                   (if highlighted
                     ($ :div.text-xs
                        {:dangerouslySetInnerHTML {:__html (str "<code class=\"language-json\">" highlighted "</code>")}})
                     ($ :pre.text-xs.text-gray-300 json-str))))))))))

(defn strip-trace-prefix
  "Strip trace-id prefix from node-id (e.g., 'uuid-0.1' -> '0.1')"
  [prefixed-id]
  (when prefixed-id
    (let [parts (clojure.string/split prefixed-id #"-(?=\d)")]
      (if (> (count parts) 1)
        (last parts)
        prefixed-id))))

(defn extract-node-memory-flow
  "Extract before/after memory state for a specific node"
  [events node-id]
  (let [;; Strip trace-id prefix to match event node-ids
        unprefixed-id (strip-trace-prefix node-id)
        _ (js/console.log "Extracting memory flow for node:" node-id "-> unprefixed:" unprefixed-id)

        ;; Find all events for this node (using unprefixed ID)
        node-events (filter #(= (:node-id %) unprefixed-id) events)
        _ (js/console.log "Found" (count node-events) "events for node")

        ;; Find memory snapshots for this node
        memory-events (filter #(= (:event-type %) :memory-snapshot) node-events)

        ;; Get the memory state and changes
        first-snapshot (first memory-events)
        memory-changes (mapcat :changes memory-events)]

    {:memory-before (:memory-state first-snapshot)
     :changes memory-changes
     :node-events node-events}))

(defui memory-flow-section
  [{:keys [node-id all-events]}]
  (let [{:keys [memory-before changes node-events]} (extract-node-memory-flow all-events node-id)]
    (if (seq changes)
      ($ :div.mb-6.bg-gray-800.rounded-lg.p-4.border.border-gray-700
         ($ :h4.text-sm.font-semibold.text-gray-300.mb-3.flex.items-center.gap-2
            ($ :span "")
            ($ :span "Memory Flow"))

         ;; Show what changed
         ($ :div.space-y-3
            (for [change changes]
              (let [key-name (if (keyword? (:key change))
                              (name (:key change))
                              (str (:key change)))
                    old-val (:old-value change)
                    new-val (:new-value change)]
                ($ :div.bg-gray-900.rounded.p-3.border-l-4.border-blue-500
                   {:key key-name}

                   ;; Key name
                   ($ :div.text-xs.font-semibold.text-blue-400.mb-2
                      (if (nil? old-val) "➕ " "📝 ")
                      key-name)

                   ;; Before state (if exists)
                   (when (some? old-val)
                     ($ :div.mb-2
                        ($ :div.text-xs.text-gray-500.mb-1 "Before:")
                        ($ :pre.text-xs.bg-gray-950.p-2.rounded.overflow-x-auto.text-gray-400
                           (format-memory old-val))))

                   ;; After state
                   ($ :div
                      ($ :div.text-xs.text-gray-500.mb-1 "After:")
                      ($ :pre.text-xs.bg-gray-950.p-2.rounded.overflow-x-auto.text-green-400
                         (format-memory new-val)))))))

      ;; No changes - return empty div
      ($ :div))))

(defui node-details
  []
  (let [selected-node (use-subscribe [::subs/selected-node])
        all-events (use-subscribe [::subs/execution-events])

        ;; Find the label for the selected node
        selected-node-label (when selected-node
                             (let [enter-event (first (filter #(and (= (:event-type %) :node-enter)
                                                                    (= (:node-id %) selected-node))
                                                             all-events))]
                               (or (:action-label enter-event) selected-node)))

        on-close (uix/use-callback
                  (fn []
                    (rf/dispatch [::events/clear-node-selection]))
                  [])]

    ($ :div.h-full.flex.flex-col.bg-gray-900.border-l.border-gray-700

       ;; Header
       ($ :div.p-4.border-b.border-gray-700.flex.flex-col.gap-1
          ($ :div.flex.items-center.justify-between
             ($ :h3.text-lg.font-bold.text-gray-100
                (if selected-node "Node Details" "EXECUTION TIMELINE"))
             (when selected-node
               ($ :button
                  {:class "text-gray-400 hover:text-gray-200 text-xl"
                   :on-click on-close}
                  "×")))
          ;; Selected node name
          (when selected-node
            ($ :div.text-sm.text-blue-400.font-mono
               selected-node-label)))

       ;; Content
       ($ :div.flex-1.overflow-y-auto.p-4

          (if selected-node
            ;; Node is selected - show only that node's data
            (let [unprefixed-id (strip-trace-prefix selected-node)
                  node-events (filter #(= (:node-id %) unprefixed-id) all-events)
                  {:keys [changes]} (extract-node-memory-flow all-events selected-node)]

              ($ :div
                 ;; Memory flow section
                 (when (seq changes)
                   ($ memory-flow-section {:node-id selected-node
                                          :all-events all-events}))

                 ;; Node-specific events
                 (when (seq node-events)
                   ($ :div.mt-6
                      ($ :h4.text-sm.font-semibold.text-gray-300.mb-2
                         ($ :span " ")
                         ($ :span (str "Node Events (" (count node-events) ")")))
                      ($ :div.space-y-1
                         (for [event node-events]
                           ($ event-item {:key (:event-id event) :event event})))))

                 ;; If no changes and no events, show message
                 (when (and (empty? changes) (empty? node-events))
                   ($ :div.text-sm.text-gray-500.text-center.mt-8
                      "No memory changes or events for this node."))))

            ;; No node selected - show full event timeline
            (if (seq all-events)
              ($ :div
                 ($ :h4.text-sm.font-semibold.text-gray-300.mb-2
                    ($ :span " ")
                    ($ :span (str "All Events (" (count all-events) ")")))
                 ($ :div.space-y-1
                    (for [event all-events]
                      ($ event-item {:key (:event-id event) :event event}))))

              ($ :div.text-sm.text-gray-500.text-center.mt-8
                 "No events captured. Node-level instrumentation is disabled."))))))))
