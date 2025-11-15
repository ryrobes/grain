(ns components.annotation-node
  "Custom React Flow annotation node with syntax-highlighted memory diffs"
  (:require ["react" :as react]
            ["reactflow" :refer [Handle]]))

(defn safe-key-name
  "Safely extract key name from change object"
  [change]
  (let [k (:key change)]
    (cond
      (keyword? k) (name k)
      (string? k) k
      :else (str k))))

(defn clean-value
  "Clean a value by removing ClojureScript internals and limiting depth"
  [val max-depth]
  (cond
    ;; Already at max depth - show summary
    (<= max-depth 0)
    (cond
      (map? val) (str "{" (count val) " keys}")
      (coll? val) (str "[" (count val) " items]")
      :else val)

    ;; Nil
    (nil? val) nil

    ;; Primitives - truncate strings to 40 chars max for canvas display
    (string? val)
    (if (> (count val) 40)
      (str (subs val 0 37) "...")
      val)

    (or (number? val) (boolean? val)) val

    ;; Keywords - convert to string
    (keyword? val) (name val)

    ;; Maps - clean recursively, filter out internals, limit to 3 keys for compactness
    (map? val)
    (into {}
          (comp
           ;; Filter out ClojureScript internals
           (filter (fn [[k _]]
                    (let [key-str (if (keyword? k) (name k) (str k))]
                      (not (or (clojure.string/starts-with? key-str "_")
                              (clojure.string/starts-with? key-str "cljs$")
                              (= key-str "meta"))))))
           ;; Take only first 3 keys for canvas compactness
           (take 3)
           ;; Clean values recursively
           (map (fn [[k v]]
                 [(if (keyword? k) (name k) k)
                  (clean-value v (dec max-depth))])))
          val)

    ;; Vectors/Lists - clean recursively, limit to 2 items
    (coll? val)
    (mapv #(clean-value % (dec max-depth)) (take 2 val))

    ;; Other - convert to string
    :else (str val)))

(defn changes-to-object
  "Convert array of changes to a clean object showing what changed"
  [changes]
  (reduce
   (fn [acc change]
     (let [key-name (safe-key-name change)
           new-val (:new-value change)
           ;; Clean the value to 1 level deep for more compact display
           cleaned (clean-value new-val 1)]
       (assoc acc key-name cleaned)))
   {}
   (take 2 changes)))  ; Limit to 2 changes for canvas compactness

(defn highlight-diff
  "Apply Prism syntax highlighting to memory diff"
  [changes]
  (when (and (seq changes) js/Prism)
    (try
      (let [;; Convert changes to clean object (not array!)
            changes-obj (changes-to-object changes)
            json-str (js/JSON.stringify (clj->js changes-obj) nil 2)
            highlighted (js/Prism.highlight
                         json-str
                         (aget (.-languages js/Prism) "json")
                         "json")]
        highlighted)
      (catch js/Error e
        (js/console.error "Prism highlighting failed:" e)
        nil))))

(defn format-change-plain
  "Format a single change as plain text (fallback)"
  [change]
  (let [key-name (safe-key-name change)
        old-val (:old-value change)
        new-val (:new-value change)
        cleaned (clean-value new-val 1)  ; Shallow clean for plain text
        format-val (fn [v]
                    (cond
                      (string? v) (str "\"" (subs v 0 (min 40 (count v)))
                                      (when (> (count v) 40) "...") "\"")
                      (map? v) (str "{" (count v) " keys}")
                      (coll? v) (str "[" (count v) " items]")
                      (nil? v) "null"
                      :else (str v)))]
    (if (nil? old-val)
      (str "➕ " key-name ": " (format-val cleaned))
      (str "📝 " key-name ": " (format-val cleaned)))))

(defn annotation-node
  "Custom annotation node showing memory diffs with syntax highlighting.
   Plain React component for React Flow compatibility."
  [props]
  (let [data (.-data props)
        ;; Get changes - might be JS array or ClojureScript vector
        changes-raw (when data (aget data "changes"))
        ;; Convert to ClojureScript if needed
        changes (when changes-raw
                 (if (array? changes-raw)
                   (js->clj changes-raw :keywordize-keys true)
                   changes-raw))]

    (react/createElement "div"
      #js {:className "p-3"
           :style #js {:minWidth "280px"
                      :maxWidth "360px"
                      :maxHeight "200px"
                      :fontSize "11px"
                      :lineHeight "1.5"
                      :overflow "hidden"}}

      ;; Connection handle (right side to receive edge from main node)
      (react/createElement Handle
        #js {:type "target"
             :position "right"
             :id "right"
             :style #js {:background "#a78bfa"
                        :width "8px"
                        :height "8px"}})

      ;; Header
      (react/createElement "div"
        #js {:className "text-xs font-semibold text-gray-400 mb-2"}
        "Memory Diff")

      ;; Content
      (if (seq changes)
        (let [total-changes (count changes)
              has-more? (> total-changes 2)
              highlighted (highlight-diff changes)]
          (react/createElement "div"
            #js {}

            ;; Show truncation indicator if there are more changes
            (when has-more?
              (react/createElement "div"
                #js {:style #js {:fontSize "9px"
                                :color "#a78bfa"
                                :opacity 0.7
                                :marginBottom "4px"
                                :fontStyle "italic"}}
                (str "Showing 2 of " total-changes " changes")))

            (if highlighted
              ;; Syntax-highlighted JSON
              (react/createElement "div"
                #js {:dangerouslySetInnerHTML
                     #js {:__html (str "<code class=\"language-json\">" highlighted "</code>")}
                     :style #js {:fontFamily "ui-monospace, 'Fira Code', monospace"
                                :fontSize "10px"
                                :maxHeight "150px"
                                :overflow "hidden"}})

              ;; Fallback: plain text with emojis
              (react/createElement "div"
                #js {:className "space-y-2 text-gray-300 font-mono"
                     :style #js {:fontSize "10px"
                                :whiteSpace "pre-wrap"
                                :maxHeight "150px"
                                :overflow "hidden"}}
                (clj->js
                 (map-indexed
                  (fn [idx change]
                    (react/createElement "div"
                      #js {:key idx}
                      (format-change-plain change)))
                  changes))))))

        ;; No changes
        (react/createElement "div"
          #js {:className "text-xs text-gray-500"}
          "No changes")))))
