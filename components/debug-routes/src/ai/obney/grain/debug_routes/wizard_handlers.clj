(ns ai.obney.grain.debug-routes.wizard-handlers
  "Direct HTTP handlers for wizard form submissions.

  These bypass the command processor and call handlers directly for simpler routing."
  (:require [ai.obney.grain.flow-session-manager.interface :as fsm]
            [ai.obney.grain.event-store-v2.interface :as es]
            [io.pedestal.http.body-params :as body-params])
  (:import [java.util UUID]))

(defn wizard-continue-handler
  "POST /wizard/continue - Handle 'Get Started' button"
  [request]
  (let [_ (println "🎬 wizard-continue-handler - DEBUG REQUEST")
        _ (println "   Request keys:" (keys request))
        _ (println "   :params:" (:params request))
        _ (println "   :form-params:" (:form-params request))
        _ (println "   :json-params:" (:json-params request))
        _ (println "   :body:" (type (:body request)))

        params (or (:form-params request) (:params request) {})
        _ (println "   Extracted params:" params)
        _ (println "   Params keys:" (keys params))

        session-id-str (or (get params "session-id")
                          (get params :session-id)
                          (get-in request [:params :session-id])
                          (get-in request [:form-params :session-id]))
        _ (println "   session-id-str:" session-id-str)

        session-id (when session-id-str (UUID/fromString session-id-str))
        session (when session-id (fsm/get-session session-id))]

    (println "   Final session ID:" session-id)
    (println "   Session found?" (boolean session))

    (if session
      (do
        ;; Set flag in session memory
        (swap! (:st-memory session) assoc :event-received/wizard/started true)

        ;; Wake up the flow
        (fsm/wake-session! session-id)

        (println "   Flag set, session woken")

        ;; Give flow time to advance
        (Thread/sleep 300)

        ;; Return HTML with meta refresh to show next view
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (str "<html><head>"
                   "<meta http-equiv='refresh' content='0; url=/flows/session/" session-id "/current-view'>"
                   "<style>body { font-family: system-ui; text-align: center; padding: 4rem; }</style>"
                   "</head><body>"
                   "<h2>⏭️ Loading next step...</h2>"
                   "<p>Please wait...</p>"
                   "</body></html>")})

      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>Session not found</h1></body></html>"})))

(defn wizard-submit-company-handler
  "POST /wizard/submit-company - Handle company info form"
  [request]
  (let [params (or (:form-params request) (:params request) {})
        ;; Handle both string and keyword keys
        session-id-str (or (get params "session-id") (get params :session-id))
        session-id (when session-id-str (UUID/fromString (str session-id-str)))
        session (when session-id (fsm/get-session session-id))

        company-data {:company-name (or (get params "company-name") (get params :company-name))
                     :industry (or (get params "industry") (get params :industry))
                     :employee-count (or (get params "employee-count") (get params :employee-count))}]

    (println "🎬 Company form submitted")
    (println "   Session ID:" session-id)
    (println "   Data:" company-data)

    (if session
      (do
        ;; Store data in session memory
        (swap! (:st-memory session) assoc :wizard/company-data company-data)

        ;; Set flag
        (swap! (:st-memory session) assoc :event-received/wizard/company-submitted true)

        ;; Wake up
        (fsm/wake-session! session-id)

        ;; Give time to advance (longer to ensure completion)
        (Thread/sleep 350)

        ;; Return HTML with meta refresh
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (str "<html><head>"
                   "<meta http-equiv='refresh' content='0; url=/flows/session/" session-id "/current-view'>"
                   "<style>body { font-family: system-ui; text-align: center; padding: 4rem; }</style>"
                   "</head><body>"
                   "<h2>⏭️ Loading next step...</h2>"
                   "</body></html>")})

      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>Session not found</h1></body></html>"})))

(defn wizard-submit-billing-handler
  "POST /wizard/submit-billing - Handle billing info form"
  [request]
  (let [params (or (:form-params request) (:params request) {})
        ;; Handle both string and keyword keys
        session-id-str (or (get params "session-id") (get params :session-id))
        session-id (when session-id-str (UUID/fromString (str session-id-str)))
        session (when session-id (fsm/get-session session-id))

        billing-data {:card-number (or (get params "card-number") (get params :card-number))
                     :expiry (or (get params "expiry") (get params :expiry))
                     :cvc (or (get params "cvc") (get params :cvc))}]

    (println "🎬 Billing form submitted")
    (println "   Session ID:" session-id)

    (if session
      (do
        ;; Store data
        (swap! (:st-memory session) assoc :wizard/billing-data billing-data)

        ;; Set flag
        (swap! (:st-memory session) assoc :event-received/wizard/billing-submitted true)

        ;; Wake up
        (fsm/wake-session! session-id)

        ;; Give time for processing tasks (300ms create + 200ms provision = 500ms+)
        (Thread/sleep 800)

        ;; Return HTML with meta refresh
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (str "<html><head>"
                   "<meta http-equiv='refresh' content='0; url=/flows/session/" session-id "/current-view'>"
                   "<style>body { font-family: system-ui; text-align: center; padding: 4rem; }</style>"
                   "</head><body>"
                   "<h2>🔧 Processing your account...</h2>"
                   "<p>Creating account and provisioning resources...</p>"
                   "</body></html>")})

      {:status 404
       :headers {"Content-Type" "text/html"}
       :body "<html><body><h1>Session not found</h1></body></html>"})))
