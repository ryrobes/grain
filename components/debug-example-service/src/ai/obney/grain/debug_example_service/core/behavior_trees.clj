(ns ai.obney.grain.debug-example-service.core.behavior-trees
  "Example behavior trees for testing the debug UI."
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.behavior-tree-v2-dspy-extensions.interface :refer [dspy]]
            [ai.obney.grain.debug-example-service.core.signatures :as sigs]
            [ai.obney.grain.view-router.htmx :as htmx]
            [ai.obney.grain.flow-session-manager.helpers :as flow]))

;;
;; Example Behavior Trees
;;

(def simple-task-tree
  "A simple linear task sequence."
  [:sequence
   [:action {:id :initialize}
    (fn [{:keys [st-memory]}]
      (swap! st-memory assoc :status :initializing)
      bt/success)]

   [:action {:id :load-data}
    (fn [{:keys [st-memory]}]
      (Thread/sleep 200)
      (swap! st-memory assoc :data {:items [1 2 3 4 5]})
      bt/success)]

   [:action {:id :process-data}
    (fn [{:keys [st-memory]}]
      (Thread/sleep 300)
      (swap! st-memory update :data assoc :processed true)
      bt/success)]

   [:action {:id :save-results}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :task/completed
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:task-id aggregate-id]]
                   :result "success"}]
        (es/append event-store {:events [event]})
        (swap! st-memory assoc :task-id aggregate-id :status :completed)
        bt/success))]])

(def robot-task-tree
  "A complex robot task with conditions and fallbacks."
  [:sequence
   ;; Initialize robot
   [:action {:id :init-robot}
    (fn [{:keys [st-memory]}]
      (swap! st-memory merge {:energy 100
                               :position {:x 0 :y 0}
                               :items-collected 0
                               :logs []})
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Robot initialized"})
      bt/success)]

   ;; Move to first item
   [:action {:id :move-to-item-1}
    (fn [{:keys [st-memory]}]
      (swap! st-memory update :energy - 15)
      (swap! st-memory assoc :position {:x 10 :y 5})
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Moved to position (10, 5)"})
      bt/success)]

   ;; Check energy
   [:condition {:path [:energy]}
    (fn [{:keys [st-memory]}]
      (>= (:energy @st-memory) 20))]

   ;; Collect item 1
   [:action {:id :collect-item-1}
    (fn [{:keys [st-memory event-store]}]
      (swap! st-memory update :items-collected inc)
      (let [aggregate-id (random-uuid)
            event {:event/type :robot/item-collected
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:robot-mission aggregate-id]]
                   :item-id 1
                   :position (:position @st-memory)}]
        (es/append event-store {:events [event]}))
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Collected item 1"})
      bt/success)]

   ;; Move to second item
   [:action {:id :move-to-item-2}
    (fn [{:keys [st-memory]}]
      (swap! st-memory update :energy - 20)
      (swap! st-memory assoc :position {:x 25 :y 15})
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Moved to position (25, 15)"})
      bt/success)]

   ;; Check energy or recharge
   [:fallback
    [:condition {:path [:energy]}
     (fn [{:keys [st-memory]}]
       (>= (:energy @st-memory) 20))]

    [:action {:id :emergency-recharge}
     (fn [{:keys [st-memory]}]
       (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                           :message "Recharging energy..."})
       (swap! st-memory assoc :energy 100)
       (swap! st-memory update :recharge-count (fnil inc 0))
       bt/success)]]

   ;; Collect item 2
   [:action {:id :collect-item-2}
    (fn [{:keys [st-memory event-store]}]
      (swap! st-memory update :items-collected inc)
      (let [aggregate-id (random-uuid)
            event {:event/type :robot/item-collected
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:robot-mission aggregate-id]]
                   :item-id 2
                   :position (:position @st-memory)}]
        (es/append event-store {:events [event]}))
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Collected item 2"})
      bt/success)]

   ;; Move home
   [:action {:id :move-home}
    (fn [{:keys [st-memory]}]
      (swap! st-memory update :energy - 30)
      (swap! st-memory assoc :position {:x 0 :y 0})
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Returned home"})
      bt/success)]

   ;; Complete mission
   [:action {:id :complete-mission}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :robot/mission-completed
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:robot-mission aggregate-id]]
                   :items-collected (:items-collected @st-memory)
                   :energy-remaining (:energy @st-memory)}]
        (es/append event-store {:events [event]}))
      (swap! st-memory assoc :status :mission-complete)
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Mission complete!"})
      bt/success)]])

