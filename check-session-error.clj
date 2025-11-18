(require '[ai.obney.grain.flow-session-manager.interface :as fsm])

(println "\n=== Session Debug ===")
(def sessions (fsm/list-active-sessions))

(when (seq sessions)
  (def session (first sessions))
  (println "Session ID:" (:session-id session))
  (println "Status:" @(:status session))
  (println "Flow name:" (:flow-name session))

  ;; Check the execution future
  (when-let [fut (:execution-future session)]
    (println "\nExecution future exists?" (boolean fut))
    (println "Future done?" (future-done? fut))

    (when (future-done? fut)
      (println "\nFuture result:")
      (try
        (def result @fut)
        (clojure.pprint/pprint result)
        (catch Exception e
          (println "ERROR in future:")
          (println (.getMessage e))
          (.printStackTrace e))))))
