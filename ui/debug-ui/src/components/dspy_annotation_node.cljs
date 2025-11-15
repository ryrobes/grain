(ns components.dspy-annotation-node
  "Custom React Flow annotation node for DSPy AI calls"
  (:require ["react" :as react]
            ["reactflow" :refer [Handle]]))

(defn dspy-annotation-node
  "Custom annotation node showing DSPy signature and reasoning with amber styling.
   Plain React component for React Flow compatibility."
  [props]
  (let [data (.-data props)
        ;; Get DSPy event data and convert to ClojureScript
        dspy-event-raw (when data (aget data "dspyEvent"))
        dspy-event (when dspy-event-raw
                     ;; ALWAYS convert to ClojureScript map
                     (js->clj dspy-event-raw :keywordize-keys true))

        ;; Extract signature info and reasoning
        body (:body dspy-event)
        node-id (get body :node-id)

        ;; Try multiple paths for reasoning
        reasoning-text (or (get body :reasoning)
                          (get-in body [:outputs :reasoning]))

        signature-name (cond
                         (= node-id :copilot) "PantryCopilot"
                         (= node-id "copilot") "PantryCopilot"
                         (string? node-id) node-id
                         :else "AI Signature")

        has-reasoning? (and reasoning-text (string? reasoning-text) (seq reasoning-text))]

    ;; Debug logging
    (js/console.log "🤖 DSPy annotation rendering")
    (js/console.log "  has-reasoning?:" has-reasoning?)
    (when has-reasoning?
      (js/console.log "  ✅ Reasoning found (" (count reasoning-text) "chars):" (subs reasoning-text 0 (min 80 (count reasoning-text))) "..."))
    (when (not has-reasoning?)
      (js/console.log "  ⚠️ No reasoning found!")
      (js/console.log "  body keys:" (clj->js (keys body)))
      (js/console.log "  body.reasoning:" (get body :reasoning))
      (js/console.log "  full dspy-event:" (clj->js dspy-event)))

    (react/createElement "div"
      #js {:className "p-3 rounded-lg shadow-lg cursor-pointer hover:border-emerald-400 transition-colors"
           :style #js {:minWidth "280px"
                      :maxWidth "360px"
                      :fontSize "11px"
                      :lineHeight "1.5"
                      :backgroundColor "#064e3b"
                      :border "2px solid #059669"
                      :color "#6ee7b7"}}

      ;; Connection handle (left side to receive edge from main node)
      (react/createElement Handle
        #js {:type "target"
             :position "left"
             :id "left"
             :style #js {:background "#6ee7b7"
                        :width "8px"
                        :height "8px"}})

      ;; Header
      (react/createElement "div"
        #js {:className "text-xs font-semibold mb-2 flex items-center gap-2"
             :style #js {:color "#6ee7b7"}}
        "🤖 DSPy Call"
        (react/createElement "span"
          #js {:className "px-2 py-0.5 rounded-full text-xs"
               :style #js {:backgroundColor "#059669"
                          :color "#6ee7b7"}}
          "CoT"))

      ;; Content
      (react/createElement "div"
        #js {:className "space-y-1"}

        ;; Reasoning text (truncated to ~120 chars / 2-3 lines)
        (if has-reasoning?
          (let [truncated (if (> (count reasoning-text) 120)
                           (str (subs reasoning-text 0 120) "...")
                           reasoning-text)]
            (react/createElement "div"
              #js {:className "text-xs leading-relaxed"
                   :style #js {:color "#86efac"
                              :fontStyle "italic"
                              :lineHeight "1.5"}}
              truncated))

          ;; Fallback if no reasoning
          (react/createElement "div"
            #js {:className "text-xs font-semibold"
                 :style #js {:color "#6ee7b7"}}
            signature-name))))))
