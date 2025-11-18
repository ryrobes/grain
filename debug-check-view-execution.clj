;; Check if view nodes are actually executing and storing output
(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
(require '[clojure.pprint :as pp])

(def traces (debug/list-trace-summaries :limit 1))
(def latest-trace (first traces))

(when-let [trace-id (:trace-id latest-trace)]
  (def full-trace (debug/get-trace trace-id))

  (println "\n=== Execution Events for View Nodes ===")
  (doseq [event (:execution-events full-trace)]
    (when (and (= :view (get-in event [:node-type]))
               (#{:node-enter :node-exit} (:event-type event)))
      (println "\n" (:event-type event) "-" (:node-id event))
      (pp/pprint event)))

  (println "\n=== Memory Snapshots with View Outputs ===")
  (doseq [event (:execution-events full-trace)]
    (when (= :memory-snapshot (:event-type event))
      (let [view-outputs (get-in event [:memory-state
                                        :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs])]
        (when view-outputs
          (println "\nFound view outputs:")
          (doseq [[node-id hiccup] view-outputs]
            (println "  " node-id ":" (take 50 (str hiccup)) "..."))))))

  (println "\n=== Final Memory State ===")
  (let [final-snapshot (->> (:execution-events full-trace)
                           (filter #(= :memory-snapshot (:event-type %)))
                           last)
        view-outputs (get-in final-snapshot [:memory-state
                                             :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs])]
    (println "View outputs in final memory:")
    (pp/pprint (keys view-outputs))))
