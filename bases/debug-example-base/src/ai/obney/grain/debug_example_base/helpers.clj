(ns ai.obney.grain.debug-example-base.helpers
  "Helper functions for testing debug commands from the REPL."
  (:require [ai.obney.grain.command-processor.interface :as cp]
            [ai.obney.grain.time.interface :as time]))

(defn run-command
  "Execute a debug command by name. Convenience function for REPL testing.

   Usage:
   (run-command app :debug-example/simple-task)
   (run-command app :debug-example/robot-mission)"
  [app command-name]
  (let [context (:ai.obney.grain.debug-example-base.core/context app)
        test-cmd {:command/name command-name
                  :command/id (random-uuid)
                  :command/timestamp (time/now)}
        result (cp/process-command (assoc context :command test-cmd))]
    (println "\n✅ Command executed:" command-name)
    (println "   Result:" (:command-result/data result))
    (println "   Check trace at http://localhost:8082\n")
    result))

(defn run-all
  "Run all debug commands in sequence."
  [app]
  (doseq [cmd [:debug-example/simple-task
               :debug-example/robot-mission
               :debug-example/make-decision
               :debug-example/parallel-tasks
               :debug-example/error-handling]]
    (run-command app cmd)
    (Thread/sleep 500)))