(def decision-tree
  "A tree with multiple fallback branches demonstrating decision-making."
  [:sequence
   [:action {:id :analyze-situation}
    (fn [{:keys [st-memory]}]
      (swap! st-memory merge {:temperature 75 :humidity 60 :battery 45 :logs []})
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Analyzing environmental conditions"})
      bt/success)]

   [:fallback
    ;; Option 1: Ideal conditions
    [:sequence
     [:condition {:path [:temperature]}
      (fn [{:keys [st-memory]}]
        (let [temp (:temperature @st-memory)]
          (and (>= temp 70) (<= temp 80))))]

     [:condition {:path [:humidity]}
      (fn [{:keys [st-memory]}]
        (< (:humidity @st-memory) 70))]

     [:condition {:path [:battery]}
      (fn [{:keys [st-memory]}]
        (>= (:battery @st-memory) 50))]

     [:action {:id :execute-optimal-path}
      (fn [{:keys [st-memory]}]
        (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                            :message "Conditions optimal - executing normal path"})
        (swap! st-memory assoc :path-chosen :optimal)
        bt/success)]]

    ;; Option 2: Battery low, charge first
    [:sequence
     [:condition {:path [:battery]}
      (fn [{:keys [st-memory]}]
        (< (:battery @st-memory) 50))]

     [:action {:id :charge-battery}
      (fn [{:keys [st-memory]}]
        (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                            :message "Battery low - charging first"})
        (swap! st-memory assoc :battery 100 :path-chosen :charge-first)
        (Thread/sleep 500)
        bt/success)]

     [:action {:id :execute-after-charge}
      (fn [{:keys [st-memory]}]
        (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                            :message "Executing after charge"})
        bt/success)]]

    ;; Option 3: Use alternative path
    [:action {:id :execute-alternative-path}
     (fn [{:keys [st-memory]}]
       (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                           :message "Using alternative path due to conditions"})
       (swap! st-memory assoc :path-chosen :alternative)
       bt/success)]]

   [:action {:id :record-decision}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :decision/made
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:decision aggregate-id]]
                   :path (:path-chosen @st-memory)
                   :conditions {:temperature (:temperature @st-memory)
                                :humidity (:humidity @st-memory)
                                :battery (:battery @st-memory)}}]
        (es/append event-store {:events [event]})
        bt/success))]])

(def parallel-task-tree
  "Demonstrates parallel execution."
  [:sequence
   [:action {:id :init-parallel-tasks}
    (fn [{:keys [st-memory]}]
      (swap! st-memory assoc :parallel-results [] :logs [])
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Starting parallel tasks"})
      bt/success)]

   [:parallel
    [:action {:id :task-a}
     (fn [{:keys [st-memory]}]
       (Thread/sleep 200)
       (swap! st-memory update :parallel-results conj :task-a-complete)
       (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                           :message "Task A completed"})
       bt/success)]

    [:action {:id :task-b}
     (fn [{:keys [st-memory]}]
       (Thread/sleep 150)
       (swap! st-memory update :parallel-results conj :task-b-complete)
       (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                           :message "Task B completed"})
       bt/success)]

    [:action {:id :task-c}
     (fn [{:keys [st-memory]}]
       (Thread/sleep 100)
       (swap! st-memory update :parallel-results conj :task-c-complete)
       (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                           :message "Task C completed"})
       bt/success)]]

   [:action {:id :aggregate-results}
    (fn [{:keys [st-memory event-store]}]
      (let [results (:parallel-results @st-memory)
            aggregate-id (random-uuid)
            event {:event/type :parallel-tasks/completed
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:parallel-tasks aggregate-id]]
                   :task-count (count results)}]
        (es/append event-store {:events [event]})
        (swap! st-memory assoc :task-count (count results))
        (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                            :message (str "All parallel tasks completed: " (count results))})
        bt/success))]])

