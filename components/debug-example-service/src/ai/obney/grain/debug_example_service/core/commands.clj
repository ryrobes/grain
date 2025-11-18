(ns ai.obney.grain.debug-example-service.core.commands
  "Command handlers for debug example service."
  (:require [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]
            [ai.obney.grain.event-store-v2.interface :as es]
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

(defn wizard-flow [context]
  "Execute the multi-step wizard flow with :view nodes."
  (let [build-context {:event-store (:event-store context)
                       :st-memory {:logs []}}
        {:keys [result trace]} (debug/run-with-tracing
                                 trees/wizard-flow-tree
                                 build-context
                                 :debug-example/wizard-flow
                                 {:streaming? true})]
    {:command-result/data {:result (if (= result :success) :success :failure)
                           :trace-id (:trace-id trace)
                           :message "Wizard flow completed"}}))

;;
;; Interactive Wizard Commands
;;

(defn start-interactive-wizard [context]
  "Start an interactive wizard session that blocks at each step."
  (require '[ai.obney.grain.flow-session-manager.interface :as fsm])

  (let [session-id (random-uuid)
        ;; NOTE: bt/build wraps st-memory in an atom, so pass a MAP not an atom!
        build-context {:event-store (:event-store context)
                       :st-memory {::trees/session-id session-id
                                  ::trees/flow-started-at (java.time.Instant/now)
                                  :logs []}}

        ;; Create and register session
        session ((resolve 'ai.obney.grain.flow-session-manager.interface/create-session)
                 session-id
                 :interactive-wizard
                 trees/interactive-wizard-tree
                 build-context)]

    ((resolve 'ai.obney.grain.flow-session-manager.interface/register-session!) session)

    ;; Start execution in background (reactive, not polling)
    ((resolve 'ai.obney.grain.flow-session-manager.interface/execute-flow-reactive)
     trees/interactive-wizard-tree
     build-context
     session-id
     :interactive-wizard
     {:streaming? true})

    {:command-result/data {:session-id (str session-id)
                           :message "Interactive wizard started"
                           :view-url (str "http://localhost:8080/flows/session/" session-id "/current-view")
                           :status-url (str "http://localhost:8080/flows/session/" session-id "/status")}}))

(defn wizard-continue [context]
  "Handle 'Get Started' button click - sets flag and redirects back to current-view."
  (let [session-id-str (get-in context [:command :session-id])
        session-id (when session-id-str (java.util.UUID/fromString session-id-str))

        ;; Get the session
        session (when session-id
                 ((resolve 'ai.obney.grain.flow-session-manager.interface/get-session) session-id))]

    (if session
      (do
        (println "🎬 wizard-continue called for session" session-id)

        ;; Set flag in session's st-memory
        (swap! (:st-memory session) assoc :event-received/wizard/started true)

        ;; Wake up the flow (signal on channel)
        ((resolve 'ai.obney.grain.flow-session-manager.interface/wake-session!) session-id)

        ;; Give the flow a moment to advance
        (Thread/sleep 150)

        ;; Also emit event for audit trail
        (let [event {:event/type :wizard/started
                     :event/id (random-uuid)
                     :event/timestamp (java.time.Instant/now)
                     :event/tags [[:session-id session-id]]}]
          (es/append (:event-store context) {:events [event]}))

        ;; Redirect back to current-view (SPA-like behavior!)
        {:status 303
         :headers {"Location" (str "/flows/session/" session-id "/current-view")}
         :body ""})

      ;; No session found
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>Session not found</h1></body></html>"})))

(defn wizard-submit-company [context]
  "Handle company info form submission."
  (let [session-id-str (get-in context [:command :session-id])
        session-id (when session-id-str (java.util.UUID/fromString session-id-str))
        session (when session-id
                 ((resolve 'ai.obney.grain.flow-session-manager.interface/get-session) session-id))

        company-data {:company-name (get-in context [:command :company-name])
                     :industry (get-in context [:command :industry])
                     :employee-count (get-in context [:command :employee-count])}]

    (if session
      (do
        (println "🎬 Company form submitted for session" session-id)

        ;; Store data in session memory
        (swap! (:st-memory session) assoc :wizard/company-data company-data)

        ;; Set flag to wake up flow
        (swap! (:st-memory session) assoc :event-received/wizard/company-submitted true)

        ;; Wake up the session
        ((resolve 'ai.obney.grain.flow-session-manager.interface/wake-session!) session-id)

        ;; Give flow time to advance
        (Thread/sleep 150)

        ;; Emit event for audit
        (es/append (:event-store context)
                   {:events [{:event/type :wizard/company-submitted
                             :event/id (random-uuid)
                             :event/timestamp (java.time.Instant/now)
                             :event/tags [[:session-id session-id]]
                             :body company-data}]})

        ;; Redirect back to current-view (SPA pattern!)
        {:status 303
         :headers {"Location" (str "/flows/session/" session-id "/current-view")}
         :body ""})

      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>Session not found</h1></body></html>"})))

(defn wizard-submit-billing [context]
  "Handle billing info form submission."
  (let [session-id-str (get-in context [:command :session-id])
        session-id (when session-id-str (java.util.UUID/fromString session-id-str))
        session (when session-id
                 ((resolve 'ai.obney.grain.flow-session-manager.interface/get-session) session-id))

        billing-data {:card-number (get-in context [:command :card-number])
                     :expiry (get-in context [:command :expiry])
                     :cvc (get-in context [:command :cvc])}]

    (if session
      (do
        (println "🎬 Billing form submitted for session" session-id)

        ;; Store data in session memory
        (swap! (:st-memory session) assoc :wizard/billing-data billing-data)

        ;; Set flag to wake up flow
        (swap! (:st-memory session) assoc :event-received/wizard/billing-submitted true)

        ;; Wake up the session
        ((resolve 'ai.obney.grain.flow-session-manager.interface/wake-session!) session-id)

        ;; Give flow time to advance
        (Thread/sleep 150)

        ;; Emit event for audit
        (es/append (:event-store context)
                   {:events [{:event/type :wizard/billing-submitted
                             :event/id (random-uuid)
                             :event/timestamp (java.time.Instant/now)
                             :event/tags [[:session-id session-id]]
                             :body billing-data}]})

        ;; Redirect back to current-view (SPA pattern!)
        {:status 303
         :headers {"Location" (str "/flows/session/" session-id "/current-view")}
         :body ""})

      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>Session not found</h1></body></html>"})))

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
                                                 [:items {:optional true} [:vector :string]]]}
   ;; View-based wizard flow
   :debug-example/wizard-flow {:handler-fn #'wizard-flow
                                :schema [:map]}
   ;; Interactive wizard commands
   :wizard/start-interactive {:handler-fn #'start-interactive-wizard
                              :schema [:map]}
   :wizard/continue {:handler-fn #'wizard-continue
                     :schema [:map [:session-id {:optional true} :string]]}
   :wizard/submit-company {:handler-fn #'wizard-submit-company
                           :schema [:map
                                    [:session-id :string]
                                    [:company-name :string]
                                    [:industry {:optional true} :string]
                                    [:employee-count {:optional true} :string]]}
   :wizard/submit-billing {:handler-fn #'wizard-submit-billing
                           :schema [:map
                                    [:session-id :string]
                                    [:card-number :string]
                                    [:expiry :string]
                                    [:cvc {:optional true} :string]]}})
