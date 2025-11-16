(ns store.events
  "Re-frame event handlers for debug UI"
  (:require [re-frame.core :as rf]
            [store.db :as db]))

;;
;; Event Batching for Performance
;;

;; Batch execution events to reduce re-renders during streaming
(defonce event-batch (atom {}))  ; Map of trace-id -> [events]
(defonce batch-timer (atom nil))

(defn flush-event-batch!
  "Flush all batched events to the DB"
  []
  (when (seq @event-batch)
    (js/console.log "🔄 Flushing" (reduce + (map (comp count second) @event-batch)) "batched events")
    (doseq [[trace-id events] @event-batch]
      (rf/dispatch [::flush-batched-events trace-id events]))
    (reset! event-batch {})
    (reset! batch-timer nil)))

(defn schedule-batch-flush!
  "Schedule a batch flush if not already scheduled"
  []
  (when-not @batch-timer
    (reset! batch-timer
            (js/setTimeout flush-event-batch! 100))))  ; Batch for 100ms

;;
;; Initialization
;;

(rf/reg-event-fx
 ::initialize
 (fn [_ _]
   {:db db/default-db
    :dispatch [::fetch-traces]}))

;;
;; Trace List Events
;;

(rf/reg-event-fx
 ::fetch-traces
 (fn [{:keys [db]} [_ opts]]
   {:db (assoc db :loading true :error nil)
    :fetch-traces (merge {:on-success [::fetch-traces-success]
                         :on-failure [::fetch-traces-failure]}
                        opts)}))

(rf/reg-event-db
 ::fetch-traces-success
 (fn [db [_ response]]
   (-> db
       (assoc :traces (:traces response))
       (assoc :loading false))))

(rf/reg-event-db
 ::fetch-traces-failure
 (fn [db [_ error]]
   (-> db
       (assoc :error (str "Failed to fetch traces: " error))
       (assoc :loading false))))

;;
;; Single Trace Events
;;

(rf/reg-event-fx
 ::fetch-trace
 (fn [{:keys [db]} [_ trace-id]]
   {;; DON'T set loading or clear current-trace - keep current trace visible
    :fetch-trace {:trace-id trace-id
                  :on-success [::fetch-trace-success]
                  :on-failure [::fetch-trace-failure]}}))

(rf/reg-event-db
 ::fetch-trace-success
 (fn [db [_ trace]]
   (let [trace-id (:trace-id trace)
         ;; Merge with any streaming events we collected
         streaming-events (get-in db [:streaming-events trace-id])
         merged-trace (if streaming-events
                       (update trace :execution-events
                              (fn [existing]
                                ;; Merge streaming events with fetched events
                                (vec (concat existing streaming-events))))
                       trace)]
     (assoc db :current-trace merged-trace :loading false))))

(rf/reg-event-db
 ::fetch-trace-failure
 (fn [db [_ error]]
   (-> db
       (assoc :error (str "Failed to fetch trace: " error))
       (assoc :loading false))))

(rf/reg-event-fx
 ::select-trace
 (fn [{:keys [db]} [_ trace-id]]
   {:db (assoc db :selected-node nil)  ; Clear selected node
    :dispatch [::fetch-trace trace-id]}))

;;
;; Node Selection
;;

(rf/reg-event-db
 ::select-node
 (fn [db [_ node-id]]
   (assoc db :selected-node node-id)))

(rf/reg-event-db
 ::clear-node-selection
 (fn [db _]
   (assoc db :selected-node nil)))

;;
;; SSE Connection
;;

(rf/reg-event-fx
 ::connect-sse
 (fn [{:keys [db]} _]
   {:db (assoc db :sse-connected :connecting)
    :connect-sse {:on-connected [::sse-connected]
                  :on-event [::sse-event-received]
                  :on-error [::sse-error]}}))

(rf/reg-event-fx
 ::sse-connected
 (fn [{:keys [db]} _]
   {:db (-> db
           (assoc :sse-connected true)
           (assoc :sse-reconnecting false)
           (assoc :loading true))
    ;; Fetch latest traces after reconnecting
    :dispatch [::fetch-traces]}))

