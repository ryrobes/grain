(ns ai.obney.grain.debug-example-service.core.commands
  "Command handlers for debug example service."
  (:require [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]
            [ai.obney.grain.debug-example-service.core.behavior-trees :as trees]))

(defn simple-task [context]
  "Execute a simple sequential task."
  (let [build-context {:event-store (:event-store context)
                       :st-memory {}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/simple-task-tree
                                 build-context
                                 :debug-example/simple-task
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Simple task completed"}}))

(defn robot-mission [context]
  "Execute a robot collection mission with energy management."
  (let [build-context {:event-store (:event-store context)
                       :st-memory {}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/robot-task-tree
                                 build-context
                                 :debug-example/robot-mission
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Robot mission completed"}}))

(defn make-decision [context]
  "Execute a decision tree with multiple fallback paths."
  (let [build-context {:event-store (:event-store context)
                       :st-memory {}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/decision-tree
                                 build-context
                                 :debug-example/make-decision
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Decision completed"}}))

(defn parallel-tasks [context]
  "Execute parallel tasks demonstration."
  (let [build-context {:event-store (:event-store context)
                       :st-memory {}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/parallel-task-tree
                                 build-context
                                 :debug-example/parallel-tasks
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Parallel tasks completed"}}))

(defn error-handling [context]
  "Execute error handling demonstration with fallbacks."
  (let [build-context {:event-store (:event-store context)
                       :st-memory {}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/error-handling-tree
                                 build-context
                                 :debug-example/error-handling
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Error handling completed"}}))

(def commands
  {:debug-example/simple-task {:handler-fn #'simple-task
                                :schema [:map]}
   :debug-example/robot-mission {:handler-fn #'robot-mission
                                  :schema [:map]}
   :debug-example/make-decision {:handler-fn #'make-decision
                                  :schema [:map]}
   :debug-example/parallel-tasks {:handler-fn #'parallel-tasks
                                   :schema [:map]}
   :debug-example/error-handling {:handler-fn #'error-handling
                                   :schema [:map]}})
