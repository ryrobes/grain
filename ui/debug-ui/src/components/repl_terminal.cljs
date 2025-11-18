(ns components.repl-terminal
  "Browser-based REPL terminal using xterm.js.

   Connects to /debug/eval endpoint to evaluate code on the server."
  (:require [reagent.core :as r]
            [ajax.core :as ajax]
            [cognitect.transit :as transit]))

(defonce command-history (r/atom []))
(defonce history-index (r/atom -1))
(defonce current-input (r/atom ""))

;; Load command history from localStorage on startup
(defn load-history! []
  (try
    (when-let [stored (.getItem js/localStorage "grain-repl-history")]
      (let [parsed (js/JSON.parse stored)
            history (js->clj parsed)]
        (reset! command-history (vec history))
        (js/console.log "📚 Loaded" (count history) "commands from history")))
    (catch js/Error e
      (js/console.warn "Could not load history:" e))))

;; Save command history to localStorage
(defn save-history! []
  (try
    (let [json (js/JSON.stringify (clj->js @command-history))]
      (.setItem js/localStorage "grain-repl-history" json))
    (catch js/Error e
      (js/console.warn "Could not save history:" e))))

(defn eval-code!
  "Send code to server for evaluation"
  [code terminal]
  (js/console.log "📤 Sending to /debug/eval:" code)
  (ajax/POST (str (.-apiBase js/window.GRAIN_DEBUG_CONFIG) "/debug/eval")
    {:params {:code code}
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :handler (fn [response]
                (js/console.log "📥 Got response:" response)
                (if (= (:status response) :success)
                  (do
                    ;; Print output if any
                    (when-let [output (:output response)]
                      (.writeln terminal output))
                    ;; Print result
                    (.writeln terminal (str "=> " (:result response))))
                  ;; Print error
                  (do
                    (.writeln terminal (str "\r\n\u001b[31mError: " (get-in response [:error :message]) "\u001b[0m"))
                    (when-let [trace (get-in response [:error :stack-trace])]
                      (doseq [line (take 5 trace)]
                        (.writeln terminal (str "  " line)))))))
     :error-handler (fn [error]
                     (js/console.log "📛 AJAX error:" error)
                     (.writeln terminal (str "\r\n\u001b[31mREPL connection error\u001b[0m")))}))

