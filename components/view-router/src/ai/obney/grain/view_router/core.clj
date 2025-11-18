(ns ai.obney.grain.view-router.core
  "Core view routing and rendering for Grain views"
  (:require [hiccup2.core :as h]
            [clojure.walk :as walk]))

(defn extract-view-nodes
  "Walk tree and extract all :view nodes with their metadata.

  Returns: Vector of view node maps with :node-id, :route, :render-fn, etc."
  [tree]
  (let [view-nodes (atom [])]
    (walk/postwalk
     (fn [node]
       (when (and (map? node) (= :view (:type node)))
         (swap! view-nodes conj node))
       node)
     tree)
    @view-nodes))

(defn generate-route-from-id
  "Generate conventional route from node ID and flow name.

  Example: :welcome + :account-setup -> /flows/account-setup/welcome"
  [node-id flow-name]
  (str "/flows/" (name flow-name) "/" (name node-id)))

(defn generate-routes
  "Generate HTTP routes from behavior tree view nodes.

  Args:
    tree-def - Behavior tree structure (before build)
    flow-name - Keyword name of the flow

  Returns: Vector of route maps with :path, :node-id, :render-fn"
  [tree-def flow-name]
  (let [view-nodes (extract-view-nodes tree-def)]
    (for [node view-nodes
          :let [node-id (get-in node [:opts :id])
                route (or (get-in node [:opts :route])
                         (generate-route-from-id node-id flow-name))]]
      {:method :get
       :path route
       :node-id node-id
       :render-fn (:render-fn node)
       :opts (:opts node)})))

(defn default-layout
  "Default HTML layout wrapper with comprehensive styles"
  [content {:keys [title] :or {title "Grain Flow"}}]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    [:title title]
    ;; HTMX
    [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
    ;; Comprehensive styles
    [:style
     "* { box-sizing: border-box; }
      body { font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; background: white; color: #1f2937; line-height: 1.6; }
      h1, h2, h3, h4, h5, h6 { margin: 0 0 1rem 0; font-weight: 600; line-height: 1.2; color: #111827; }
      h1 { font-size: 2rem; }
      h2 { font-size: 1.5rem; }
      h3 { font-size: 1.25rem; }
      p { margin: 0 0 1rem 0; }
      ul, ol { margin: 0 0 1rem 0; padding-left: 1.5rem; }
      .wizard-step { padding: 2rem; background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
      .wizard-step.complete { text-align: center; }
      .progress-bar { background: #e5e7eb; height: 8px; border-radius: 4px; margin: 1.5rem 0; overflow: hidden; }
      .progress-bar .fill { background: linear-gradient(90deg, #10b981, #059669); height: 100%; border-radius: 4px; transition: width 0.3s ease; }
      button, .button { padding: 0.625rem 1.25rem; border: none; border-radius: 6px; cursor: pointer; font-size: 1rem; text-decoration: none; display: inline-block; font-weight: 500; transition: all 0.2s; }
      button:hover, .button:hover { transform: translateY(-1px); box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
      button.primary, .button.primary { background: #10b981; color: white; }
      button.primary:hover, .button.primary:hover { background: #059669; }
      button.secondary, .button.secondary { background: #6b7280; color: white; margin-right: 0.5rem; }
      button.secondary:hover, .button.secondary:hover { background: #4b5563; }
      button[type=submit] { margin-left: auto; }
      input, select { padding: 0.625rem 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 1rem; margin: 0.25rem 0; width: 100%; font-family: inherit; transition: border-color 0.2s; }
      input:focus, select:focus { outline: none; border-color: #10b981; box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1); }
      input::placeholder { color: #9ca3af; }
      label { display: block; margin: 1rem 0 0.375rem; font-weight: 500; color: #374151; font-size: 0.875rem; }
      .errors { background: #fef2f2; color: #991b1b; padding: 1rem; border-radius: 6px; margin: 1rem 0; border-left: 4px solid #dc2626; }
      .error { margin: 0.375rem 0; font-size: 0.875rem; }
      form { margin: 1.5rem 0; }
      form > div { margin-bottom: 0.5rem; }
      code { background: #f3f4f6; padding: 0.125rem 0.375rem; border-radius: 3px; font-family: 'Courier New', monospace; font-size: 0.875em; color: #1f2937; }
      strong { font-weight: 600; color: #111827; }
      a { color: #10b981; text-decoration: none; }
      a:hover { text-decoration: underline; }"]]
   [:body content]])

(defn render-view
  "Render a view function to HTML response.

  Args:
    render-fn - Function that takes context and returns Hiccup
    context - Map with :st-memory, :lt-memory, etc.
    layout-fn - Optional layout wrapper (default: default-layout)

  Returns: Ring response map"
  [render-fn context & [{:keys [layout-fn] :or {layout-fn default-layout}}]]
  (let [hiccup (render-fn context)
        html-body (str (h/html hiccup))
        wrapped (if layout-fn
                  (str (h/html (layout-fn hiccup {})))
                  html-body)]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body wrapped}))

(defn get-view-output
  "Retrieve rendered view output from st-memory.

  Args:
    st-memory - The short-term memory atom
    node-id - ID of the view node

  Returns: Rendered Hiccup data structure (not HTML string)"
  [st-memory node-id]
  (get-in @st-memory [::view-outputs node-id]))

(defn has-view-output?
  "Check if a view has been rendered into st-memory"
  [st-memory node-id]
  (contains? (get @st-memory ::view-outputs {}) node-id))
