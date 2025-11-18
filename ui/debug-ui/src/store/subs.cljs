(ns store.subs
  "Re-frame subscriptions for debug UI"
  (:require [re-frame.core :as rf]))

;;
;; Helper Functions
;;

(defn find-node-in-tree
  "Recursively find a node in the tree by node-id"
  [node target-id]
  (cond
    (= (:node-id node) target-id)
    node

    (:children node)
    (some #(find-node-in-tree % target-id) (:children node))

    :else
    nil))

;;
;; Simple Subscriptions
;;

(rf/reg-sub
 ::traces
 (fn [db _]
   (:traces db)))

(rf/reg-sub
 ::current-trace
 (fn [db _]
   (:current-trace db)))

(rf/reg-sub
 ::loading
 (fn [db _]
   (:loading db)))

(rf/reg-sub
 ::error
 (fn [db _]
   (:error db)))

(rf/reg-sub
 ::api-base
 (fn [db _]
   (:api-base db)))

(rf/reg-sub
 ::selected-node
 (fn [db _]
   (:selected-node db)))

(rf/reg-sub
 ::sse-connected
 (fn [db _]
   (:sse-connected db)))

(rf/reg-sub
 ::sse-reconnecting
 (fn [db _]
   (:sse-reconnecting db)))

(rf/reg-sub
 ::view-mode
 (fn [db _]
   (or (:view-mode db) :hybrid)))

(rf/reg-sub
 ::expanded-groups
 (fn [db _]
   (:expanded-groups db)))

(rf/reg-sub
 ::terminal-visible
 (fn [db _]
   (let [visible (:terminal-visible db)]
     (js/console.log "🎹 Subscription read - terminal-visible:" visible)
     visible)))

(rf/reg-sub
 ::view-preview-visible
 (fn [db _]
   (:view-preview-visible db)))

(rf/reg-sub
 ::view-preview-url
 (fn [db _]
   (:view-preview-url db)))

;;
;; Derived Subscriptions
;;

(rf/reg-sub
 ::grouped-traces
 :<- [::traces]
 :<- [::expanded-groups]
 (fn [[traces expanded-groups] _]
   (when (seq traces)
     ;; Group traces by command name
     (let [grouped (group-by :command-name traces)
           ;; Sort groups by most recent trace timestamp
           sorted-groups (sort-by (fn [[_cmd-name trace-list]]
                                   (apply max (map #(or (:started-at %) 0) trace-list)))
                                 >
                                 grouped)]
       ;; Transform to list of {:command-name, :traces, :count, :expanded?, :latest-timestamp}
       (mapv (fn [[cmd-name trace-list]]
               (let [sorted-traces (sort-by :started-at > trace-list)]
                 {:command-name cmd-name
                  :traces sorted-traces
                  :count (count sorted-traces)
                  :expanded? (contains? expanded-groups cmd-name)
                  :latest-timestamp (:started-at (first sorted-traces))}))
             sorted-groups)))))

(rf/reg-sub
 ::trace-count
 :<- [::traces]
 (fn [traces _]
   (count traces)))

(rf/reg-sub
 ::has-traces
 :<- [::trace-count]
 (fn [count _]
   (> count 0)))

(rf/reg-sub
 ::tree-structure
 :<- [::current-trace]
 (fn [trace _]
   (:tree-structure trace)))

(rf/reg-sub
 ::execution-events
 :<- [::current-trace]
 (fn [trace _]
   (:execution-events trace)))

(rf/reg-sub
 ::trace-duration
 :<- [::current-trace]
 (fn [trace _]
   (:duration-ms trace)))

(rf/reg-sub
 ::trace-status
 :<- [::current-trace]
 (fn [trace _]
   (:status trace)))

(rf/reg-sub
 ::is-trace-executing
 :<- [::current-trace]
 (fn [trace _]
   (= (:status trace) :running)))

;;
;; Node Status Mapping
;;
;; Build a map of node-id -> status based on execution events
;;

(rf/reg-sub
 ::node-status-map
 :<- [::execution-events]
 (fn [events _]
   (when events
     (reduce
      (fn [acc event]
        (if (= (:event-type event) :node-exit)
          (assoc acc (:node-id event) (:status event))
          acc))
      {}
      events))))

(rf/reg-sub
 ::node-status
 (fn [[_ node-id]]
   [(rf/subscribe [::node-status-map])])
 (fn [[status-map] [_ node-id]]
   (get status-map node-id :pending)))

;;
;; Currently Executing Nodes
;;

(rf/reg-sub
 ::executing-nodes
 :<- [::execution-events]
 (fn [events _]
   (when events
     (let [entered (reduce
                    (fn [acc event]
                      (if (= (:event-type event) :node-enter)
                        (conj acc (:node-id event))
                        acc))
                    #{}
                    events)
           exited (reduce
                   (fn [acc event]
                     (if (= (:event-type event) :node-exit)
                       (conj acc (:node-id event))
                       acc))
                   #{}
                   events)]
       (clojure.set/difference entered exited)))))

(rf/reg-sub
 ::is-node-executing
 (fn [[_ node-id]]
   [(rf/subscribe [::executing-nodes])])
 (fn [[executing] [_ node-id]]
   (contains? executing node-id)))

;;
;; Selected Node Details
;;

(rf/reg-sub
 ::selected-node-events
 (fn [_]
   [(rf/subscribe [::selected-node])
    (rf/subscribe [::execution-events])])
 (fn [[node-id events] _]
   (when (and node-id events)
     (filter #(= (:node-id %) node-id) events))))

(rf/reg-sub
 ::selected-node-details
 (fn [[_ node-id]]
   [(rf/subscribe [::tree-structure])
    (rf/subscribe [::selected-node-events])])
 (fn [[tree events] [_ node-id]]
   (when (and node-id tree)
     (let [node (find-node-in-tree tree node-id)]
       (when node
         {:node node
          :events events})))))

;;
;; Event Emission Subscriptions
;;

(rf/reg-sub
 ::emitted-events
 :<- [::execution-events]
 (fn [events _]
   (when events
     (filter #(= (:event-type %) :events-appended) events))))

;;
;; Node Classification (Command/Query/Event Modeling)
;;

(rf/reg-sub
 ::node-classifications
 :<- [::execution-events]
 (fn [events _]
   (when events
     (let [;; Nodes that emit events are COMMANDS
           command-nodes (into #{}
                              (map :node-id)
                              (filter #(= (:event-type %) :events-appended) events))

           ;; Nodes with "load" in label are QUERIES (read operations)
           ;; We can detect these from node-enter events
           query-nodes (into #{}
                            (keep (fn [event]
                                   (when (and (= (:event-type event) :node-enter)
                                             (or (and (:action-label event)
                                                     (clojure.string/includes?
                                                      (clojure.string/lower-case (:action-label event))
                                                      "load"))
                                                 (and (:action-label event)
                                                     (clojure.string/includes?
                                                      (clojure.string/lower-case (:action-label event))
                                                      "get"))
                                                 (and (:action-label event)
                                                     (clojure.string/includes?
                                                      (clojure.string/lower-case (:action-label event))
                                                      "read"))))
                                     (:node-id event)))
                                 events))

           ;; Nodes with "copilot" or computation-related labels are COMPUTATIONS
           computation-nodes (into #{}
                                  (keep (fn [event]
                                         (when (and (= (:event-type event) :node-enter)
                                                   (or (and (:action-label event)
                                                           (= (:action-label event) "copilot"))
                                                       (and (:action-label event)
                                                           (clojure.string/includes?
                                                            (clojure.string/lower-case (:action-label event))
                                                            "transform"))
                                                       (and (:action-label event)
                                                           (clojure.string/includes?
                                                            (clojure.string/lower-case (:action-label event))
                                                            "parse"))))
                                           (:node-id event)))
                                       events))

           ;; Nodes that are conditions (have :node-type :condition)
           conditional-nodes (into #{}
                                  (keep (fn [event]
                                         (when (and (= (:event-type event) :node-enter)
                                                   (= (:node-type event) :condition))
                                           (:node-id event)))
                                       events))]

       {:commands command-nodes
        :queries query-nodes
        :computations computation-nodes
        :conditionals conditional-nodes}))))

(rf/reg-sub
 ::node-type
 :<- [::node-classifications]
 (fn [classifications [_ node-id]]
   (cond
     (contains? (:commands classifications) node-id) :command
     (contains? (:queries classifications) node-id) :query
     (contains? (:computations classifications) node-id) :computation
     (contains? (:conditionals classifications) node-id) :conditional
     :else :action)))

(rf/reg-sub
 ::emitted-events-count
 :<- [::emitted-events]
 (fn [events _]
   (count events)))

(rf/reg-sub
 ::all-events-flat
 :<- [::emitted-events]
 (fn [event-appends _]
   (when event-appends
     (mapcat (fn [event-append]
               (map (fn [event]
                      (assoc event
                             :emitted-by-node (:node-id event-append)
                             :timestamp (:timestamp event-append)
                             :relative-ms (:relative-ms event-append)))
                    (:events event-append)))
             event-appends))))

;;
;; Read Model Subscriptions
;;

(rf/reg-sub
 ::read-model-events
 :<- [::execution-events]
 (fn [events _]
   (when events
     (filter #(= (:event-type %) :read-model-queried) events))))

(rf/reg-sub
 ::read-model-events-count
 :<- [::read-model-events]
 (fn [events _]
   (count events)))

(rf/reg-sub
 ::total-events-queried
 :<- [::read-model-events]
 (fn [read-events _]
   (when read-events
     (reduce + 0 (map :events-returned read-events)))))
