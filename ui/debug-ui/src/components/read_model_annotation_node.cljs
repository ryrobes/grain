(ns components.read-model-annotation-node
  "Custom React Flow annotation node for read model queries"
  (:require ["react" :as react]
            ["reactflow" :refer [Handle]]))

(defn safe-event-type
  "Safely extract event type as string"
  [event-type]
  (cond
    (keyword? event-type) (name event-type)
    (string? event-type) event-type
    :else (str event-type)))

(defn read-model-annotation-node
  "Custom annotation node showing read model queries with cyan styling.
   Plain React component for React Flow compatibility."
  [props]
  (let [data (.-data props)
        ;; Get read operations - might be JS array or ClojureScript vector
        reads-raw (when data (aget data "reads"))
        ;; Convert to ClojureScript if needed
        reads (when reads-raw
                (if (array? reads-raw)
                  (js->clj reads-raw :keywordize-keys true)
                  reads-raw))

        ;; Calculate totals
        num-queries (count reads)
        ;; Collect all event types across all queries
        all-event-types (into #{} (mapcat :event-types reads))
        ;; Collect all tags across all queries
        all-tags (into #{} (mapcat :event-tags reads))]

    ;; Debug logging
    ;; (js/console.log "read-model-annotation-node rendering")
    ;; (js/console.log "  reads:" (clj->js reads))
    ;; (js/console.log "  num-queries:" num-queries)
    ;; (js/console.log "  all-event-types:" (clj->js all-event-types))

    (react/createElement "div"
      #js {:className "p-3 bg-cyan-950 border border-cyan-700 rounded-lg shadow-lg cursor-pointer hover:border-cyan-500 transition-colors"
           :style #js {:minWidth "280px"
                      :maxWidth "360px"
                      :fontSize "11px"
                      :lineHeight "1.5"}}

      ;; Connection handle (right side to receive edge from main node)
      (react/createElement Handle
        #js {:type "target"
             :position "right"
             :id "right"
             :style #js {:background "#22d3ee"
                        :width "8px"
                        :height "8px"}})

      ;; Header
      (react/createElement "div"
        #js {:className "text-xs font-semibold text-cyan-400 mb-2 flex items-center gap-2"}
        "READ MODEL"
        (react/createElement "span"
          #js {:className "bg-cyan-800 text-cyan-300 px-2 py-0.5 rounded-full text-xs"}
          (str num-queries " " 
               ;(if (= num-queries 1) "query" "queries")
               )))

      ;; Content - show event types and tags
      (if (seq reads)
        (react/createElement "div"
          #js {:className "space-y-2"}

          ;; Event types section
          (when (seq all-event-types)
            (react/createElement "div"
              #js {:className "space-y-0.5"}
              (into-array
               (map
                (fn [event-type]
                  (react/createElement "div"
                    #js {:key (str event-type)
                         :className "text-xs text-cyan-300"}
                    (str "• " (safe-event-type event-type))))
                (take 4 (sort all-event-types))))))

          ;; Tags section
          (when (seq all-tags)
            (react/createElement "div"
              #js {:className "text-xs text-cyan-500 pt-2 border-t border-cyan-800"}
              (into-array
               (map
                (fn [[tag-key tag-value]]
                  (react/createElement "div"
                    #js {:key (str tag-key tag-value)
                         :className "truncate"}
                    (str (name tag-key) ": " (subs (str tag-value) 0 8) "...")))
                (take 2 all-tags))))))

        ;; No reads
        (react/createElement "div"
          #js {:className "text-xs text-cyan-500"}
          "No events queried")))))
