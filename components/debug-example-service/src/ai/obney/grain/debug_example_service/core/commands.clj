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

(defn ai-question-answer [context]
  "Answer a question using AI with chain-of-thought reasoning."
  (let [question (get-in context [:command :question])
        build-context {:event-store (:event-store context)
                       :st-memory {:question question :logs []}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/ai-question-answer-tree
                                 build-context
                                 :debug-example/ai-question-answer
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :answer (get-in trace [:execution-events])
                           :message "AI question answered"}}))

(defn ai-story-generator [context]
  "Generate a creative story using AI."
  (let [genre (get-in context [:command :genre] "fantasy")
        characters (get-in context [:command :characters] ["Alice" "Bob"])
        build-context {:event-store (:event-store context)
                       :st-memory {:genre genre
                                   :characters characters
                                   :logs []}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/ai-story-generator-tree
                                 build-context
                                 :debug-example/ai-story-generator
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Story generated"}}))

(defn ai-recipe-suggester [context]
  "Suggest a recipe based on available items."
  (let [items (get-in context [:command :items] ["chicken" "rice" "onions" "garlic"])
        build-context {:event-store (:event-store context)
                       :st-memory {:available_items items
                                   :logs []}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/ai-recipe-suggester-tree
                                 build-context
                                 :debug-example/ai-recipe-suggester
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Recipe suggested"}}))

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
                                   :schema [:map]}
   ;; AI-powered commands
   :debug-example/ai-question-answer {:handler-fn #'ai-question-answer
                                       :schema [:map [:question :string]]}
   :debug-example/ai-story-generator {:handler-fn #'ai-story-generator
                                       :schema [:map
                                                [:genre {:optional true} :string]
                                                [:characters {:optional true} [:vector :string]]]}
   :debug-example/ai-recipe-suggester {:handler-fn #'ai-recipe-suggester
                                        :schema [:map
                                                 [:items {:optional true} [:vector :string]]]}})
