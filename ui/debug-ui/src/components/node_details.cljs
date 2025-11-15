(ns components.node-details
  "Node details panel component"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-com.core :as rc]
            [store.subs :as subs]
            [store.events :as events]
            [styles.core :as s]))

(defn format-memory
  "Format memory state as pretty JSON"
  [memory]
  (when memory
    (js/JSON.stringify (clj->js memory) nil 2)))

(defn highlight-json
  "Apply Prism syntax highlighting to JSON"
  [json-str]
  (when (and json-str js/Prism)
    (try
      (js/Prism.highlight json-str
                         (aget (.-languages js/Prism) "json")
                         "json")
      (catch js/Error _
        ;; Fallback to plain JSON if Prism fails
        json-str))))

(defn safe-event-type
  "Safely extract event type as string"
  [event]
  (let [t (get event :type (get event "type"))]
    (cond
      (keyword? t) (name t)
      (string? t) t
      :else (str t))))

(defn emitted-event-item
  [{:keys [event emitted-by-node relative-ms]}]
  (let [event-type (safe-event-type event)
        event-id (get event :event/id (get event "event/id"))
        body (get event :body (get event "body"))]
    [:div {:key (or event-id (random-uuid))
           :style {:background-color "#581c87"    ; bg-purple-900
                   :border-radius "0.25rem"        ; rounded
                   :padding "0.5rem"               ; p-2
                   :border-left "2px solid"        ; border-l-2
                   :border-left-color "#a855f7"}}  ; border-purple-500

     ;; Event type
     [:div {:style {:display "flex"
                    :align-items "center"
                    :gap "0.5rem"
                    :margin-bottom "0.25rem"}}     ; flex items-center gap-2 mb-1
      [:span {:style {:font-size "0.75rem"       ; text-xs
                      :line-height "1rem"
                      :color "#c084fc"             ; text-purple-400
                      :font-family "ui-monospace, 'Fira Code'"}}  ; font-mono
       (str "[" relative-ms "ms]")]
      [:span {:style {:font-size "0.875rem"      ; text-sm
                      :line-height "1.25rem"
                      :font-weight "600"           ; font-semibold
                      :color "#d8b4fe"}}           ; text-purple-300
       (str "" event-type)]]

     ;; Emitted by
     (when emitted-by-node
       [:div {:style {:font-size "0.75rem"       ; text-xs
                      :line-height "1rem"
                      :color "#c084fc"}}           ; text-purple-400
        (str "from: " emitted-by-node)])

     ;; Event body (collapsible)
     (when body
       [:details {:style {:margin-top "0.25rem"}}  ; mt-1
        [:summary {:style {:cursor "pointer"       ; cursor-pointer
                           :font-size "0.75rem"    ; text-xs
                           :line-height "1rem"
                           :color "#c084fc"}}      ; text-purple-400 (hover ignored for inline styles)
         "View payload"]
        [:pre {:style {:font-size "0.75rem"        ; text-xs
                       :line-height "1rem"
                       :background-color "#3b0764" ; bg-purple-950
                       :padding "0.5rem"           ; p-2
                       :border-radius "0.25rem"    ; rounded
                       :margin-top "0.5rem"        ; mt-2
                       :overflow "auto"            ; overflow-auto
                       :color "#d8b4fe"            ; text-purple-300
                       :font-family "ui-monospace, 'Fira Code'"}}  ; font-mono
         (js/JSON.stringify (clj->js body) nil 2)]])]))

