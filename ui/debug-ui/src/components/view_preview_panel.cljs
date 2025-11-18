(ns components.view-preview-panel
  "View preview panel - renders views directly (no iframe)"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [ajax.core :as ajax]
            [store.subs :as subs]
            [store.events :as events]))

(defn parse-inline-style
  "Parse inline style string to React style map.
   Example: 'width: 10%; color: red' -> {:width '10%' :color 'red'}"
  [style-str]
  (when (string? style-str)
    (try
      (let [pairs (clojure.string/split style-str #";")
            style-map (reduce
                       (fn [acc pair]
                         (let [trimmed (clojure.string/trim pair)]
                           (when-not (empty? trimmed)
                             (let [[k v] (clojure.string/split trimmed #":" 2)]
                               (when (and k v)
                                 (let [key-name (-> k
                                                   clojure.string/trim
                                                   (clojure.string/replace #"-([a-z])"
                                                                          (fn [[_ c]] (clojure.string/upper-case c))))
                                       value (clojure.string/trim v)]
                                   (assoc acc (keyword key-name) value))))))
                         acc)
                       {}
                       pairs)]
        style-map)
      (catch js/Error e
        (js/console.warn "Failed to parse style:" style-str e)
        {}))))

(defn transform-attrs
  "Transform attributes for React compatibility:
   - Convert HTMX attrs (hx-*) to data attributes (data-hx-*)
   - Convert style strings to style maps
   - Wrap seq children to avoid React key warnings"
  [attrs]
  (when (map? attrs)
    (reduce-kv
     (fn [acc k v]
       (cond
         ;; Transform style string to map
         (and (= k :style) (string? v))
         (assoc acc :style (parse-inline-style v))

         ;; Convert HTMX attributes to data-* attributes for React
         (and (keyword? k) (clojure.string/starts-with? (name k) "hx-"))
         (let [data-attr (keyword (str "data-" (name k)))]
           (assoc acc data-attr v))

         ;; Keep other attributes as-is
         :else
         (assoc acc k v)))
     {}
     attrs)))

(defn hiccup->reagent
  "Convert server-side Hiccup to Reagent-compatible format.
   Handles:
   - Style strings -> style maps
   - HTMX attributes -> data-* attributes
   - Wraps seqs to avoid React key warnings"
  [hiccup]
  (cond
    ;; Vector (Hiccup element)
    (vector? hiccup)
    (let [[tag attrs & children] (if (map? (second hiccup))
                                   hiccup
                                   (into [(first hiccup) {}] (rest hiccup)))
          transformed-attrs (transform-attrs attrs)
          transformed-children (map hiccup->reagent children)]
      (into [tag transformed-attrs] transformed-children))

    ;; Seq (multiple elements) - wrap to avoid React warnings
    (seq? hiccup)
    (into [:div {:style {:display "contents"}}]
          (map-indexed (fn [i child]
                        ^{:key i} [hiccup->reagent child])
                      hiccup))

    ;; Primitive (string, number, etc.)
    :else
    hiccup))

(defn fetch-view-data!
  "Fetch view data from backend"
  [preview-url api-base view-data loading? error]
  (when preview-url
    (let [url-match (re-matches #".*/debug/trace/([^/]+)/view/(.+)" preview-url)]
      (when url-match
        (let [trace-id (nth url-match 1)
              view-id (nth url-match 2)
              data-url (str api-base "/debug/trace/" trace-id "/view-data/" view-id)]
          (js/console.log "📡 Fetching view data from:" data-url)
          (reset! loading? true)
          (reset! error nil)
          (reset! view-data nil)
          (ajax/GET data-url
            {:format (ajax/transit-request-format)
             :response-format (ajax/transit-response-format)
             :handler (fn [response]
                        (js/console.log "✅ Got view data:" response)
                        (reset! view-data response)
                        (reset! loading? false))
             :error-handler (fn [err]
                             (js/console.error "❌ Error fetching view data:" err)
                             (reset! error (or (:response err) "Failed to fetch view"))
                             (reset! loading? false))}))))))

(defn view-preview-panel
  "Panel showing view preview rendered directly (no iframe)"
  []
  (let [view-data (r/atom nil)
        loading? (r/atom false)
        error (r/atom nil)
        last-url (r/atom nil)]

    (r/create-class
     {:component-did-mount
      (fn [_]
        (let [preview-url @(rf/subscribe [::subs/view-preview-url])
              api-base @(rf/subscribe [::subs/api-base])]
          (reset! last-url preview-url)
          (fetch-view-data! preview-url api-base view-data loading? error)))

      :component-did-update
      (fn [this]
        (let [preview-url @(rf/subscribe [::subs/view-preview-url])
              api-base @(rf/subscribe [::subs/api-base])]
          ;; Check if URL changed
          (when (not= @last-url preview-url)
            (js/console.log "🔄 Preview URL changed:" @last-url "→" preview-url)
            (reset! last-url preview-url)
            (fetch-view-data! preview-url api-base view-data loading? error))))

      :reagent-render
      (fn []
        (let [preview-url @(rf/subscribe [::subs/view-preview-url])]
        [:div {:style {:width "100%"
                       :height "100%"
                       :display "flex"
                       :flex-direction "column"
                       :background-color "#1e293b"}}
         ;; Header bar
         [:div {:style {:display "flex"
                        :align-items "center"
                        :justify-content "space-between"
                        :padding "0.5rem 1rem"
                        :background-color "#0f172a"
                        :border-bottom "1px solid #475569"}}
          [:div {:style {:display "flex"
                         :align-items "center"
                         :gap "0.5rem"}}
           [:span {:style {:font-size "1.25rem"}} "👁"]
           [:span {:style {:font-weight "600"
                           :color "#e9d5ff"}} "View Preview"]
           (when @view-data
             [:span {:style {:font-size "0.75rem"
                             :color "#94a3b8"
                             :font-family "ui-monospace"}}
              (str "View: " (:view-id @view-data))])
           [:a {:href preview-url
                :target "_blank"
                :style {:font-size "0.75rem"
                        :color "#a78bfa"
                        :margin-left "0.5rem"
                        :text-decoration "none"}
                :onMouseOver (fn [e] (set! (-> e .-target .-style .-textDecoration) "underline"))
                :onMouseOut (fn [e] (set! (-> e .-target .-style .-textDecoration) "none"))}
            "↗ Open in new tab"]]
          [:button {:on-click #(rf/dispatch [::events/close-view-preview])
                    :style {:background "none"
                            :border "none"
                            :color "#94a3b8"
                            :font-size "1.5rem"
                            :cursor "pointer"
                            :padding "0 0.5rem"}
                    :onMouseOver (fn [e] (set! (-> e .-target .-style .-color) "#f1f5f9"))
                    :onMouseOut (fn [e] (set! (-> e .-target .-style .-color) "#94a3b8"))}
           "×"]]

         ;; Content area
         [:div {:style {:flex "1"
                        :overflow "auto"
                        :padding "1rem"
                        :background-color "white"
                        :position "relative"}}
          (cond
            @loading?
            [:div {:style {:display "flex"
                           :align-items "center"
                           :justify-content "center"
                           :height "100%"
                           :color "#64748b"}}
             "⏳ Loading view..."]

            @error
            [:div {:style {:padding "2rem"
                           :color "#dc2626"
                           :background-color "#fef2f2"
                           :border-radius "0.5rem"}}
             [:h3 "Error loading view"]
             [:pre {:style {:margin-top "1rem"
                            :font-size "0.875rem"}}
              (pr-str @error)]]

            @view-data
            (let [hiccup (:hiccup @view-data)]
              ;; CSS Reset wrapper to prevent debug UI styles from bleeding in
              [:div {:style {:max-width "800px"
                             :margin "0 auto"
                             ;; Reset all inherited styles
                             :all "initial"
                             :font-family "system-ui, -apple-system, sans-serif"
                             :color "#000"
                             :line-height "1.5"}}
               ;; Debug banner
               [:div {:style {:background-color "#ff9800"
                              :color "white"
                              :padding "0.5rem 1rem"
                              :margin-bottom "1rem"
                              :font-size "0.875rem"
                              :font-weight "600"
                              :border-radius "0.25rem"}}
                "🔍 Debug View Preview (Direct Render)"]
               ;; Render the Hiccup directly as Reagent with fresh styles
               [:div {:style {:all "initial"
                              :display "block"
                              :font-family "system-ui, -apple-system, sans-serif"
                              :color "#000"
                              :line-height "1.5"}}
                (hiccup->reagent hiccup)]])

            :else
            [:div {:style {:display "flex"
                           :align-items "center"
                           :justify-content "center"
                           :height "100%"
                           :color "#64748b"}}
             "No view selected"])]]))})))
