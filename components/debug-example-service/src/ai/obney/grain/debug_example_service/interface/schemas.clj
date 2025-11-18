(ns ai.obney.grain.debug-example-service.interface.schemas
  "Schema definitions for debug example service commands and events."
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defschemas commands
  {:debug-example/simple-task [:map]
   :debug-example/robot-mission [:map]
   :debug-example/make-decision [:map]
   :debug-example/parallel-tasks [:map]
   :debug-example/error-handling [:map]
   ;; AI-powered commands
   :debug-example/ai-question-answer [:map [:question :string]]
   :debug-example/ai-story-generator [:map
                                       [:genre {:optional true} :string]
                                       [:characters {:optional true} [:vector :string]]]
   :debug-example/ai-recipe-suggester [:map
                                        [:items {:optional true} [:vector :string]]]
   ;; View-based wizard flow
   :debug-example/wizard-flow [:map]
   ;; Interactive wizard commands
   :wizard/start-interactive [:map]
   :wizard/continue [:map [:session-id {:optional true} :string]]
   :wizard/submit-company [:map
                           [:session-id :string]
                           [:company-name :string]
                           [:industry {:optional true} :string]
                           [:employee-count {:optional true} :string]]
   :wizard/submit-billing [:map
                           [:session-id :string]
                           [:card-number :string]
                           [:expiry :string]
                           [:cvc {:optional true} :string]]})

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defschemas events
  {:task/completed [:map]
   :robot/item-collected [:map]
   :robot/mission-completed [:map]
   :decision/made [:map]
   :parallel-tasks/completed [:map]
   :operation/completed [:map]})

#_{:clojure-lsp/ignore [:clojure-lsp/unused-public-var]}
(defschemas queries
  {})
