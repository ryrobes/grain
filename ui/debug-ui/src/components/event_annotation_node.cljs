(ns components.event-annotation-node
  "Custom React Flow annotation node for event emissions"
  (:require ["react" :as react]
            ["reactflow" :refer [Handle]]))

(defn safe-event-type
  "Safely extract event type as string"
  [event]
  (let [t (:type event)]
    (cond
      (keyword? t) (name t)
      (string? t) t
      :else (str t))))

(defn format-event-body
  "Format event body for display, limiting size"
  [body]
  (when body
    (try
      (let [json-str (js/JSON.stringify (clj->js body) nil 2)
            truncated (if (> (count json-str) 200)
                       (str (subs json-str 0 200) "...")
                       json-str)]
        truncated)
      (catch js/Error _
        (str body)))))

(defn event-annotation-node
  "Custom annotation node showing emitted events with purple styling.
   Plain React component for React Flow compatibility."
  [props]
  (let [data (.-data props)
        ;; Get events - might be JS array or ClojureScript vector
        events-raw (when data (aget data "events"))
        ;; Convert to ClojureScript if needed
        events (when events-raw
                 (if (array? events-raw)
                   (js->clj events-raw :keywordize-keys true)
                   events-raw))]

    (react/createElement "div"
      #js {:className "p-3 bg-purple-950 border border-purple-700 rounded-lg shadow-lg cursor-pointer hover:border-purple-500 transition-colors"
           :style #js {:minWidth "280px"
                      :maxWidth "360px"
                      :fontSize "11px"
                      :lineHeight "1.5"}}

      ;; Connection handle (left side to receive edge from main node)
      (react/createElement Handle
        #js {:type "target"
             :position "left"
             :id "left"
             :style #js {:background "#a78bfa"
                        :width "8px"
                        :height "8px"}})

      ;; Header
      (react/createElement "div"
        #js {:className "text-xs font-semibold text-purple-400 mb-2 flex items-center gap-2"}
        "EVENTS EMITTED"
        (react/createElement "span"
          #js {:className "bg-purple-800 text-purple-300 px-2 py-0.5 rounded-full text-xs"}
          (str (if events (count events) 0))))

      ;; Content
      (if (seq events)
        (react/createElement "div"
          #js {:className "space-y-1"}
          (into-array
           (map-indexed
            (fn [idx event]
              (react/createElement "div"
                #js {:key (or (:event/id event) (str "event-" idx))
                     :className "text-xs text-purple-300"}
                (str "• " (safe-event-type event))))
            events)))

        ;; No events
        (react/createElement "div"
          #js {:className "text-xs text-purple-500"}
          "No events emitted")))))