(defn event-item
  [{:keys [event]}]
  (let [{:keys [event-type relative-ms duration-ms status action-label node-id
                changes memory-state event-types events events-returned]} event
        color (case event-type
                :node-enter "#60a5fa"        ; text-blue-400
                :node-exit "#22c3ee"         ; text-green-400 (using cyan)
                :memory-snapshot "#c084fc"   ; text-purple-400
                :events-appended "#c084fc"   ; text-purple-400
                :read-model-queried "#22d3ee" ; text-cyan-400
                :dspy-call-start "#facc15"   ; text-yellow-400
                :dspy-call-complete "#facc15" ; text-yellow-400
                "#9ca3af")]                  ; text-gray-400
    [:div {:style {:padding-top "0.25rem"
                   :padding-bottom "0.25rem"}}  ; py-1
     ;; Event header line
     [:div {:style {:display "flex"
                    :align-items "flex-start"
                    :gap "0.5rem"
                    :font-size "0.875rem"       ; text-sm
                    :line-height "1.25rem"}}
      [:span {:style {:font-family "ui-monospace, 'Fira Code'"  ; font-mono
                      :color color}}
       (str "[" relative-ms "ms]")]

      ;; Special rendering for different event types
      (cond
        ;; Read model query
        (= event-type :read-model-queried)
        [:div {:style {:display "flex"
                       :align-items "center"
                       :gap "0.5rem"}}
         [:span {:style {:color "#22d3ee"}}  ; text-cyan-400
          "Read Model Query"]
         (when node-id
           [:span {:style {:color "#6b7280"}}  ; text-gray-500
            (str "(" node-id ")")])]

        ;; Events appended
        (= event-type :events-appended)
        [:div {:style {:display "flex"
                       :align-items "center"
                       :gap "0.5rem"}}
         [:span {:style {:color "#c084fc"}}  ; text-purple-400
          "Events Emitted"]
         (when events
           [:span {:style {:color "#d8b4fe"}}  ; text-purple-300
            (str "(" (count events) ")")])
         (when node-id
           [:span {:style {:color "#6b7280"}}  ; text-gray-500
            (str "(" node-id ")")])]

        ;; Default rendering
        :else
        [:div {:style {:display "flex"
                       :align-items "center"
                       :gap "0.5rem"}}
         [:span {:style {:color "#d1d5db"}}  ; text-gray-300
          (if (keyword? event-type) (name event-type) (str event-type))]
         (when action-label
           [:span {:style {:color "#93c5fd"}}  ; text-blue-300
            (str ": " action-label)])
         (when (and (not action-label) node-id)
           [:span {:style {:color "#6b7280"}}  ; text-gray-500
            (str " (" node-id ")")])
         (when duration-ms
           [:span {:style {:color "#6b7280"}}  ; text-gray-500
            (str " (" duration-ms "ms)")])
         (when status
           [:span {:style {:color "#9ca3af"}}  ; text-gray-400
            (str " → " (if (keyword? status) (name status) (str status)))])])]

     ;; Show details based on event type
     (cond
       ;; Read model query - show event types
       (and (= event-type :read-model-queried) (seq event-types))
       [:div {:style {:margin-left "1.5rem"      ; ml-6
                      :margin-top "0.25rem"      ; mt-1
                      :font-size "0.75rem"       ; text-xs
                      :line-height "1rem"
                      :color "#67e8f9"}}         ; text-cyan-300
        (for [et event-types]
          [:div {:key (str et)}
           (str "  • " (if (keyword? et) (name et) (str et)))])]

       ;; Events appended - show emitted events
       (and (= event-type :events-appended) (seq events))
       [:div {:style {:margin-left "1.5rem"      ; ml-6
                      :margin-top "0.25rem"      ; mt-1
                      :font-size "0.75rem"       ; text-xs
                      :line-height "1rem"
                      :color "#d8b4fe"}}         ; text-purple-300
        (for [evt events]
          (let [evt-type (:type evt)]
            [:div {:key (or (:event/id evt) (random-uuid))}
             (str "  • " (if (keyword? evt-type) (name evt-type) (str evt-type)))]))]

       ;; Memory snapshot - show full state
       (and (= event-type :memory-snapshot) memory-state)
       (let [json-str (format-memory memory-state)
             highlighted (highlight-json json-str)]
         [:div {:style {:margin-left "1.5rem"         ; ml-6
                        :margin-top "0.5rem"          ; mt-2
                        :background-color "#1f2937"   ; bg-gray-800
                        :border-radius "0.25rem"      ; rounded
                        :padding "0.75rem"            ; p-3
                        :border-left "2px solid"      ; border-l-2
                        :border-left-color "#a855f7"}} ; border-purple-500
          [:div {:style {:font-size "0.75rem"         ; text-xs
                         :line-height "1rem"
                         :color "#c084fc"             ; text-purple-400
                         :font-weight "600"           ; font-semibold
                         :margin-bottom "0.5rem"}}    ; mb-2
           "Full Memory State"]
          (if highlighted
            [:pre {:style (merge {:font-size "0.75rem"       ; text-xs
                                  :line-height "1rem"
                                  :background-color "#030712" ; bg-gray-950
                                  :padding "0.5rem"           ; p-2
                                  :border-radius "0.25rem"    ; rounded
                                  :overflow-x "auto"          ; overflow-x-auto
                                  :white-space "pre-wrap"})
                   :dangerouslySetInnerHTML {:__html (str "<code class=\"language-json\">" highlighted "</code>")}}]
            [:pre {:style {:font-size "0.75rem"       ; text-xs
                           :line-height "1rem"
                           :background-color "#030712" ; bg-gray-950
                           :padding "0.5rem"           ; p-2
                           :border-radius "0.25rem"    ; rounded
                           :overflow-x "auto"          ; overflow-x-auto
                           :color "#d1d5db"            ; text-gray-300
                           :white-space "pre-wrap"}}
             json-str])]))]))

