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
        change-count (when data (aget data "changeCount"))]

    (react/createElement "div"
      #js {:className "p-3 relative"
           :style #js {:minWidth "180px"}}

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
            (str duration "ms")))))))
