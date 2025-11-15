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

(defn run-ai
  "Run a specific AI command with parameters.

   Usage:
   (run-ai app :ai-question-answer {:question \"What is the meaning of life?\"})
   (run-ai app :ai-story-generator {:genre \"sci-fi\" :characters [\"Zara\" \"Commander Rex\"]})
   (run-ai app :ai-recipe-suggester {:items [\"chicken\" \"rice\" \"curry powder\"]})"
  [app command-type params]
  (let [context (:ai.obney.grain.debug-example-base.core/context app)
        command-name (keyword "debug-example" (name command-type))
        test-cmd (merge {:command/name command-name
                         :command/id (random-uuid)
                         :command/timestamp (time/now)}
                        params)
        result (cp/process-command (assoc context :command test-cmd))]
    (println "\n✅ AI Command executed:" command-name)
    (println "   Result:" (:command-result/data result))
    (println "   Check trace at http://localhost:8082\n")
    result))

(defn demo-ai
  "Run all AI commands with interesting examples."
  [app]
  (println "\n🤖 Running AI Demo Commands...\n")

  (println "1️⃣  Question & Answer")
  (run-ai app :ai-question-answer {:question "Explain quantum entanglement in simple terms"})
  (Thread/sleep 1000)

  (println "2️⃣  Story Generator")
  (run-ai app :ai-story-generator {:genre "cyberpunk"
                                    :characters ["Nova" "Cipher" "The Oracle"]})
  (Thread/sleep 1000)

  (println "3️⃣  Recipe Suggester")
  (run-ai app :ai-recipe-suggester {:items ["chicken" "coconut milk" "curry paste" "rice" "lime"]})

  (println "\n✨ Demo complete! Check http://localhost:8082 for traces\n"))