(rf/reg-event-fx
 ::sse-event-received
 (fn [{:keys [db]} [_ event]]
   (case (keyword (:event-type event))
     ;; New trace started (partial trace) - update traces list and auto-display
     :trace-started
     (let [trace (:trace event)
           trace-id (:trace-id trace)]
       {:db (-> db
               (update :traces
                      (fn [traces]
                        ;; Add new trace to beginning, remove if already exists
                        (let [filtered (remove #(= (:trace-id %) trace-id) traces)]
                          (vec (cons trace filtered)))))
               ;; Immediately set as current trace (no HTTP fetch needed!)
               (assoc :current-trace trace)
               ;; Clear selected node
               (assoc :selected-node nil))})

     ;; Trace completed - update existing trace in list and cleanup streaming events
     :trace-completed
     (let [trace-summary (:trace event)
           trace-id (:trace-id trace-summary)]
       ;; Flush any pending batched events for this trace immediately
       (when-let [batched-events (get @event-batch trace-id)]
         (js/console.log "🏁 Trace completed, flushing" (count batched-events) "pending events")
         (rf/dispatch [::flush-batched-events trace-id batched-events])
         (swap! event-batch dissoc trace-id))

       {:db (-> db
               (update :traces
                      (fn [traces]
                        ;; Update the trace if it exists, otherwise add it
                        (if-let [idx (first (keep-indexed #(when (= (:trace-id %2) trace-id) %1) traces))]
                          (assoc traces idx (merge (get traces idx) trace-summary))
                          (vec (cons trace-summary traces)))))
               ;; Cleanup streaming events for completed trace (prevent memory leak)
               (update :streaming-events dissoc trace-id)
               ;; Update current-trace status if it's the one being viewed
               (update :current-trace
                      (fn [ct]
                        (if (= (:trace-id ct) trace-id)
                          (merge ct trace-summary)
                          ct))))})

     ;; Real-time execution event
     :execution-event
     {:dispatch [::execution-event-received event]}

     ;; Connected event (initial handshake)
     :connected
     {:db db}  ; Ignore, just confirms connection

     ;; Default: ignore unknown events
     {:db db})))

(rf/reg-event-db
 ::flush-batched-events
 (fn [db [_ trace-id events]]
   (let [current-trace (:current-trace db)]
     ;; Apply all batched events at once
     (cond-> db
       ;; Add to streaming-events map
       true
       (update-in [:streaming-events trace-id]
                  (fn [existing]
                    (vec (concat (or existing []) events))))

       ;; Also update current-trace if we're viewing this trace
       (and current-trace (= trace-id (:trace-id current-trace)))
       (update-in [:current-trace :execution-events]
                  (fn [existing]
                    (vec (concat (or existing []) events))))))))

(rf/reg-event-db
 ::execution-event-received
 (fn [db [_ sse-event]]
   (let [trace-id (:trace-id sse-event)
         event (:event sse-event)]

     ;; Add event to batch instead of immediately updating DB
     (swap! event-batch update trace-id (fnil conj []) event)
     (schedule-batch-flush!)

     ;; Debug logging every 100 events
     (let [total-batched (reduce + (map count (vals @event-batch)))]
       (when (zero? (mod total-batched 100))
         (js/console.log "📊 Total batched events:" total-batched "| Batch size:" (count @event-batch) "traces")))

     ;; Return DB unchanged (actual update happens in flush)
     db)))

(rf/reg-event-db
 ::sse-error
 (fn [db [_ error]]
   (-> db
       (assoc :sse-connected false)
       (assoc :sse-reconnecting true))))

;;
;; View Mode Toggle
;;

(rf/reg-event-db
 ::set-view-mode
 (fn [db [_ mode]]
   (assoc db :view-mode mode)))

;;
;; Trace Grouping
;;

(rf/reg-event-db
 ::toggle-group
 (fn [db [_ command-name]]
   (update db :expanded-groups
           (fn [groups]
             (if (contains? groups command-name)
               (disj groups command-name)
               (conj groups command-name))))))

(rf/reg-event-db
 ::expand-all-groups
 (fn [db _]
   (let [all-command-names (into #{} (map :command-name (:traces db)))]
     (assoc db :expanded-groups all-command-names))))

(rf/reg-event-db
 ::collapse-all-groups
 (fn [db _]
   (assoc db :expanded-groups #{})))

;;
;; Error Handling
;;

(rf/reg-event-db
 ::clear-error
 (fn [db _]
   (assoc db :error nil)))
