(ns ai.obney.grain.debug-example-base.helpers
  "Helper functions for testing debug commands from the REPL."
  (:require [ai.obney.grain.command-processor.interface :as cp]
            [ai.obney.grain.time.interface :as time]
            [clojure.pprint :as pp]))

(defn start-app!
  "Start the Grain app and populate the global app atom.

   This is needed for the browser REPL to work. Call this once at startup.

   Usage: (h/start-app!)"
  []
  (require 'ai.obney.grain.debug-example-base.core)
  (let [start-fn (resolve 'ai.obney.grain.debug-example-base.core/start)
        app-atom-var (resolve 'ai.obney.grain.debug-example-base.core/app)]
    (if (and start-fn app-atom-var)
      (let [app-atom @app-atom-var
            started-app (start-fn)]
        (reset! app-atom started-app)
        (println "✅ App started and global atom populated!")
        (println "   Server running on port 8080")
        (println "   Debug UI: http://localhost:8082")
        started-app)
      (do
        (println "❌ Could not find start function or app atom!")
        nil))))

(defn inspect-app
  "Debug helper - inspect the app structure.

   Usage: (h/inspect-app app)"
  [app]
  (println "\n🔍 App structure:")
  (if (nil? app)
    (do
      (println "   ⚠️  app is nil!")
      (println "   Try: (def app (h/start-app!))"))
    (do
      (println "   Keys:" (keys app))
      (when-let [ctx (:ai.obney.grain.debug-example-base.core/context app)]
        (println "   Context keys:" (keys ctx))
        (println "   Available commands:" (keys (:command-registry ctx))))))
  (println))

(defn run-command
  "Execute a debug command by name. Convenience function for REPL testing.

   Usage:
   (run-command app :debug-example/simple-task)
   (run-command app :debug-example/robot-mission)"
  [app command-name]
  (let [context (:ai.obney.grain.debug-example-base.core/context app)]
    (when-not context
      (println "⚠️  WARNING: No context found in app!")
      (println "    App keys:" (keys app))
      (println "    Try: (h/inspect-app app)")
      (throw (ex-info "No context in app" {:app-keys (keys app)})))

    (when-not (get-in context [:command-registry command-name])
      (println "⚠️  WARNING: Command not found in registry!")
      (println "    Available commands:" (keys (:command-registry context)))
      (throw (ex-info "Command not registered"
                     {:command command-name
                      :available (keys (:command-registry context))})))

    (let [test-cmd {:command/name command-name
                    :command/id (random-uuid)
                    :command/timestamp (time/now)}
          result (cp/process-command (assoc context :command test-cmd))]
      (println "\n✅ Command executed:" command-name)
      (println "   Result:" (:command-result/data result))
      (println "   Check trace at http://localhost:8082\n")
      result)))

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
