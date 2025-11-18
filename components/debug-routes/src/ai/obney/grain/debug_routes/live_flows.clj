(ns ai.obney.grain.debug-routes.live-flows
  "HTTP endpoints for interactive live flows"
  (:require [ai.obney.grain.flow-session-manager.interface :as fsm]
            [ai.obney.grain.behavior-tree-v2-debug.interface :as debug]
            [hiccup2.core :as h]
            [cognitect.transit :as transit]
            [clojure.walk])
  (:import [java.util UUID]
           [java.io ByteArrayOutputStream]
           [java.time Instant]))

;;
;; Shared utilities (inlined to avoid circular dependency)
;;

(defn instant->string [^Instant inst]
  (str inst))

(defn prepare-for-transit [data]
  (clojure.walk/postwalk
   (fn [x]
     (cond
       (instance? Instant x) (instant->string x)
       :else x))
   data))

(defn serialize-transit [data]
  (let [prepared-data (prepare-for-transit data)
        out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer prepared-data)
    (.toString out "UTF-8")))

(def cors-headers
  {"Access-Control-Allow-Origin" "*"
   "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type"})

(def wizard-styles
  "* { box-sizing: border-box; }
   body { font-family: system-ui, -apple-system, sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; background: white; color: #1f2937; }
   h1, h2, h3 { margin: 0 0 1rem 0; font-weight: 600; color: #111827; }
   h1 { font-size: 2rem; }
   h2 { font-size: 1.5rem; }
   p { margin: 0 0 1rem 0; }
   .wizard-step { padding: 2rem; background: white; border-radius: 8px; }
   .progress-bar { background: #e5e7eb; height: 8px; border-radius: 4px; margin: 1.5rem 0; }
   .progress-bar .fill { background: #10b981; height: 100%; transition: width 0.3s; }
   button { padding: 0.625rem 1.25rem; border: none; border-radius: 6px; cursor: pointer; font-size: 1rem; font-weight: 500; }
   button.primary { background: #10b981; color: white; }
   button.secondary { background: #6b7280; color: white; margin-right: 0.5rem; }
   input, select { padding: 0.625rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; width: 100%; }
   label { display: block; margin: 1rem 0 0.375rem; font-weight: 500; }
   .errors { background: #fef2f2; color: #991b1b; padding: 1rem; border-radius: 6px; margin: 1rem 0; }
   form { margin: 1.5rem 0; }
   code { background: #f3f4f6; padding: 0.125rem 0.375rem; border-radius: 3px; }")

(defn current-view-data-handler
  "GET /flows/session/:session-id/current-view-data

  Returns Transit JSON with the currently active view's Hiccup.
  Used by debug UI for live rendering."
  [request]
  (try
    (let [session-id (-> request :path-params :session-id UUID/fromString)
          session (fsm/get-session session-id)]

      (if-not session
        {:status 404
         :headers {"Content-Type" "application/json"
                   "Access-Control-Allow-Origin" "*"}
         :body (serialize-transit {:error "Session not found"
                                  :session-id (str session-id)})}

        (let [;; Get current memory state (live!)
              st-memory (:st-memory session)
              current-memory @st-memory

              ;; Find view outputs
              view-outputs-key (keyword "ai.obney.grain.behavior-tree-v2.core.nodes" "view-outputs")
              all-view-outputs (get current-memory view-outputs-key)

              ;; Get session status
              session-status @(:status session)

              ;; Try to determine current view from execution state
              ;; For now, just return the most recently rendered view
              latest-view-id (last (keys all-view-outputs))
              latest-hiccup (get all-view-outputs latest-view-id)]

          {:status 200
           :headers {"Content-Type" "application/json"
                     "Access-Control-Allow-Origin" "*"}
           :body (serialize-transit {:hiccup latest-hiccup
                                    :view-id latest-view-id
                                    :session-id (str session-id)
                                    :session-status session-status
                                    :all-views (vec (keys all-view-outputs))})})))
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body (serialize-transit {:error (.getMessage e)})})))

(defn current-view-html-handler
  "GET /flows/session/:session-id/current-view

  Returns HTML for the currently active view.
  Can be used directly in browser or iframe."
  [request]
  (try
    (let [session-id (-> request :path-params :session-id UUID/fromString)
          session (fsm/get-session session-id)]

      (println "🌐 current-view-html-handler called")
      (println "  Session ID:" session-id)
      (println "  Session found?" (boolean session))

      (if-not session
        {:status 404
         :headers {"Content-Type" "text/html"}
         :body (str "<html><body style='font-family: monospace; padding: 2rem;'>"
                   "<h1>404 - Session not found</h1>"
                   "<p>Session ID: " session-id "</p>"
                   "<p>Active sessions: " (count (fsm/list-active-sessions)) "</p>"
                   "</body></html>")}

        (let [st-memory (:st-memory session)
              _ (println "  Memory atom exists?" (boolean st-memory))
              current-mem (when st-memory @st-memory)
              _ (println "  Memory keys:" (keys current-mem))

              view-outputs-key (keyword "ai.obney.grain.behavior-tree-v2.core.nodes" "view-outputs")
              all-view-outputs (get current-mem view-outputs-key)
              _ (println "  View outputs:" (keys all-view-outputs))

              latest-view-id (last (keys all-view-outputs))
              latest-hiccup (get all-view-outputs latest-view-id)
              _ (println "  Latest view ID:" latest-view-id)
              _ (println "  Latest hiccup exists?" (boolean latest-hiccup))]

          (if (and latest-view-id latest-hiccup)
            (let [layout (fn [content]
                          [:html
                           [:head
                            [:meta {:charset "UTF-8"}]
                            [:title (str "Live Flow: " latest-view-id)]
                            [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
                            [:style wizard-styles]]
                           [:body
                            [:div {:style {:background "#2563eb"
                                          :color "white"
                                          :padding "0.5rem 1rem"
                                          :font-size "0.875rem"}}
                             "🔴 LIVE - Interactive Flow Session: " (str session-id)]
                            content]])]

              {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"
                         "Access-Control-Allow-Origin" "*"}
               :body (str (h/html (layout latest-hiccup)))})

            {:status 404
             :headers {"Content-Type" "text/html"}
             :body (str "<html><body style='font-family: monospace; padding: 2rem;'>"
                       "<h1>No view rendered yet</h1>"
                       "<p>Session exists but no views have rendered.</p>"
                       "<p>Session status: " @(:status session) "</p>"
                       "<p>View outputs: " (pr-str (keys all-view-outputs)) "</p>"
                       "<p>Try refreshing in a moment...</p>"
                       "</body></html>")}))))
    (catch Exception e
      (println "❌ Error in current-view-html-handler:" (.getMessage e))
      (.printStackTrace e)
      {:status 500
       :headers {"Content-Type" "text/html"}
       :body (str "<html><body><h1>Error</h1><pre>" (.getMessage e) "\n\n"
                 (clojure.string/join "\n" (.getStackTrace e))
                 "</pre></body></html>")})))

(defn session-status-handler
  "GET /flows/session/:session-id/status

  Returns the current status of a flow session."
  [request]
  (let [session-id (-> request :path-params :session-id UUID/fromString)
        session (fsm/get-session session-id)]

    (if session
      {:status 200
       :headers {"Content-Type" "application/json"
                 "Access-Control-Allow-Origin" "*"}
       :body (serialize-transit {:session-id (str session-id)
                                :status @(:status session)
                                :flow-name (:flow-name session)
                                :started-at (str (:started-at session))})}
      {:status 404
       :headers {"Content-Type" "application/json"}
       :body (serialize-transit {:error "Session not found"})})))
