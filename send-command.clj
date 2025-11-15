#!/usr/bin/env -S clj -Sdeps '{:deps {com.cognitect/transit-clj {:mvn/version "1.0.333"}}}' -M

;; Send a command to the debug example server using proper Transit encoding
;; Usage: clj -Sdeps '{:deps {com.cognitect/transit-clj {:mvn/version "1.0.333"}}}' -M send-command.clj debug-example/simple-task

(require '[clojure.java.io :as io]
         '[cognitect.transit :as transit])
(import '[java.net HttpURLConnection URL]
        '[java.io ByteArrayOutputStream])

(defn send-transit-command [command-name]
  (let [url (URL. "http://localhost:8080/command")
        conn ^HttpURLConnection (.openConnection url)]

    ;; Setup connection
    (.setRequestMethod conn "POST")
    (.setRequestProperty conn "Content-Type" "application/transit+json")
    (.setDoOutput conn true)

    ;; Create Transit body - command map needs namespaced keys!
    (let [out (ByteArrayOutputStream.)
          writer (transit/writer out :json)
          body {:command {:command/name (keyword command-name)}}]
      (transit/write writer body)
      (let [body-bytes (.toByteArray out)
            body-str (String. body-bytes)]
        (println "Sending Transit body:" body-str)
        (with-open [os (.getOutputStream conn)]
          (.write os body-bytes))))

    ;; Get response
    (let [response-code (.getResponseCode conn)]
      (println "Response code:" response-code)
      (if (= 200 response-code)
        (with-open [in (.getInputStream conn)]
          (let [reader (transit/reader in :json)
                result (transit/read reader)]
            (println "✅ Success!")
            (println "Result:" result)))
        (with-open [err (.getErrorStream conn)]
          (println "❌ Error:")
          (println (slurp err)))))))

(let [command-name (or (first *command-line-args*) "debug-example/simple-task")]
  (println "🚀 Sending command:" command-name)
  (send-transit-command command-name)
  (println "\n💡 Check debug UI at http://localhost:8082"))

(System/exit 0)