(defn repl-terminal []
  ;; Load history on first mount
  (when (empty? @command-history)
    (load-history!))

  (let [terminal-ref (r/atom nil)
        paste-textarea-ref (r/atom nil)
        show-history? (r/atom false)

        process-command
        (fn [input terminal]
          (js/console.log "⌨️ Processing command:" input)
          (when-not (clojure.string/blank? input)
            ;; Add to history and persist
            (swap! command-history conj input)
            (save-history!)
            (reset! history-index -1)

            ;; Show prompt with command
            (.writeln terminal (str "user=> " input))

            ;; Eval on server
            (eval-code! input terminal)

            ;; Reset input
            (reset! current-input "")

            ;; Show new prompt
            (.write terminal "\r\nuser=> ")))

        handle-key
        (fn [terminal e]
          (let [key (.-key e)
                dom-event (.-domEvent ^js e)
                char-code (when (seq key) (.charCodeAt key 0))]
            (js/console.log "🔑 Key:" (pr-str key) "| code:" char-code "| length:" (count key))

            (cond
              ;; Enter - check for carriage return \r (char code 13)
              (= char-code 13)
              (do
                (js/console.log "↵ Enter pressed! Current input:" @current-input)
                (.preventDefault dom-event)
                (process-command @current-input terminal)
                (reset! current-input ""))

              ;; Backspace/Delete - char code 127 (DEL) or 8 (BS), or key "Backspace"
              (or (= char-code 127) (= char-code 8) (= key "Backspace"))
              (when (seq @current-input)
                (js/console.log "⌫ Backspace")
                (.preventDefault dom-event)
                (swap! current-input #(subs % 0 (dec (count %))))
                ;; Proper backspace: move back, space, move back
                (.write terminal "\u0008 \u0008"))

              ;; Up arrow - previous history
              (= key "ArrowUp")
              (do
                (.preventDefault dom-event)
                (when (seq @command-history)
                  (let [new-index (min (dec (count @command-history))
                                      (inc @history-index))]
                    (reset! history-index new-index)
                    (let [cmd (get @command-history (- (count @command-history) new-index 1))]
                      ;; Clear current line
                      (.write terminal (str "\r\u001b[K" "user=> "))
                      ;; Write history command
                      (.write terminal cmd)
                      (reset! current-input cmd)))))

              ;; Down arrow - next history
              (= key "ArrowDown")
              (do
                (.preventDefault dom-event)
                (if (>= @history-index 0)
                  (let [new-index (dec @history-index)]
                    (reset! history-index new-index)
                    (if (< new-index 0)
                      (do
                        (.write terminal (str "\r\u001b[K" "user=> "))
                        (reset! current-input ""))
                      (let [cmd (get @command-history (- (count @command-history) new-index 1))]
                        (.write terminal (str "\r\u001b[K" "user=> "))
                        (.write terminal cmd)
                        (reset! current-input cmd))))))

              ;; Ctrl+V - paste from clipboard
              (and (.-ctrlKey dom-event) (= key "v"))
              (do
                (js/console.log "📋 Ctrl+V detected, attempting paste...")
                (.preventDefault dom-event)
                (if (and js/navigator js/navigator.clipboard)
                  (-> (js/navigator.clipboard.readText)
                      (.then (fn [text]
                               (js/console.log "✅ Pasted text:" (pr-str text))
                               (when (seq text)
                                 (swap! current-input str text)
                                 (.write terminal text))))
                      (.catch (fn [err]
                                (js/console.error "❌ Clipboard API failed:" err)
                                (js/console.log "💡 Try using browser's native paste or clicking history items"))))
                  (js/console.warn "⚠️  Clipboard API not available")))

              ;; Regular character - add to input
              (and (= 1 (count key)) (not (.-ctrlKey dom-event)))
              (do
                (.preventDefault dom-event)  ; Prevent xterm from echoing it too!
                (swap! current-input str key)
                (.write terminal key)))))]

    (let [container-ref (r/atom nil)
          paste-command (fn [cmd]
                          (js/console.log "📝 Pasting command from history:" cmd)
                          (when-let [term @terminal-ref]
                            ;; Clear current line
                            (.write term (str "\r\u001b[K" "user=> "))
                            ;; Write command
                            (.write term cmd)
                            (reset! current-input cmd)
                            (js/console.log "✅ Command pasted")))]
      (r/create-class
       {:component-did-mount
        (fn [_]
          (when-let [container-el @container-ref]
            (let [term (new (.-Terminal js/window) #js {:theme #js {:background "#0f172a"
                                                                  :foreground "#e2e8f0"
                                                                  :cursor "#fcd34d"
                                                                  :cursorAccent "#0f172a"
                                                                  :selection "rgba(252, 211, 77, 0.3)"
                                                                  :black "#1e293b"
                                                                  :red "#fca5a5"
                                                                  :green "#6ee7b7"
                                                                  :yellow "#fcd34d"
                                                                  :blue "#7dd3fc"
                                                                  :magenta "#d8b4fe"
                                                                  :cyan "#a7f3d0"
                                                                  :white "#e2e8f0"}
                                                     :fontFamily "'Fira Code', 'Consolas', 'Monaco', monospace"
                                                     :fontSize 13
                                                     :fontWeight "400"
                                                     :letterSpacing 0
                                                     :lineHeight 1.2
                                                     :cursorBlink true})
              ]

          (reset! terminal-ref term)

          ;; Open terminal in container
          (.open term container-el)

          ;; Create hidden textarea for paste support
          (let [paste-input (.createElement js/document "textarea")]
            ;; Store reference for cleanup
            (reset! paste-textarea-ref paste-input)

            ;; Style it to be truly invisible and not interfere with layout
            (set! (.-style paste-input)
                  (clj->js {:position "fixed"
                            :left "-9999px"
                            :top "-9999px"
                            :width "1px"
                            :height "1px"
                            :opacity "0"
                            :pointer-events "none"
                            :z-index "-1"}))
            (.appendChild js/document.body paste-input)  ; Append to body, not container

            ;; Handle paste events on the textarea
            (.addEventListener paste-input "paste"
                             (fn [e]
                               (.preventDefault e)
                               (let [text (-> e .-clipboardData (.getData "text"))]
                                 (js/console.log "📋 Pasted via textarea:" (pr-str text))
                                 (when (seq text)
                                   (swap! current-input str text)
                                   (.write term text)))))

            ;; Keep textarea focused when clicking in terminal area
            (.addEventListener container-el "click"
                             (fn [_]
                               (js/console.log "🖱️ Terminal clicked, focusing paste textarea")
                               (.focus paste-input)))

            ;; Focus the textarea initially
            (js/setTimeout
             #(do
                (js/console.log "⚡ Initial focus on paste textarea")
                (.focus paste-input))
             150))

          ;; Use FitAddon to properly size terminal to container
          (js/console.log "🔧 Terminal container dimensions:"
                         (.-offsetWidth container-el)
                         "x"
                         (.-offsetHeight container-el))

          (let [show-welcome
                (fn []
                  ;; Welcome message
                  (.writeln term "\u001b[33m╔════════════════════════════════════════╗\u001b[0m")
                  (.writeln term "\u001b[33m║\u001b[0m  \u001b[1mGrain Debug REPL\u001b[0m                   \u001b[33m║\u001b[0m")
                  (.writeln term "\u001b[33m║\u001b[0m  Connected to server eval           \u001b[33m║\u001b[0m")
                  (.writeln term "\u001b[33m╚════════════════════════════════════════╝\u001b[0m")
                  (.writeln term "")
                  (.writeln term "Type Clojure expressions and press Enter")
                  (.writeln term "Try: (+ 1 2 3)")
                  (.writeln term "")
                  ;; Initial prompt
                  (.write term "user=> "))]

            (try
              (let [FitAddonClass (-> js/window .-FitAddon .-FitAddon)]
                (if FitAddonClass
                  (let [fit-addon (new FitAddonClass)]
                    (js/console.log "✅ FitAddon loaded, fitting terminal to container")
                    (.loadAddon ^js term fit-addon)

                    ;; Wait for CSS animation (300ms transition + 50ms buffer)
                    (js/setTimeout
                     #(do
                        (js/console.log "🔧 Container after animation:"
                                       (.-offsetWidth container-el) "x"
                                       (.-offsetHeight container-el))
                        (.fit fit-addon)
                        (js/console.log "📐 Terminal sized to:"
                                       (.-cols term) "cols x" (.-rows term) "rows")
                        (show-welcome))
                     350)

                    ;; Set up ResizeObserver to re-fit on container resize
                    (when (exists? js/window.ResizeObserver)
                      (let [observer (js/ResizeObserver.
                                     (fn [_entries]
                                       (js/console.log "📏 Container resized, re-fitting...")
                                       (.fit fit-addon)))]
                        (.observe observer container-el))))
                  (do
                    (js/console.warn "⚠️  FitAddon class not found, using fallback sizing")
                    (.resize term 80 24)
                    (show-welcome))))
              (catch js/Error e
                (js/console.error "❌ Error with FitAddon:" e)
                (.resize term 80 24)
                (show-welcome))))

          ;; Handle all key input (including Ctrl+V paste)
          (.onKey ^js term (fn [e] (handle-key term e))))))

        :component-will-unmount
        (fn [_]
          ;; Clean up terminal
          (when-let [term @terminal-ref]
            (.dispose ^js term))
          ;; Clean up paste textarea
          (when-let [paste-textarea @paste-textarea-ref]
            (js/console.log "🧹 Cleaning up paste textarea")
            (.remove paste-textarea)))

        :reagent-render
        (fn [_]
          [:div {:style {:width "100%"
                         :height "100%"
                         :display "flex"
                         :background-color "#0f172a"}}
           ;; History sidebar (left)
           [:div {:style {:width "300px"
                          :height "100%"
                          :background-color "#1e293b"
                          :border-right "1px solid #475569"
                          :overflow-y "auto"
                          :flex-shrink 0}}
            [:div {:style {:padding "12px"
                           :border-bottom "1px solid #475569"
                           :background-color "#0f172a"}}
             [:div {:style {:display "flex"
                            :justify-content "space-between"
                            :align-items "center"
                            :margin-bottom "8px"}}
              [:div {:style {:font-size "14px"
                             :font-weight "bold"
                             :color "#fcd34d"}}
               "📚 Command History"]
              [:button {:on-click #(do
                                     (reset! command-history [])
                                     (.removeItem js/localStorage "grain-repl-history"))
                        :style {:padding "4px 8px"
                                :font-size "10px"
                                :background-color "#450a0a"
                                :color "#fca5a5"
                                :border "1px solid #7f1d1d"
                                :border-radius "3px"
                                :cursor "pointer"}}
               "Clear"]]
             [:div {:style {:font-size "11px"
                            :color "#94a3b8"}}
              (str (count @command-history) " commands • click to paste")]]

            ;; History list (reverse chronological)
            [:div {:style {:padding "8px"}}
             (doall
              (for [[idx cmd] (map-indexed vector (reverse @command-history))]
                [:div {:key idx
                       :on-click (fn [e]
                                   (.stopPropagation e)
                                   (paste-command cmd))
                       :style {:padding "8px"
                               :margin-bottom "4px"
                               :background-color "#0f172a"
                               :border "1px solid #334155"
                               :border-radius "4px"
                               :cursor "pointer"
                               :font-family "'Fira Code', monospace"
                               :font-size "11px"
                               :color "#e2e8f0"
                               :transition "all 0.15s"
                               :overflow "hidden"
                               :text-overflow "ellipsis"
                               :white-space "nowrap"}
                       :on-mouse-enter (fn [e]
                                         (set! (-> e .-target .-style .-backgroundColor) "#1e3a5f")
                                         (set! (-> e .-target .-style .-borderColor) "#3b82f6"))
                       :on-mouse-leave (fn [e]
                                         (set! (-> e .-target .-style .-backgroundColor) "#0f172a")
                                         (set! (-> e .-target .-style .-borderColor) "#334155"))}
                 [:div {:style {:color "#64748b"
                                :font-size "10px"
                                :margin-bottom "4px"}}
                  (str "#" (- (count @command-history) idx))]
                 cmd]))]]  ; close history list and sidebar

           ;; Terminal (right)
           [:div {:ref #(when % (reset! container-ref %))
                  :style {:flex "1"
                          :height "100%"
                          :background-color "#0f172a"
                          :padding "8px"}}]])}))))  ; close terminal, main container, fn, create-class, let, defn
