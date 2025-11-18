;; Quick script to inspect the wizard trace
(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
(require '[clojure.pprint :as pp])

;; Get the most recent trace
(def traces (debug/list-trace-summaries :limit 1))
(def latest-trace (first traces))

(println "\n=== Latest Trace Summary ===")
(pp/pprint latest-trace)

;; Get the full trace
(when-let [trace-id (:trace-id latest-trace)]
  (def full-trace (debug/get-trace trace-id))

  (println "\n=== Tree Structure ===")
  (pp/pprint (:tree-structure full-trace))

  (println "\n=== Checking for View Nodes ===")
  (letfn [(find-view-nodes [node]
            (cond
              (and (map? node) (= :view (:type node)))
              (do
                (println "Found view node!")
                (pp/pprint node)
                [node])

              (map? node)
              (mapcat find-view-nodes (:children node))

              :else
              []))]
    (let [view-nodes (find-view-nodes (:tree-structure full-trace))]
      (println "\nTotal view nodes found:" (count view-nodes)))))