(def error-handling-tree
  "Demonstrates error handling and recovery."
  [:sequence
   [:action {:id :risky-operation-1}
    (fn [{:keys [st-memory]}]
      (swap! st-memory assoc :attempts 0 :logs [])
      (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                          :message "Attempting risky operation..."})
      bt/success)]

   [:fallback
    [:action {:id :main-operation}
     (fn [{:keys [st-memory]}]
       (swap! st-memory update :attempts inc)
       (if (< (rand) 0.6)
         (do
           (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                               :message "Main operation failed!"})
           bt/failure)
         (do
           (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                               :message "Main operation succeeded!"})
           bt/success)))]

    [:sequence
     [:action {:id :log-failure}
      (fn [{:keys [st-memory]}]
        (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                            :message "Main operation failed, trying backup..."})
        bt/success)]

     [:action {:id :backup-operation}
      (fn [{:keys [st-memory]}]
        (Thread/sleep 100)
        (swap! st-memory update :logs conj {:timestamp (java.time.Instant/now)
                                            :message "Backup operation succeeded!"})
        bt/success)]]]

   [:action {:id :finalize}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :operation/completed
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:operation aggregate-id]]
                   :attempts (:attempts @st-memory)}]
        (es/append event-store {:events [event]})
        bt/success))]])

;;
;; AI-Powered Behavior Trees (DSPy Examples)
;;

(def ai-question-answer-tree
  "Answer a question using AI with chain-of-thought reasoning."
  [:sequence
   ;; Validate input
   [:condition {:path [:question]}
    (fn [{:keys [st-memory]}]
      (not (clojure.string/blank? (:question @st-memory))))]

   ;; Call AI
   [:action {:id :ai-answer
             :signature #'sigs/QuestionAnswerer
             :operation :chain-of-thought}
    dspy]

   ;; Log result
   [:action {:id :log-answer}
    (fn [{:keys [st-memory]}]
      (swap! st-memory update :logs (fnil conj [])
             {:timestamp (java.time.Instant/now)
              :message (str "AI answered: " (subs (:answer @st-memory) 0 (min 50 (count (:answer @st-memory)))) "...")})
      bt/success)]

   ;; Persist to event store
   [:action {:id :save-qa}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :ai/question-answered
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:ai-qa aggregate-id]]
                   :question (:question @st-memory)
                   :answer (:answer @st-memory)
                   :reasoning (:reasoning @st-memory)}]
        (es/append event-store {:events [event]})
        bt/success))]])

(def ai-story-generator-tree
  "Generate a creative story using AI."
  [:sequence
   ;; Validate inputs
   [:condition {:path [:genre]}
    (fn [{:keys [st-memory]}]
      (not (clojure.string/blank? (:genre @st-memory))))]

   [:condition {:path [:characters]}
    (fn [{:keys [st-memory]}]
      (seq (:characters @st-memory)))]

   ;; Generate story with AI
   [:action {:id :generate-story
             :signature #'sigs/StoryGenerator
             :operation :chain-of-thought}
    dspy]

   ;; Log result
   [:action {:id :log-story}
    (fn [{:keys [st-memory]}]
      (swap! st-memory update :logs (fnil conj [])
             {:timestamp (java.time.Instant/now)
              :message (str "Story generated (" (count (:story @st-memory)) " chars)")})
      bt/success)]

   ;; Save to event store
   [:action {:id :save-story}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :ai/story-generated
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:story aggregate-id]]
                   :genre (:genre @st-memory)
                   :characters (:characters @st-memory)
                   :story (:story @st-memory)}]
        (es/append event-store {:events [event]})
        bt/success))]])

(def ai-recipe-suggester-tree
  "Suggest a recipe based on available items."
  [:sequence
   ;; Validate items
   [:condition {:path [:available_items]}
    (fn [{:keys [st-memory]}]
      (seq (:available_items @st-memory)))]

   ;; Call AI
   [:action {:id :suggest-recipe
             :signature #'sigs/RecipeSuggester
             :operation :chain-of-thought}
    dspy]

   ;; Validate response
   [:condition {:path [:recipe_title]}
    (fn [{:keys [st-memory]}]
      (not (clojure.string/blank? (:recipe_title @st-memory))))]

   ;; Log result
   [:action {:id :log-recipe}
    (fn [{:keys [st-memory]}]
      (swap! st-memory update :logs (fnil conj [])
             {:timestamp (java.time.Instant/now)
              :message (str "Recipe suggested: " (:recipe_title @st-memory))})
      bt/success)]

   ;; Save recipe
   [:action {:id :save-recipe}
    (fn [{:keys [st-memory event-store]}]
      (let [aggregate-id (random-uuid)
            event {:event/type :ai/recipe-suggested
                   :event/id (random-uuid)
                   :event/timestamp (java.time.Instant/now)
                   :event/tags [[:recipe aggregate-id]]
                   :title (:recipe_title @st-memory)
                   :ingredients (:recipe_ingredients @st-memory)
                   :instructions (:recipe_instructions @st-memory)}]
        (es/append event-store {:events [event]})
        bt/success))]])

