(ns ai.obney.grain.debug-example-service.core.behavior-trees
  "Example behavior trees for testing the debug UI."
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.event-store-v2.interface :as es]))

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
