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

(rf/reg-event-fx
 ::fetch-traces-success
 (fn [{:keys [db]} [_ response]]
   (let [new-traces (:traces response)
         current-trace-id (get-in db [:current-trace :trace-id])
         pending-trace-id (:pending-trace-fetch db)
         ;; Find if current trace was updated
         updated-current (when current-trace-id
                          (first (filter #(= current-trace-id (:trace-id %)) new-traces)))
         ;; Find pending trace to check if it has session-id
         pending-trace (when pending-trace-id
                        (first (filter #(= pending-trace-id (:trace-id %)) new-traces)))]

     (js/console.log "📋 Traces fetched. Pending trace:" pending-trace-id
                     "| Has session:" (boolean (:session-id pending-trace)))

     ;; Update traces and clear pending flag
     (cond-> {:db (-> db
                     (assoc :traces new-traces)
                     (assoc :loading false)
                     (dissoc :pending-trace-fetch))}

       ;; Fetch pending trace if it exists (for new trace-started events)
       pending-trace-id
       (assoc :dispatch [::fetch-trace pending-trace-id])

       ;; Also fetch if current trace was updated and is live
       (and updated-current (:live? updated-current) (not= pending-trace-id current-trace-id))
       (assoc :dispatch [::fetch-trace current-trace-id])))))

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

(rf/reg-event-fx
 ::fetch-trace-success
 (fn [{:keys [db]} [_ trace]]
   (let [trace-id (:trace-id trace)
         ;; Merge with any streaming events we collected
         streaming-events (get-in db [:streaming-events trace-id])
         merged-trace (if streaming-events
                        (update trace :execution-events
                                (fn [existing]
                                  ;; Merge streaming events with fetched events
                                  (vec (concat existing streaming-events))))
                        trace)
         db' (assoc db :current-trace merged-trace :loading false)

         ;; Look up summary for this trace (has :live? and :session-id when interactive)
         traces (:traces db)
         summary (first (filter #(= trace-id (:trace-id %)) traces))
         session-id (:session-id summary)
         live? (:live? summary)

         ;; Heuristic: treat traces with view or view-action nodes as interactive flows
         tree-structure (:tree-structure merged-trace)
         has-view?
         (boolean
          (when tree-structure
            (some (fn [node]
                    (and (map? node)
                         (or (= :view (:type node))
                             (= :view-action (:type node)))))
                  (tree-seq
                   (fn [x] (or (map? x) (sequential? x)))
                   (fn [x]
                     (cond
                       (map? x) (:children x)
                       (sequential? x) x
                       :else nil))
                   tree-structure))))]
     (js/console.log "🎯 View detection for" trace-id "| Live:" live? "| Session:" session-id "| Has view:" has-view?)
     (when (and live? session-id has-view?)
       (js/console.log "🖼️ Auto-opening live flow preview for" trace-id))
     (cond-> {:db db'}
       (and live? session-id has-view?)
       ;; Automatically open live flow iframe preview inside debug UI
       (assoc :dispatch
              [::show-view-preview
               (str (:api-base db) "/flows/session/" session-id "/current-view")])))))

(rf/reg-event-db
 ::fetch-trace-failure
 (fn [db [_ error]]
   (-> db
       (assoc :error (str "Failed to fetch trace: " error))
       (assoc :loading false))))

(rf/reg-event-fx
 ::select-trace
 (fn [{:keys [db]} [_ trace-id]]
   (let [;; Find the trace in our list to check if it's live
         traces (:traces db)
         selected-trace (first (filter #(= trace-id (:trace-id %)) traces))
         is-live? (:live? selected-trace)]

     (js/console.log "Selected trace" trace-id "- live?" is-live?)

     (cond->  {:db (assoc db :selected-node nil)  ; Clear selected node
               :dispatch [::fetch-trace trace-id]}

       ;; If it's a live trace, start polling for updates
       is-live?
       (assoc :dispatch-later [{:ms 1000 :dispatch [::poll-live-trace-updates trace-id]}])))))

;;
;; Live Trace Polling
;;

(rf/reg-event-fx
 ::poll-live-trace-updates
 (fn [{:keys [db]} [_ original-trace-id]]
   (let [current-trace (:current-trace db)
         traces (:traces db)
         api-base (:api-base db)

         ;; Find if ANY trace for this session is live
         current-session-traces (filter #(= (:command-name %) (:command-name current-trace)) traces)
         any-live? (some :live? current-session-traces)

         ;; Get the LATEST trace (highest trace-id timestamp)
         latest-trace (last (sort-by :started-at current-session-traces))
         latest-trace-id (:trace-id latest-trace)

         ;; Check if we should auto-select the latest
         viewing-old-trace? (and latest-trace-id
                                (not= latest-trace-id (:trace-id current-trace)))]

     (js/console.log "🔄 Poll check - any live?" any-live? "viewing old?" viewing-old-trace?)

     (cond
       ;; New trace available! Auto-select it
       viewing-old-trace?
       (do
         (js/console.log "🔄 Auto-selecting latest trace:" latest-trace-id)
         {:dispatch [::select-trace latest-trace-id]
          :dispatch-later [{:ms 1000 :dispatch [::poll-live-trace-updates latest-trace-id]}]})

       ;; Still live, continue polling
       any-live?
       {:dispatch [::fetch-traces]
        :dispatch-later [{:ms 1000 :dispatch [::poll-live-trace-updates original-trace-id]}]}

       ;; Not live anymore, stop
       :else
       (do
         (js/console.log "⏸️  Session completed, stopping poll")
         {:db db})))))

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
       (js/console.log "🚀 Trace started:" trace-id "- fetching metadata and full trace")
       {:db (-> db
               (update :traces
                      (fn [traces]
                        ;; Add new trace to beginning, remove if already exists
                        (let [filtered (remove #(= (:trace-id %) trace-id) traces)]
                          (vec (cons trace filtered)))))
               ;; Immediately set as current trace (no HTTP fetch needed!)
               (assoc :current-trace trace)
               ;; Clear selected node
               (assoc :selected-node nil)
               ;; Mark that we need to fetch this trace after traces list updates
               (assoc :pending-trace-fetch trace-id))
        ;; First fetch traces list to get session-id/live? metadata
        :dispatch [::fetch-traces]})

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

(rf/reg-event-fx
 ::flush-batched-events
 (fn [{:keys [db]} [_ trace-id events]]
   (let [current-trace (:current-trace db)
         ;; Check if any of the new events contain view nodes
         has-view-event? (some (fn [event]
                                 (and (map? event)
                                      (or (= :view (:type event))
                                          (= :view-action (:type event)))))
                              events)
         ;; Look up trace info for session-id
         traces (:traces db)
         trace-info (first (filter #(= trace-id (:trace-id %)) traces))
         session-id (:session-id trace-info)
         is-live? (:live? trace-info)
         ;; Check if preview is already open
         preview-open? (:view-preview-visible db)]

     (when (and has-view-event? is-live? session-id (not preview-open?)
                current-trace (= trace-id (:trace-id current-trace)))
       (js/console.log "🎨 View node detected in execution events - opening preview for" trace-id))

     ;; Apply all batched events at once
     (cond-> {:db (cond-> db
                    ;; Add to streaming-events map
                    true
                    (update-in [:streaming-events trace-id]
                               (fn [existing]
                                 (vec (concat (or existing []) events))))

                    ;; Also update current-trace if we're viewing this trace
                    (and current-trace (= trace-id (:trace-id current-trace)))
                    (update-in [:current-trace :execution-events]
                               (fn [existing]
                                 (vec (concat (or existing []) events)))))}

       ;; Auto-open view preview if we detect view nodes and haven't opened yet
       (and has-view-event? is-live? session-id (not preview-open?)
            current-trace (= trace-id (:trace-id current-trace)))
       (assoc :dispatch [::show-view-preview
                        (str (:api-base db) "/flows/session/" session-id "/current-view")])))))

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
;; Terminal Toggle
;;

(rf/reg-event-db
 ::toggle-terminal
 (fn [db _]
   (let [new-state (not (:terminal-visible db))]
     (js/console.log "🎹 Toggle terminal event - Current:" (:terminal-visible db) "→ New:" new-state)
     (assoc db :terminal-visible new-state))))

(rf/reg-event-db
 ::show-terminal
 (fn [db _]
   (assoc db :terminal-visible true)))

(rf/reg-event-db
 ::hide-terminal
 (fn [db _]
   (assoc db :terminal-visible false)))

;;
;; View Preview Toggle
;;

(rf/reg-event-db
 ::show-view-preview
 (fn [db [_ preview-url]]
   (js/console.log "🖼 Show view preview:" preview-url)
   (assoc db
          :view-preview-visible true
          :view-preview-url preview-url)))

(rf/reg-event-db
 ::close-view-preview
 (fn [db _]
   (js/console.log "🖼 Close view preview")
   (assoc db
          :view-preview-visible false
          :view-preview-url nil)))

;;
;; Error Handling
;;

(rf/reg-event-db
 ::clear-error
 (fn [db _]
   (assoc db :error nil)))