;;
;; View-Based Wizard Flow Example
;;

;; View functions for wizard steps
(defn welcome-view [{:keys [st-memory]}]
  [:div.wizard-step {:style {:padding "2rem"
                             :background "#ffffff"
                             :border-radius "8px"}}
   [:h1 {:style {:font-size "2rem"
                 :font-weight "600"
                 :margin "0 0 1rem 0"
                 :color "#111827"}}
    "🧙 Account Setup Wizard"]
   [:p {:style {:margin "0 0 1rem 0"
                :color "#374151"
                :line-height "1.6"}}
    "Welcome! This wizard will guide you through setting up your account."]
   [:p {:style {:margin "0 0 1.5rem 0"
                :color "#6b7280"
                :font-size "0.875rem"
                :line-height "1.6"}}
    "This demo showcases Grain's view integration - each step is a :view node in the behavior tree."]
   [:div.progress-bar {:style {:background "#e5e7eb"
                               :height "8px"
                               :border-radius "4px"
                               :margin "1.5rem 0"
                               :overflow "hidden"}}
    [:div.fill {:style {:background "linear-gradient(90deg, #10b981, #059669)"
                        :height "100%"
                        :width "10%"
                        :border-radius "4px"
                        :transition "width 0.3s ease"}}]]
   (htmx/form {:action :wizard/start}
     [:button.primary {:style {:padding "0.625rem 1.25rem"
                               :border "none"
                               :border-radius "6px"
                               :cursor "pointer"
                               :font-size "1rem"
                               :font-weight "500"
                               :background "#10b981"
                               :color "white"
                               :transition "all 0.2s"}}
      "Get Started →"])])

(defn company-info-view [{:keys [st-memory]}]
  (let [errors (get @st-memory ::validation-errors)
        data (get @st-memory ::company-data {})]
    [:div.wizard-step
     [:h2 "Step 1: Company Information"]
     [:div.progress-bar
      [:div.fill {:style "width: 35%"}]]

     (when errors
       [:div.errors
        [:p.error "Please correct the following errors:"]
        (for [err errors]
          [:p.error "• " err])])

     (htmx/form {:action :wizard/submit-company}
       [:div
        [:label "Company Name *"]
        [:input {:name "company-name"
                 :value (:company-name data)
                 :required true
                 :placeholder "Acme Corp"}]]

       [:div
        [:label "Industry"]
        [:input {:name "industry"
                 :value (:industry data)
                 :placeholder "Technology, Healthcare, etc."}]]

       [:div
        [:label "Employee Count"]
        [:select {:name "employee-count"}
         [:option "1-10"]
         [:option "11-50"]
         [:option "51-200"]
         [:option "201+"]]]

       [:div {:style "margin-top: 1rem;"}
        (htmx/button {:action :wizard/back-to-welcome
                      :class "secondary"}
                     "← Back")
        [:button.primary {:type "submit"} "Next →"]])]))

