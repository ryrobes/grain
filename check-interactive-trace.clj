(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
(require '[clojure.pprint :as pp])

(def traces (debug/list-trace-summaries :limit 1))
(println "\n=== Latest Trace ===")
(pp/pprint (first traces))

(when-let [trace-id (:trace-id (first traces))]
  (def full-trace (debug/get-trace trace-id))
  (println "\n=== Tree Structure (first few nodes) ===")
  (pp/pprint (take 3 (get-in full-trace [:tree-structure :children]))))
