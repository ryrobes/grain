#!/usr/bin/env -S clj -M

;; Helper script to run debug commands via nREPL
;; Usage: clj -M run-debug-command.clj [command-name]

(require '[clojure.core.server :as server])

(defn run-command [command-name]
  (println "Connecting to nREPL on port 7888...")
  (let [socket (java.net.Socket. "localhost" 7888)
        out (java.io.PrintWriter. (.getOutputStream socket) true)
        in (java.io.BufferedReader. (java.io.InputStreamReader. (.getInputStream socket)))]

    ;; Send command to REPL
    (.println out (str "(require '[ai.obney.grain.command-processor.interface :as cp])"))
    (.println out (str "(require '[ai.obney.grain.time.interface :as time])"))
    (.println out (str "(def test-cmd {:command/name " command-name
                      " :command/id (random-uuid)"
                      " :command/timestamp (time/now)})"))
    (.println out (str "(cp/process-command (assoc (::demo/context app) :command test-cmd))"))

    (Thread/sleep 1000)
    (.close socket)
    (println "Command sent!")))

(let [command-name (or (first *command-line-args*) ":debug-example/simple-task")]
  (println "Running command:" command-name)
  (run-command command-name))