(defn billing-view [{:keys [st-memory]}]
  (let [company-name (get @st-memory ::company-name "your company")]
    [:div.wizard-step
     [:h2 "Step 2: Billing Information"]
     [:div.progress-bar
      [:div.fill {:style "width: 65%"}]]

     [:div {:style "background: #f5f5f5; padding: 1rem; border-radius: 4px; margin: 1rem 0;"}
      [:h3 "Standard Plan for " company-name]
      [:p "✓ Unlimited users"]
      [:p "✓ 24/7 support"]
      [:p "✓ Advanced analytics"]
      [:p {:style "font-size: 1.5rem; font-weight: bold; margin-top: 1rem;"} "$99/month"]]

     (htmx/form {:action :wizard/submit-billing}
       [:div
        [:label "Card Number *"]
        [:input {:name "card-number"
                 :required true
                 :placeholder "4242 4242 4242 4242"}]]

       [:div {:style "display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;"}
        [:div
         [:label "Expiry *"]
         [:input {:name "expiry"
                  :required true
                  :placeholder "MM/YY"}]]
        [:div
         [:label "CVC *"]
         [:input {:name "cvc"
                  :required true
                  :placeholder "123"}]]]

       [:div {:style "margin-top: 1rem;"}
        (htmx/button {:action :wizard/back-to-company
                      :class "secondary"}
                     "← Back")
        [:button.primary {:type "submit"} "Complete Setup →"]])]))

(defn processing-view [{:keys [st-memory]}]
  (let [progress (get @st-memory ::progress 80)]
    [:div.wizard-step
     [:h2 "⚙️ Setting Up Your Account..."]
     [:div.progress-bar
      [:div.fill {:style (str "width: " progress "%")}]]

     [:div {:style "margin: 2rem 0;"}
      [:p "Please wait while we:"]
      [:ul {:style "list-style: none; padding-left: 0;"}
       [:li "✓ Create your account"]
       [:li "✓ Provision resources"]
       [:li "✓ Send welcome email"]]]

     [:div {:id "status"
            :style "font-style: italic; color: #666;"}
      "Initializing..."]]))

(defn complete-view [{:keys [st-memory]}]
  (let [company-name (get @st-memory ::company-name "")
        account-id (get @st-memory ::account-id)]
    [:div.wizard-step.complete
     [:h1 "✅ All Set!"]
     [:p "Welcome to the platform, " [:strong company-name] "!"]
     [:div {:style "background: #e8f5e9; padding: 1rem; border-radius: 4px; margin: 1rem 0;"}
      [:p "Your account ID: " [:code account-id]]]
     [:div {:style "margin-top: 2rem;"}
      [:a.button.primary {:href "/dashboard"} "Go to Dashboard"]]]))

