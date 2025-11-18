(require '[ai.obney.grain.flow-session-manager.interface :as fsm])
(require '[ai.obney.grain.behavior-tree-v2-debug.interface :as debug])
(require '[clojure.pprint :as pp])

(println "\n=== Active Sessions ===")
(def sessions (fsm/list-active-sessions))
(println "Count:" (count sessions))

(when (seq sessions)
  (doseq [session sessions]
    (println "\nSession:" (:session-id session))
    (println "  Status:" @(:status session))
    (println "  Flow name:" (:flow-name session))
    (println "  Trace ID:" (:trace-id session))
    (println "  Started:" (:started-at session))
    (when-let [st-mem (:st-memory session)]
      (println "  Memory keys:" (keys @st-mem))
      (println "  Session ID in memory:" (::ai.obney.grain.debug-example-service.core.behavior-trees/session-id @st-mem)))))

(println "\n=== Recent Traces ===")
(def traces (debug/list-trace-summaries :limit 3))
(doseq [trace traces]
  (println "\nTrace:" (:trace-id trace))
  (println "  Command:" (:command-name trace))
  (println "  Status:" (:status trace))
  (println "  Duration:" (:duration-ms trace) "ms"))
