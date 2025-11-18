(ns components.custom-node
  "Custom React Flow node component with rich formatting"
  (:require ["react" :as react]))

(defn custom-flow-node
  "Custom node showing name, duration, and memory change indicators.
   Plain React component for React Flow compatibility."
  [props]
  (let [data (.-data props)
        label (when data (aget data "label"))
        duration (when data (aget data "duration"))
        has-memory-changes (when data (aget data "hasMemoryChanges"))
        change-count (when data (aget data "changeCount"))
        node-type (when data (aget data "nodeType"))
        node-id (when data (aget data "nodeId"))
        trace-id (when data (aget data "traceId"))
        is-view-node (= node-type "view")]

    (react/createElement "div"
      #js {:className "p-3 relative"
           :style #js {:minWidth "180px"}}

      ;; View node indicator badge (top-left corner)
      (when is-view-node
        (react/createElement "div"
          #js {:className "absolute -top-1 -left-1 bg-purple-500 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm"
               :title "View node - click to preview"}
          ""))

      ;; Memory change indicator badge (top-right corner)
      (when has-memory-changes
        (react/createElement "div"
          #js {:className "absolute -top-1 -right-1 bg-blue-500 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs font-bold"
               :title (str change-count " memory change" (when (> change-count 1) "s"))}
          (str change-count)))

      ;; Main label
      (react/createElement "div"
        #js {:className "font-medium text-sm mb-1 flex items-center gap-2"}
        (or label "Unknown")
        ;; Small indicator if memory changed
        (when has-memory-changes
          (react/createElement "span"
            #js {:className "text-blue-400 text-xs"}
            "")))

      ;; Duration (if present)
      (when duration
        (react/createElement "div"
          #js {:className "text-xs text-gray-400 flex items-center gap-1"}
          (react/createElement "span" nil "⏱")
          (react/createElement "span"
            #js {:className "font-mono"}
            (str duration "ms"))))

      ;; View preview button (for view nodes)
      (when is-view-node
        (react/createElement "div"
          #js {:className "mt-2 pt-2 border-t border-gray-200"}
          (react/createElement "a"
            #js {:href (str "/debug/trace/" trace-id "/view/" node-id)
                 :target "_blank"
                 :className "text-xs bg-purple-500 hover:bg-purple-600 text-white px-2 py-1 rounded inline-flex items-center gap-1"
                 :onClick (fn [e] (.stopPropagation e))}
            (react/createElement "span" nil "")
            (react/createElement "span" nil "Preview")))))))
