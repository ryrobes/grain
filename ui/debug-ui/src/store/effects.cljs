(ns store.effects
  "Re-frame effects for API calls"
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [cognitect.transit :as transit]))

;; Read API base from window config, fallback to localhost
(def api-base
  (or (when (exists? js/window.GRAIN_DEBUG_CONFIG)
        (.-apiBase js/window.GRAIN_DEBUG_CONFIG))
      "http://localhost:8081"))

;;
;; Fetch traces
;;

(rf/reg-fx
 :fetch-traces
 (fn [{:keys [on-success on-failure limit command-name status]}]
   (let [params (cond-> {}
                  limit (assoc "limit" limit)
                  command-name (assoc "command-name" (name command-name))
                  status (assoc "status" (name status)))]
     (ajax/GET (str api-base "/debug/traces")
       {:params params
        :response-format (ajax/transit-response-format)
        :handler #(rf/dispatch (conj on-success %))
        :error-handler #(rf/dispatch (conj on-failure (get-in % [:response :error] "Unknown error")))}))))

;;
;; Fetch Single Trace
;;

(rf/reg-fx
 :fetch-trace
 (fn [{:keys [trace-id on-success on-failure]}]
   (ajax/GET (str api-base "/debug/trace/" trace-id)
     {:response-format (ajax/transit-response-format)
      :handler #(rf/dispatch (conj on-success %))
      :error-handler #(rf/dispatch (conj on-failure (get-in % [:response :error] "Unknown error")))})))

;;
;; SSE Connection
;;

;; Transit reader for SSE messages
(def transit-reader
  (transit/reader :json))

;; Track SSE connection for auto-reconnect
(defonce sse-source (atom nil))
(defonce reconnect-attempt (atom 0))

(defn connect-sse-with-retry
  "Connect to SSE with automatic reconnection"
  [{:keys [on-connected on-event on-error] :as opts}]
  (let [attempt @reconnect-attempt
        delay-ms (* 1000 (min 30 (Math/pow 2 attempt)))  ; Exponential backoff, max 30s

        connect-fn
        (fn []
          (js/console.log "Connecting to SSE (attempt" (inc attempt) ")...")
          (let [source (js/EventSource. (str api-base "/debug/stream"))]

            (reset! sse-source source)

            ;; Connection opened
            (.addEventListener source "open"
              (fn [_]
                (js/console.log "SSE connected!")
                (reset! reconnect-attempt 0)  ; Reset backoff on success
                (rf/dispatch on-connected)))

            ;; Message received
            (.addEventListener source "message"
              (fn [e]
                (try
                  ;; Parse Transit format
                  (let [data (transit/read transit-reader (.-data e))
                        event-type (:event-type data)]
                    ;; Debug logging for execution events
                    (when (= event-type :execution-event)
                      (when (zero? (mod (rand-int 100) 20))  ; Log ~20% of execution events
                        (js/console.log "⚡ SSE execution-event received, size:" (.-length (.-data e)) "bytes")))
                    (rf/dispatch (conj on-event data)))
                  (catch js/Error err
                    (js/console.error "Failed to parse SSE message:" err)
                    (js/console.error "Message data:" (.-data e))))))

            ;; Error occurred - auto-reconnect
            (.addEventListener source "error"
              (fn [e]
                (js/console.warn "SSE connection lost, will retry in" delay-ms "ms")
                (.close source)
                (reset! sse-source nil)
                (rf/dispatch (conj on-error "Disconnected"))

                ;; Schedule reconnection
                (swap! reconnect-attempt inc)
                (js/setTimeout
                 #(connect-sse-with-retry opts)
                 delay-ms)))))]

    ;; Initial connection or retry
    (if (zero? attempt)
      (connect-fn)
      (js/setTimeout connect-fn delay-ms))))

(rf/reg-fx
 :connect-sse
 connect-sse-with-retry)
