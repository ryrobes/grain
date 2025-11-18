(ns ai.obney.grain.debug-routes.repl
  "HTTP-based REPL eval endpoint for debug UI.

   WARNING: This evaluates arbitrary code on the server. DEBUG ONLY!"
  (:require [cognitect.transit :as transit])
  (:import [java.io ByteArrayOutputStream StringWriter]))

(defn get-running-app
  "Try to get the running app instance from debug-example-base.core/app.
   Returns nil if not available.

   Note: app is stored in an atom, so we need to deref twice:
   - First @ gets the var's value (the atom)
   - Second @ gets the atom's value (the actual app map)"
  []
  (try
    (require 'ai.obney.grain.debug-example-base.core)
    (let [app-var (resolve 'ai.obney.grain.debug-example-base.core/app)]
      (if app-var
        (let [app-atom @app-var
              app-value @app-atom]
          (println "🔧 Debug: app-var found:" (boolean app-var))
          (println "🔧 Debug: app-atom type:" (type app-atom))
          (println "🔧 Debug: app-value type:" (type app-value))
          (println "🔧 Debug: app-value keys:" (keys app-value))
          (println "🔧 Debug: app-value empty?" (empty? app-value))
          (if (empty? app-value)
            (do
              (println "⚠️  App atom is empty! Did you start the server?")
              (println "   Try: (require '[ai.obney.grain.debug-example-base.core :as demo])")
              (println "        (reset! demo/app (demo/start))")
              nil)
            app-value))
        (do
          (println "⚠️  Could not resolve app var!")
          nil)))
    (catch Exception e
      (println "❌ Error getting app:" (.getMessage e))
      nil)))

(defn eval-code
  "Evaluate Clojure code string in the server context.

   Automatically binds:
   - `app` - the running Grain application (from debug-example-base)
   - `h` - helper namespace (ai.obney.grain.debug-example-base.helpers)

   WARNING: No sandboxing. Only use in debug/development!"
  [code-str]
  (try
    (let [output-writer (StringWriter.)
          form (read-string code-str)
          running-app (get-running-app)]

      ;; Load helper namespace if available
      (try
        (require 'ai.obney.grain.debug-example-base.helpers)
        (catch Exception _))

      ;; Set up user namespace with app and h bindings
      ;; We need to do this OUTSIDE the eval's binding so it persists
      (when running-app
        (intern 'user 'app running-app))

      (when (find-ns 'ai.obney.grain.debug-example-base.helpers)
        (binding [*ns* (find-ns 'user)]
          (eval '(require '[ai.obney.grain.debug-example-base.helpers :as h]))))

      ;; Capture both result and output
      (binding [*out* output-writer
                *ns* (find-ns 'user)]
        (let [;; Now eval user's code in the user namespace
              result (eval form)
              output (.toString output-writer)
              result-str (pr-str result)]

          {:status :success
           :result result-str
           :output (when-not (clojure.string/blank? output) output)
           :value result  ; The actual value (for Transit serialization)
           :type (str (type result))})))

    (catch Exception e
      {:status :error
       :error {:message (.getMessage e)
               :type (-> e class .getName)
               :stack-trace (take 10 (mapv str (.getStackTrace e)))}})))

(defn eval-handler
  "POST /debug/eval - Evaluate Clojure code.

   Request body (Transit JSON):
   {:code \"(+ 1 2)\"}

   Response (Transit JSON):
   {:status :success, :result \"3\", :output nil, :type \"java.lang.Long\"}
   or
   {:status :error, :error {:message \"...\", :type \"...\", :stack-trace [...]}}"
  [request]
  (let [code (get-in request [:transit-params :code])
        _ (println "🔧 Evaluating code:" code)
        result (eval-code code)]

    {:status 200
     :headers {"Content-Type" "application/transit+json"
               "Access-Control-Allow-Origin" "*"}
     :body (let [out (ByteArrayOutputStream.)
                 writer (transit/writer out :json)]
             (transit/write writer result)
             (.toString out "UTF-8"))}))