;; The wizard behavior tree with :view nodes
(def wizard-flow-tree
  "Multi-step wizard demonstrating :view nodes and HTMX integration."
  [:sequence

   ;; Step 1: Welcome Screen
   [:view {:id :welcome
           :route "/wizard/welcome"}
    welcome-view]

   ;; Wait for user to click "Get Started"
   [:action {:id :await-start}
    (fn [{:keys [st-memory]}]
      ;; In a real app, this would wait for an event
      ;; For now, we just simulate immediate continuation
      (swap! st-memory assoc ::wizard-started true)
      bt/success)]

   ;; Step 2: Company Info (with validation loop)
   [:sequence
    [:view {:id :company-info
            :route "/wizard/company"}
     company-info-view]

    [:action {:id :await-company-data}
     (fn [{:keys [st-memory]}]
       ;; Simulate waiting for form submission
       (swap! st-memory assoc ::company-data {:company-name "Demo Corp"
                                                :industry "Technology"
                                                :employee-count "11-50"})
       bt/success)]

    [:action {:id :validate-company}
     (fn [{:keys [st-memory]}]
       (let [data (get @st-memory ::company-data)]
         (if (and (:company-name data)
                  (not (clojure.string/blank? (:company-name data))))
           (do
             (swap! st-memory assoc ::company-name (:company-name data))
             (swap! st-memory dissoc ::validation-errors)
             bt/success)
           (do
             (swap! st-memory assoc ::validation-errors ["Company name is required"])
             bt/failure))))]

    [:action {:id :save-company-data}
     (fn [{:keys [st-memory event-store]}]
       (let [aggregate-id (random-uuid)
             data (get @st-memory ::company-data)
             event {:event/type :wizard/company-info-submitted
                    :event/id (random-uuid)
                    :event/timestamp (java.time.Instant/now)
                    :event/tags [[:wizard-session aggregate-id]]
                    :company-data data}]
         (es/append event-store {:events [event]})
         bt/success))]]

   ;; Step 3: Billing Info
   [:sequence
    [:view {:id :billing
            :route "/wizard/billing"}
     billing-view]

    [:action {:id :await-billing-data}
     (fn [{:keys [st-memory]}]
       ;; Simulate form submission
       (Thread/sleep 100)
       (swap! st-memory assoc ::billing-data {:card-number "****4242"
                                               :expiry "12/25"})
       bt/success)]

    [:action {:id :validate-billing}
     (fn [{:keys [st-memory]}]
       ;; Simple validation
       (let [data (get @st-memory ::billing-data)]
         (if (and (:card-number data) (:expiry data))
           bt/success
           bt/failure)))]]

   ;; Step 4: Processing (parallel background tasks with view)
   [:parallel
    [:view {:id :processing
            :route "/wizard/processing"}
     processing-view]

    [:sequence
     [:action {:id :create-account}
      (fn [{:keys [st-memory]}]
        (swap! st-memory assoc ::progress 85)
        (Thread/sleep 300)
        (swap! st-memory assoc ::account-id (str "ACC-" (random-uuid)))
        (swap! st-memory update :logs (fnil conj [])
               {:timestamp (java.time.Instant/now)
                :message "Account created"})
        bt/success)]

     [:action {:id :provision-resources}
      (fn [{:keys [st-memory]}]
        (swap! st-memory assoc ::progress 92)
        (Thread/sleep 200)
        (swap! st-memory update :logs (fnil conj [])
               {:timestamp (java.time.Instant/now)
                :message "Resources provisioned"})
        bt/success)]

     [:action {:id :send-welcome-email}
      (fn [{:keys [st-memory]}]
        (swap! st-memory assoc ::progress 100)
        (Thread/sleep 150)
        (swap! st-memory update :logs (fnil conj [])
               {:timestamp (java.time.Instant/now)
                :message "Welcome email sent"})
        bt/success)]]]

   ;; Step 5: Completion
   [:sequence
    [:view {:id :complete
            :route "/wizard/complete"}
     complete-view]

    [:action {:id :finalize-wizard}
     (fn [{:keys [st-memory event-store]}]
       (let [aggregate-id (get @st-memory ::account-id)
             event {:event/type :wizard/completed
                    :event/id (random-uuid)
                    :event/timestamp (java.time.Instant/now)
                    :event/tags [[:account-id aggregate-id]]
                    :company-name (get @st-memory ::company-name)
                    :account-id aggregate-id}]
         (es/append event-store {:events [event]})
         bt/success))]]])

;;
;; Interactive View Functions (Forms with session-id)
;;

(defn interactive-welcome-view [{:keys [st-memory]}]
  (let [session-id (::session-id @st-memory)]
    [:div.wizard-step {:style {:padding "2rem" :background "#ffffff" :border-radius "8px"}}
     [:h1 {:style {:font-size "2rem" :font-weight "600" :margin "0 0 1rem 0" :color "#111827"}}
      "🧙 Interactive Wizard"]
     [:p {:style {:margin "0 0 1rem 0" :color "#374151"}}
      "Welcome! This wizard is LIVE and interactive."]
     [:p {:style {:margin "0 0 1.5rem 0" :color "#6b7280" :font-size "0.875rem"}}
      "Session ID: " [:code {:style {:background "#f3f4f6" :padding "0.125rem 0.375rem"}} (str session-id)]]
     [:div.progress-bar {:style {:background "#e5e7eb" :height "8px" :margin "1.5rem 0"}}
      [:div.fill {:style {:background "#10b981" :height "100%" :width "10%"}}]]
     [:form {:method "post" :action "/wizard/continue"}
      [:input {:type "hidden" :name "session-id" :value (str session-id)}]
      [:button.primary {:type "submit"
                        :style {:padding "0.625rem 1.25rem" :border "none" :border-radius "6px"
                                :background "#10b981" :color "white" :font-weight "500" :cursor "pointer"}}
       "Get Started →"]]]))

