;; Check what's actually in memory snapshots
(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
(require '[clojure.pprint :as pp])

(def traces (debug/list-trace-summaries :limit 1))
(def latest-trace (first traces))

(when-let [trace-id (:trace-id latest-trace)]
  (def full-trace (debug/get-trace trace-id))

  (println "\n=== All Memory Snapshots ===")
  (doseq [event (filter #(= :memory-snapshot (:event-type %)) (:execution-events full-trace))]
    (println "\n--- Memory at" (:relative-ms event) "ms ---")
    (println "Keys:" (keys (:memory-state event)))
    (when-let [view-outputs (get (:memory-state event)
                                 :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs)]
      (println "View outputs found!")
      (println "  Keys:" (keys view-outputs))
      (doseq [[k v] view-outputs]
        (println "  " k ":" (take 80 (str v)) "..."))))

  (println "\n=== Final Memory State ===")
  (let [final-snapshot (->> (:execution-events full-trace)
                           (filter #(= :memory-snapshot (:event-type %)))
                           last)]
    (when final-snapshot
      (println "All keys in final memory:")
      (pp/pprint (keys (:memory-state final-snapshot)))
      (println "\nView outputs:")
      (pp/pprint (get (:memory-state final-snapshot)
                     :ai.obney.grain.behavior-tree-v2.core.nodes/view-outputs)))))