(defn strip-prefix
  "Strip trace prefix from node-id"
  [prefixed-id]
  (when prefixed-id
    (last (clojure.string/split prefixed-id #"-(?=\d)"))))

(defn get-memory-snapshots-for-node
  "Get memory snapshots that occurred during a node's execution.
   Memory snapshots don't have node-id, so we find them by timeline position."
  [events node-id]
  (let [;; Find the node-enter and node-exit events
        node-enter (first (filter #(and (= (:event-type %) :node-enter)
                                       (= (:node-id %) node-id))
                                 events))
        node-exit (first (filter #(and (= (:event-type %) :node-exit)
                                      (= (:node-id %) node-id))
                                events))]
    (when (and node-enter node-exit)
      (let [enter-id (:event-id node-enter)
            exit-id (:event-id node-exit)]
        ;; Find memory snapshots between enter and exit
        (filter #(and (= (:event-type %) :memory-snapshot)
                     (> (:event-id %) enter-id)
                     (< (:event-id %) exit-id))
               events)))))

(defn is-event-annotation-node?
  "Check if selected node is an event annotation node."
  [node-id]
  (when node-id
    (clojure.string/includes? node-id "-event-annotation")))

(defn is-read-model-annotation-node?
  "Check if selected node is a read-model annotation node."
  [node-id]
  (when node-id
    (clojure.string/includes? node-id "-read-model-annotation")))

(defn is-memory-annotation-node?
  "Check if selected node is a memory annotation node."
  [node-id]
  (when node-id
    (and (clojure.string/includes? node-id "-annotation")
         (not (clojure.string/includes? node-id "-event-annotation"))
         (not (clojure.string/includes? node-id "-read-model-annotation"))
         (not (clojure.string/includes? node-id "-dspy-annotation")))))

(defn is-dspy-annotation-node?
  "Check if selected node is a DSPy annotation node."
  [node-id]
  (when node-id
    (clojure.string/includes? node-id "-dspy-annotation")))

(defn extract-events-from-node-id
  "Extract events data from execution events for a given event annotation node."
  [node-id execution-events]
  (when (is-event-annotation-node? node-id)
    ;; Extract parent node ID (remove "-event-annotation" suffix)
    (let [parent-node-id (clojure.string/replace node-id #"-event-annotation$" "")
          unprefixed-id (last (clojure.string/split parent-node-id #"-(?=\d)"))
          ;; Find the :events-appended event for this node
          events-appended (first (filter #(and (= (:event-type %) :events-appended)
                                               (= (:node-id %) unprefixed-id))
                                         execution-events))]
      (:events events-appended))))

(defn extract-read-models-from-node-id
  "Extract read model data from execution events for a given read-model annotation node."
  [node-id execution-events]
  (when (is-read-model-annotation-node? node-id)
    ;; Extract parent node ID (remove "-read-model-annotation" suffix)
    (let [parent-node-id (clojure.string/replace node-id #"-read-model-annotation$" "")
          unprefixed-id (last (clojure.string/split parent-node-id #"-(?=\d)"))
          ;; Find all :read-model-queried events for this node
          read-models (filter #(and (= (:event-type %) :read-model-queried)
                                   (= (:node-id %) unprefixed-id))
                             execution-events)]
      read-models)))

(defn extract-memory-changes-from-node-id
  "Extract memory changes from execution events for a given memory annotation node."
  [node-id execution-events]
  (when (is-memory-annotation-node? node-id)
    ;; Extract parent node ID (remove "-annotation" suffix)
    (let [parent-node-id (clojure.string/replace node-id #"-annotation$" "")
          unprefixed-id (last (clojure.string/split parent-node-id #"-(?=\d)"))
          ;; Find memory snapshot events for this node
          memory-snapshots (get-memory-snapshots-for-node execution-events unprefixed-id)]
      ;; Extract changes from all snapshots and realize the lazy sequence
      (vec (mapcat :changes memory-snapshots)))))

(defn extract-dspy-event-from-node-id
  "Extract DSPy event from execution events for a given DSPy annotation node."
  [node-id execution-events]
  (when (is-dspy-annotation-node? node-id)
    ;; Extract parent node ID (remove "-dspy-annotation" suffix)
    (let [parent-node-id (clojure.string/replace node-id #"-dspy-annotation$" "")
          unprefixed-id (last (clojure.string/split parent-node-id #"-(?=\d)"))
          ;; Find events-appended for this node
          events-appended (filter #(and (= (:event-type %) :events-appended)
                                       (= (:node-id %) unprefixed-id))
                                 execution-events)]
      ;; Find the DSPy reasoning event
      (first (mapcat (fn [evt-append]
                      (filter #(= (:type %) :grain.agent/reasoned)
                             (:events evt-append)))
                    events-appended)))))

(defn node-details
  []
  (let [selected-node @(rf/subscribe [::subs/selected-node])
        all-events @(rf/subscribe [::subs/execution-events])
        emitted-events-flat @(rf/subscribe [::subs/all-events-flat])
        is-event-node? (is-event-annotation-node? selected-node)
        is-read-model-node? (is-read-model-annotation-node? selected-node)
        is-memory-node? (is-memory-annotation-node? selected-node)
        is-dspy-node? (is-dspy-annotation-node? selected-node)
        selected-events (when is-event-node?
                         (extract-events-from-node-id selected-node all-events))
        selected-read-models (when is-read-model-node?
                              (extract-read-models-from-node-id selected-node all-events))
        selected-memory-changes (when is-memory-node?
                                  (extract-memory-changes-from-node-id selected-node all-events))
        selected-dspy-event (when is-dspy-node?
                             (extract-dspy-event-from-node-id selected-node all-events))]

    [rc/v-box
     :style (merge s/h-full s/flex s/flex-col s/bg-gray-900  )
     :children
     [;; Header
      [rc/h-box
       :style (merge s/p-4  s/flex s/items-center s/justify-between)
       :children
       [[rc/box :style (merge s/text-lg s/font-bold s/text-gray-100)
         :child (cond
           is-event-node? "Events Emitted"
           is-read-model-node? "Read Model Queries"
           is-memory-node? "Memory Diff"
           is-dspy-node? "DSPy AI Call"
           selected-node "Node Details"
           :else "EXECUTION TIMELINE")]
        (when selected-node
          [:button {:style {:color "#9ca3af"           ; text-gray-400 (hover ignored for inline)
                            :font-size "1.25rem"}      ; text-xl
                    :on-click #(rf/dispatch [::events/clear-node-selection])}
           "×"])]]

      ;; Content (scrollable)
      [rc/box
       :style (merge s/flex-1 s/overflow-y-auto s/p-4)
       :child
       (cond
         ;; Read model annotation - show query details
         is-read-model-node?
         [:div
          (if (seq selected-read-models)
            [:div {:style {:display "flex"
                           :flex-direction "column"
                           :gap "1rem"}}              ; space-y-4
             (for [read-op selected-read-models]
               [:div {:key (:event-id read-op)
                      :style {:background-color "#164e63"   ; bg-cyan-900
                              :border-radius "0.25rem"      ; rounded
                              :padding "0.75rem"            ; p-3
                              :border-left "2px solid"      ; border-l-2
                              :border-left-color "#06b6d4"}} ; border-cyan-500
                [:div {:style {:font-size "0.875rem"       ; text-sm
                               :line-height "1.25rem"
                               :font-weight "600"          ; font-semibold
                               :color "#67e8f9"            ; text-cyan-300
                               :margin-bottom "0.5rem"}}   ; mb-2
                 (str "Query #" (:event-id read-op))]
                [:div {:style {:margin-bottom "0.5rem"}}   ; mb-2
                 [:div {:style {:font-size "0.75rem"       ; text-xs
                                :line-height "1rem"
                                :color "#22d3ee"           ; text-cyan-400
                                :margin-bottom "0.25rem"}} ; mb-1
                  "Query:"]
                 [:div {:style {:font-size "0.875rem"      ; text-sm
                                :line-height "1.25rem"
                                :color "#67e8f9"}}         ; text-cyan-300
                  "Event stream queried"]]
                (when-let [event-types (:event-types read-op)]
                  [:div {:style {:margin-bottom "0.5rem"}} ; mb-2
                   [:div {:style {:font-size "0.75rem"     ; text-xs
                                  :line-height "1rem"
                                  :color "#22d3ee"         ; text-cyan-400
                                  :margin-bottom "0.25rem"}} ; mb-1
                    "Event Types:"]
                   [:div {:style {:display "flex"
                                  :flex-direction "column"
                                  :gap "0.25rem"}}         ; space-y-1
                    (for [event-type event-types]
                      [:div {:key (str event-type)
                             :style {:font-size "0.75rem"      ; text-xs
                                     :line-height "1rem"
                                     :color "#67e8f9"          ; text-cyan-300
                                     :font-family "ui-monospace, 'Fira Code'"}} ; font-mono
                       (str "  • " (if (keyword? event-type) (name event-type) (str event-type)))])]])
                (when-let [tags (:event-tags read-op)]
                  [:div {:style {:margin-bottom "0.5rem"}} ; mb-2
                   [:div {:style {:font-size "0.75rem"     ; text-xs
                                  :line-height "1rem"
                                  :color "#22d3ee"         ; text-cyan-400
                                  :margin-bottom "0.25rem"}} ; mb-1
                    "Query Tags:"]
                   [:div {:style {:display "flex"
                                  :flex-direction "column"
                                  :gap "0.25rem"}}         ; space-y-1
                    (for [[tag-key tag-value] tags]
                      [:div {:key (str tag-key tag-value)
                             :style {:font-size "0.75rem"      ; text-xs
                                     :line-height "1rem"
                                     :color "#67e8f9"          ; text-cyan-300
                                     :font-family "ui-monospace, 'Fira Code'"}} ; font-mono
                       (str "  " tag-key ": " tag-value)])]])
                [:div {:style {:font-size "0.75rem"        ; text-xs
                               :line-height "1rem"
                               :color "#06b6d4"            ; text-cyan-500
                               :margin-top "0.5rem"}}      ; mt-2
                 (str "@ " (:relative-ms read-op) "ms")]])]
            [:div {:style {:font-size "0.875rem"           ; text-sm
                           :line-height "1.25rem"
                           :color "#6b7280"                ; text-gray-500
                           :text-align "center"
                           :margin-top "2rem"}}            ; text-center mt-8
             "No read models"])]

         ;; Event annotation - show event details
         is-event-node?
         [:div
          (if (seq selected-events)
            [:div {:style {:display "flex"
                           :flex-direction "column"
                           :gap "1rem"}}              ; space-y-4
             (for [event selected-events]
               [:div {:key (or (:event/id event) (random-uuid))
                      :style {:background-color "#581c87"    ; bg-purple-900
                              :border-radius "0.25rem"       ; rounded
                              :padding "0.75rem"             ; p-3
                              :border-left "2px solid"       ; border-l-2
                              :border-left-color "#a855f7"}} ; border-purple-500
                [:div {:style {:font-size "0.875rem"        ; text-sm
                               :line-height "1.25rem"
                               :font-weight "600"           ; font-semibold
                               :color "#d8b4fe"             ; text-purple-300
                               :margin-bottom "0.5rem"}}    ; mb-2
                 (str "" (safe-event-type event))]
                (when-let [event-id (:event/id event)]
                  [:div {:style {:font-size "0.75rem"       ; text-xs
                                 :line-height "1rem"
                                 :color "#c084fc"           ; text-purple-400
                                 :font-family "ui-monospace, 'Fira Code'" ; font-mono
                                 :margin-bottom "0.5rem"}}  ; mb-2
                   (str "ID: " event-id)])
                (when-let [tags (:tags event)]
                  [:div {:style {:margin-bottom "0.5rem"}}  ; mb-2
                   [:div {:style {:font-size "0.75rem"      ; text-xs
                                  :line-height "1rem"
                                  :color "#c084fc"          ; text-purple-400
                                  :margin-bottom "0.25rem"}} ; mb-1
                    "Tags:"]
                   [:div {:style {:display "flex"
                                  :flex-direction "column"
                                  :gap "0.25rem"}}          ; space-y-1
                    (for [[tag-key tag-value] tags]
                      [:div {:key (str tag-key tag-value)
                             :style {:font-size "0.75rem"       ; text-xs
                                     :line-height "1rem"
                                     :color "#d8b4fe"           ; text-purple-300
                                     :font-family "ui-monospace, 'Fira Code'"}} ; font-mono
                       (str "  " (name tag-key) ": " tag-value)])]])
                (when-let [body (:body event)]
                  (let [json-str (js/JSON.stringify (clj->js body) nil 2)
                        highlighted (highlight-json json-str)]
                    [:div
                     [:div {:style {:font-size "0.75rem"    ; text-xs
                                    :line-height "1rem"
                                    :color "#c084fc"        ; text-purple-400
                                    :margin-bottom "0.25rem"}} ; mb-1
                      "Payload:"]
                     (if highlighted
                       [:pre {:style (merge {:font-size "0.75rem"       ; text-xs
                                             :line-height "1rem"
                                             :background-color "#3b0764" ; bg-purple-950
                                             :padding "0.5rem"           ; p-2
                                             :border-radius "0.25rem"    ; rounded
                                             :overflow "auto"            ; overflow-auto
                                             :white-space "pre-wrap"})
                              :dangerouslySetInnerHTML {:__html (str "<code class=\"language-json\">" highlighted "</code>")}}]
                       [:pre {:style {:font-size "0.75rem"       ; text-xs
                                      :line-height "1rem"
                                      :background-color "#3b0764" ; bg-purple-950
                                      :padding "0.5rem"           ; p-2
                                      :border-radius "0.25rem"    ; rounded
                                      :overflow "auto"            ; overflow-auto
                                      :color "#d8b4fe"            ; text-purple-300
                                      :font-family "ui-monospace, 'Fira Code'" ; font-mono
                                      :white-space "pre-wrap"}}
                        json-str])]))
                (when-let [timestamp (:event/timestamp event)]
                  [:div {:style {:font-size "0.75rem"       ; text-xs
                                 :line-height "1rem"
                                 :color "#a855f7"           ; text-purple-500
                                 :margin-top "0.5rem"}}     ; mt-2
                   (str "Timestamp: " timestamp)])])]
            [:div {:style {:font-size "0.875rem"           ; text-sm
                           :line-height "1.25rem"
                           :color "#6b7280"                ; text-gray-500
                           :text-align "center"
                           :margin-top "2rem"}}            ; text-center mt-8
             "No events"])]

         ;; Memory annotation - show memory diff
         is-memory-node?
         [:div
          (if (seq selected-memory-changes)
            [:div {:style {:display "flex"
                           :flex-direction "column"
                           :gap "0.75rem"}}           ; space-y-3
             (for [change selected-memory-changes]
               (let [k (:key change)
                     key-name (cond
                               (keyword? k) (name k)
                               (string? k) k
                               :else (str k))
                     old-val (:old-value change)
                     new-val (:new-value change)
                     json-str (js/JSON.stringify (clj->js new-val) nil 2)
                     highlighted (highlight-json json-str)]
                 [:div {:key key-name
                        :style {:background-color "#1f2937"   ; bg-gray-800
                                :border-radius "0.25rem"      ; rounded
                                :padding "0.75rem"            ; p-3
                                :border-left "2px solid"      ; border-l-2
                                :border-left-color "#a855f7"}} ; border-purple-500
                  [:div {:style {:font-size "0.875rem"       ; text-sm
                                 :line-height "1.25rem"
                                 :font-weight "600"          ; font-semibold
                                 :color "#d8b4fe"            ; text-purple-300
                                 :margin-bottom "0.5rem"}}   ; mb-2
                   (str (if (nil? old-val) "➕ " "📝 ") key-name)]
                  [:div
                   [:div {:style {:font-size "0.75rem"       ; text-xs
                                  :line-height "1rem"
                                  :color "#c084fc"           ; text-purple-400
                                  :margin-bottom "0.25rem"}} ; mb-1
                    (if (nil? old-val) "Added:" "Updated to:")]
                   (if highlighted
                     [:pre {:style (merge {:font-size "0.75rem"       ; text-xs
                                           :line-height "1rem"
                                           :background-color "#030712" ; bg-gray-950
                                           :padding "0.5rem"           ; p-2
                                           :border-radius "0.25rem"    ; rounded
                                           :overflow "auto"            ; overflow-auto
                                           :white-space "pre-wrap"})
                            :dangerouslySetInnerHTML {:__html (str "<code class=\"language-json\">" highlighted "</code>")}}]
                     [:pre {:style {:font-size "0.75rem"       ; text-xs
                                    :line-height "1rem"
                                    :background-color "#030712" ; bg-gray-950
                                    :padding "0.5rem"           ; p-2
                                    :border-radius "0.25rem"    ; rounded
                                    :overflow "auto"            ; overflow-auto
                                    :color "#d1d5db"            ; text-gray-300
                                    :white-space "pre-wrap"}}
                      json-str])]]))]
            [:div {:style {:font-size "0.875rem"           ; text-sm
                           :line-height "1.25rem"
                           :color "#6b7280"                ; text-gray-500
                           :text-align "center"
                           :margin-top "2rem"}}            ; text-center mt-8
             "No memory changes"])]

         ;; DSPy annotation - show AI reasoning
         is-dspy-node?
         [:div
          (when selected-dspy-event
            (let [json-str (js/JSON.stringify (clj->js selected-dspy-event) nil 2)
                  highlighted (highlight-json json-str)]
              [:div
               [:div {:style {:font-size "0.875rem"        ; text-sm
                              :line-height "1.25rem"
                              :font-weight "600"           ; font-semibold
                              :margin-bottom "0.75rem"     ; mb-3
                              :color "#6ee7b7"}}
                "DSPy Event Data"]
               (if highlighted
                 [:pre {:style (merge {:font-size "0.75rem"     ; text-xs
                                       :line-height "1rem"
                                       :padding "0.75rem"       ; p-3
                                       :border-radius "0.25rem" ; rounded
                                       :overflow "auto"         ; overflow-auto
                                       :white-space "pre-wrap"
                                       :backgroundColor "#064e3b"
                                       :border "1px solid #059669"})
                        :dangerouslySetInnerHTML {:__html (str "<code class=\"language-json\">" highlighted "</code>")}}]
                 [:pre {:style {:font-size "0.75rem"       ; text-xs
                                :line-height "1rem"
                                :padding "0.75rem"         ; p-3
                                :border-radius "0.25rem"   ; rounded
                                :overflow "auto"           ; overflow-auto
                                :white-space "pre-wrap"
                                :backgroundColor "#064e3b"
                                :color "#6ee7b7"}}
                  json-str])]))]

         ;; Regular node selected - show node details
         (and selected-node
              (not is-memory-node?)
              (not is-read-model-node?)
              (not is-event-node?)
              (not is-dspy-node?))
         (let [unprefixed-id (strip-prefix selected-node)
               node-events (filter #(= (:node-id %) unprefixed-id) all-events)
               memory-events (get-memory-snapshots-for-node all-events unprefixed-id)]
           [:div
            [:div {:style {:font-size "0.875rem"           ; text-sm
                           :line-height "1.25rem"
                           :color "#60a5fa"                ; text-blue-400
                           :font-family "ui-monospace, 'Fira Code'" ; font-mono
                           :margin-bottom "1rem"}}         ; mb-4
             unprefixed-id]

            ;; Memory section
            (when (seq memory-events)
              [:div {:style {:margin-bottom "1.5rem"}}     ; mb-6
               [:h4 {:style {:font-size "0.875rem"         ; text-sm
                             :line-height "1.25rem"
                             :font-weight "600"            ; font-semibold
                             :color "#d1d5db"              ; text-gray-300
                             :margin-bottom "0.5rem"}}     ; mb-2
                "Memory Snapshots (" (count memory-events) ")"]
               [:div {:style {:display "flex"
                              :flex-direction "column"
                              :gap "0.75rem"}}             ; space-y-3
                (for [event memory-events]
                  (let [json-str (format-memory (:memory-state event))
                        highlighted (highlight-json json-str)]
                    [:div {:key (:event-id event)
                           :style {:background-color "#1f2937"   ; bg-gray-800
                                   :border-radius "0.25rem"      ; rounded
                                   :padding "0.75rem"            ; p-3
                                   :border-left "2px solid"      ; border-l-2
                                   :border-left-color "#a855f7"}} ; border-purple-500
                     [:div {:style {:font-size "0.75rem"         ; text-xs
                                    :line-height "1rem"
                                    :color "#9ca3af"             ; text-gray-400
                                    :margin-bottom "0.5rem"}}    ; mb-2
                      (str "@ " (:relative-ms event) "ms")]
                     (if highlighted
                       [:pre {:style (merge {:font-size "0.75rem"       ; text-xs
                                             :line-height "1rem"
                                             :background-color "#030712" ; bg-gray-950
                                             :padding "0.5rem"           ; p-2
                                             :border-radius "0.25rem"    ; rounded
                                             :overflow-x "auto"          ; overflow-x-auto
                                             :white-space "pre-wrap"})
                              :dangerouslySetInnerHTML {:__html (str "<code class=\"language-json\">" highlighted "</code>")}}]
                       [:pre {:style {:font-size "0.75rem"       ; text-xs
                                      :line-height "1rem"
                                      :background-color "#030712" ; bg-gray-950
                                      :padding "0.5rem"           ; p-2
                                      :border-radius "0.25rem"    ; rounded
                                      :overflow-x "auto"          ; overflow-x-auto
                                      :color "#d1d5db"            ; text-gray-300
                                      :white-space "pre-wrap"}}
                        json-str])]))]])

            ;; All events for this node
            (if (seq node-events)
              [:div
               [:h4 {:style {:font-size "0.875rem"         ; text-sm
                             :line-height "1.25rem"
                             :font-weight "600"            ; font-semibold
                             :color "#d1d5db"              ; text-gray-300
                             :margin-bottom "0.5rem"}}     ; mb-2
                "All Events (" (count node-events) ")"]
               [:div {:style {:display "flex"
                              :flex-direction "column"
                              :gap "0.25rem"}}             ; space-y-1
                (for [event node-events]
                  ^{:key (:event-id event)}
                  [event-item {:event event}])]]
              [:div {:style {:font-size "0.875rem"         ; text-sm
                             :line-height "1.25rem"
                             :color "#6b7280"              ; text-gray-500
                             :text-align "center"
                             :margin-top "2rem"}}          ; text-center mt-8
               "No events for this node."])])

         ;; Default: Show execution timeline
         :else
         (if (seq all-events)
           [:div
            [:h4 {:style {:font-size "0.875rem"            ; text-sm
                          :line-height "1.25rem"
                          :font-weight "600"               ; font-semibold
                          :color "#d1d5db"                 ; text-gray-300
                          :margin-bottom "0.5rem"}}        ; mb-2
             [:span "⚙️ Execution Timeline "]
             [:span (str "(" (count all-events) ")")]]
            [:div {:style {:display "flex"
                           :flex-direction "column"
                           :gap "0.25rem"}}                ; space-y-1
             (for [event all-events]
               ^{:key (:event-id event)}
               [event-item {:event event}])]]

           [:div {:style {:font-size "0.875rem"            ; text-sm
                          :line-height "1.25rem"
                          :color "#6b7280"                 ; text-gray-500
                          :text-align "center"
                          :margin-top "2rem"}}             ; text-center mt-8
            "No events captured."]))]]]))