(defn interactive-company-view [{:keys [st-memory]}]
  (let [session-id (::session-id @st-memory)
        errors (::validation-errors @st-memory)]
    [:div.wizard-step {:style {:padding "2rem"}}
     [:h2 {:style {:font-size "1.5rem" :margin "0 0 1rem 0"}} "Company Information"]
     [:div.progress-bar {:style {:background "#e5e7eb" :height "8px" :margin "1.5rem 0"}}
      [:div.fill {:style {:background "#10b981" :height "100%" :width "35%"}}]]

     (when errors
       [:div.errors {:style {:background "#fef2f2" :color "#991b1b" :padding "1rem" :margin "1rem 0"}}
        (for [err errors]
          [:p.error {:key err :style {:margin "0.25rem 0"}} err])])

     [:form {:method "post" :action "/wizard/submit-company"}
      [:input {:type "hidden" :name "session-id" :value (str session-id)}]
      [:div
       [:label {:style {:display "block" :margin "1rem 0 0.375rem" :font-weight "500"}} "Company Name *"]
       [:input {:name "company-name" :required true
                :style {:padding "0.625rem" :border "1px solid #d1d5db" :border-radius "6px" :width "100%"}}]]
      [:div
       [:label {:style {:display "block" :margin "1rem 0 0.375rem" :font-weight "500"}} "Industry"]
       [:input {:name "industry"
                :style {:padding "0.625rem" :border "1px solid #d1d5db" :border-radius "6px" :width "100%"}}]]
      [:div
       [:label {:style {:display "block" :margin "1rem 0 0.375rem" :font-weight "500"}} "Employee Count"]
       [:select {:name "employee-count"
                 :style {:padding "0.625rem" :border "1px solid #d1d5db" :border-radius "6px" :width "100%"}}
        [:option "1-10"]
        [:option "11-50"]
        [:option "51-200"]
        [:option "201+"]]]
      [:button.primary {:type "submit"
                        :style {:padding "0.625rem 1.25rem" :border "none" :border-radius "6px"
                                :background "#10b981" :color "white" :margin-top "1rem"}}
       "Next →"]]]))

(defn interactive-billing-view [{:keys [st-memory]}]
  (let [session-id (::session-id @st-memory)
        company-name (::company-name @st-memory "your company")]
    [:div.wizard-step {:style {:padding "2rem"}}
     [:h2 {:style {:font-size "1.5rem" :margin "0 0 1rem 0"}} "Billing Information"]
     [:div.progress-bar {:style {:background "#e5e7eb" :height "8px" :margin "1.5rem 0"}}
      [:div.fill {:style {:background "#10b981" :height "100%" :width "65%"}}]]
     [:div {:style {:background "#f3f4f6" :padding "1rem" :border-radius "6px" :margin "1rem 0"}}
      [:h3 "Standard Plan for " company-name]
      [:p "✓ Unlimited users • ✓ 24/7 support"]
      [:p {:style {:font-size "1.5rem" :font-weight "600" :margin-top "0.5rem"}} "$99/month"]]
     [:form {:method "post" :action "/wizard/submit-billing"}
      [:input {:type "hidden" :name "session-id" :value (str session-id)}]
      [:div
       [:label {:style {:display "block" :margin "1rem 0 0.375rem" :font-weight "500"}} "Card Number *"]
       [:input {:name "card-number" :required true :placeholder "4242 4242 4242 4242"
                :style {:padding "0.625rem" :border "1px solid #d1d5db" :border-radius "6px" :width "100%"}}]]
      [:div {:style {:display "grid" :grid-template-columns "1fr 1fr" :gap "1rem"}}
       [:div
        [:label {:style {:display "block" :margin "1rem 0 0.375rem" :font-weight "500"}} "Expiry *"]
        [:input {:name "expiry" :required true :placeholder "MM/YY"
                 :style {:padding "0.625rem" :border "1px solid #d1d5db" :border-radius "6px" :width "100%"}}]]
       [:div
        [:label {:style {:display "block" :margin "1rem 0 0.375rem" :font-weight "500"}} "CVC"]
        [:input {:name "cvc" :placeholder "123"
                 :style {:padding "0.625rem" :border "1px solid #d1d5db" :border-radius "6px" :width "100%"}}]]]
      [:button.primary {:type "submit"
                        :style {:padding "0.625rem 1.25rem" :border "none" :border-radius "6px"
                                :background "#10b981" :color "white" :margin-top "1rem"}}
       "Complete Setup →"]]]))

