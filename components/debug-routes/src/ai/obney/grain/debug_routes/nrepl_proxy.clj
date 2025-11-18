(ns ai.obney.grain.debug-routes.nrepl-proxy
  "WebSocket proxy to nREPL server for browser-based REPL.

   Connects browser (WebSocket) ↔ nREPL (TCP socket with bencode protocol)."
  (:require [clojure.core.async :as async]
            [bencode.core :as bencode])
  (:import [java.net Socket]
           [java.io InputStream OutputStream PushbackInputStream]))

(defn connect-to-nrepl
  "Open TCP connection to nREPL server"
  [host port]
  (try
    (let [socket (Socket. host port)]
      {:socket socket
       :input-stream (PushbackInputStream. (.getInputStream socket))
       :output-stream (.getOutputStream socket)})
    (catch Exception e
      (println "Failed to connect to nREPL:" (.getMessage e))
      nil)))

(defn read-bencode-message
  "Read one bencode message from input stream"
  [^PushbackInputStream input-stream]
  (try
    (bencode/read-bencode input-stream)
    (catch Exception e
      nil)))

(defn write-bencode-message
  "Write bencode message to output stream"
  [^OutputStream output-stream message]
  (try
    (bencode/write-bencode output-stream message)
    (.flush output-stream)
    true
    (catch Exception e
      (println "Failed to write bencode:" (.getMessage e))
      false)))

(defn nrepl-proxy-handler
  "WebSocket handler that proxies to nREPL server.

   Creates bidirectional message flow:
   Browser (WebSocket) ↔ This Handler ↔ nREPL (TCP Socket)"
  [request]
  (let [ws-ch (async/chan 100)  ; WebSocket messages from browser
        nrepl-conn (connect-to-nrepl "localhost" 7888)]

    (if-not nrepl-conn
      ;; Failed to connect to nREPL
      {:status 503
       :headers {"Content-Type" "text/plain"}
       :body "nREPL server not available on port 7888"}

      ;; Connected - start proxying
      (let [{:keys [socket input-stream output-stream]} nrepl-conn]

        ;; Thread 1: Read from nREPL, send to browser via WebSocket
        (async/thread
          (try
            (loop []
              (when-let [response (read-bencode-message input-stream)]
                ;; Convert bencode response to EDN for browser
                (async/>!! ws-ch (pr-str response))
                (recur)))
            (catch Exception e
              (println "nREPL read error:" (.getMessage e)))
            (finally
              (async/close! ws-ch)
              (.close socket))))

        ;; Thread 2: Read from WebSocket, send to nREPL
        (async/go-loop []
          (when-let [msg (async/<! ws-ch)]
            (try
              ;; Browser sends EDN strings, convert to bencode for nREPL
              (let [edn-msg (read-string msg)]
                (write-bencode-message output-stream edn-msg))
              (catch Exception e
                (println "WebSocket message error:" (.getMessage e))))
            (recur)))

        ;; Return WebSocket response
        {:status 200
         :headers {"Content-Type" "text/event-stream"
                   "Upgrade" "websocket"
                   "Connection" "Upgrade"}
         :body ws-ch}))))