(defn interactive-complete-view [{:keys [st-memory]}]
  (let [company-name (::company-name @st-memory "")
        account-id (::account-id @st-memory)]
    [:div.wizard-step.complete {:style {:padding "2rem" :text-align "center"}}
     [:h1 {:style {:font-size "2rem" :margin "0 0 1rem 0"}} "✅ All Set!"]
     [:p "Welcome to the platform, " [:strong company-name] "!"]
     [:div {:style {:background "#e8f5e9" :padding "1rem" :border-radius "6px" :margin "1rem auto" :max-width "400px"}}
      [:p "Account ID: " [:code {:style {:background "white" :padding "0.25rem 0.5rem"}} account-id]]]
     [:p {:style {:color "#6b7280" :margin-top "2rem"}}
      "This interactive session has completed successfully."]]))

;; Interactive wizard tree definition (using :view-action nodes)
(def interactive-wizard-tree
  "Interactive wizard with :view-action nodes that properly block.

  :view-action nodes execute the view (renders once), then the action (blocks).
  This ensures views render before blocking, and returns :running correctly."
  [:sequence
   [:action {:id :init-session}
    (fn [{:keys [st-memory]}]
      (swap! st-memory merge
             {::session-id (or (::session-id @st-memory) (random-uuid))
              ::flow-started-at (java.time.Instant/now)
              :logs []})
      (println "🎬 Session initialized")
      bt/success)]

   ;; Step 1: Welcome screen (blocks until button clicked)
   [:view-action
    [:view {:id :welcome} interactive-welcome-view]
    [:action {:id :await-start} (flow/await-event-fn :wizard/started)]]

   ;; Step 2: Company info (blocks until form submitted AND validated)
   [:view-action
    [:view {:id :company-info} interactive-company-view]
    [:sequence
     [:action {:id :await-company} (flow/await-event-fn :wizard/company-submitted)]
     [:action {:id :validate-company}
      (fn [{:keys [st-memory]}]
        (let [data (get @st-memory :wizard/company-data)]
          (if (and data (:company-name data))
            (do
              (swap! st-memory assoc ::company-name (:company-name data))
              (swap! st-memory dissoc ::validation-errors)
              (println "✅ Company data validated")
              bt/success)
            (do
              (swap! st-memory assoc ::validation-errors ["Company name required"])
              (println "❌ Validation failed")
              bt/failure))))]
     [:action {:id :save-company}
      (fn [{:keys [st-memory event-store]}]
        (let [data (get @st-memory :wizard/company-data)
              session-id (::session-id @st-memory)]
          (es/append event-store
                     {:events [{:event/type :wizard/company-saved
                               :event/id (random-uuid)
                               :event/timestamp (java.time.Instant/now)
                               :event/tags [[:session-id session-id]]
                               :body data}]})
          (println "💾 Company data saved")
          bt/success))]]]

   ;; Step 3: Billing (blocks until billing submitted AND validated)
   [:view-action
    [:view {:id :billing} interactive-billing-view]
    [:sequence
     [:action {:id :await-billing} (flow/await-event-fn :wizard/billing-submitted)]
     [:action {:id :validate-billing}
      (fn [{:keys [st-memory]}]
        (let [data (get @st-memory :wizard/billing-data)]
          (if (and data (:card-number data) (:expiry data))
            (do
              (println "✅ Billing validated")
              bt/success)
            (do
              (println "❌ Billing validation failed")
              bt/failure))))]]]

   ;; Step 4: Processing (view + background tasks in sequence)
   [:sequence
    [:view {:id :processing} processing-view]
    [:action {:id :create-account}
     (fn [{:keys [st-memory]}]
       (swap! st-memory assoc ::progress 85)
       (Thread/sleep 300)
       (swap! st-memory assoc ::account-id (str "ACC-" (random-uuid)))
       (println "👤 Account created")
       bt/success)]
    [:action {:id :provision}
     (fn [{:keys [st-memory]}]
       (swap! st-memory assoc ::progress 100)
       (Thread/sleep 200)
       (println "🔧 Resources provisioned")
       bt/success)]]

   ;; Step 5: Complete
   [:view {:id :complete} interactive-complete-view]

   [:action {:id :finalize}
    (fn [{:keys [st-memory event-store]}]
      (let [session-id (::session-id @st-memory)]
        (es/append event-store
                   {:events [{:event/type :wizard/completed
                             :event/id (random-uuid)
                             :event/timestamp (java.time.Instant/now)
                             :event/tags [[:session-id session-id]]}]})
        (println "🎉 Wizard completed!")
        bt/success))]])

