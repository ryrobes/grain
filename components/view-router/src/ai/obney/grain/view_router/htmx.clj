(ns ai.obney.grain.view-router.htmx
  "HTMX helper functions for Grain views")

(defn command->url
  "Convert command keyword to URL path.

  Example: :wizard/start -> /commands/wizard/start"
  [command-kw]
  (str "/commands/" (namespace command-kw) "/" (name command-kw)))

(defn form
  "HTMX-enhanced form that posts to Grain commands.

  Options:
    :action - Command name (keyword) or URL string
    :target - CSS selector for swap target (default: body)
    :swap - Swap strategy (outerHTML, innerHTML, etc.)
    :push-url - Update browser URL? (default: true)
    :validate - Enable HTML5 validation? (default: true)

  Example:
    (htmx/form {:action :wizard/submit-step}
      [:input {:name \"email\" :required true}]
      [:button \"Submit\"])"
  [{:keys [action target swap push-url validate]
    :or {target "body" swap "outerHTML" push-url true validate true}
    :as opts}
   & body]
  (let [url (if (keyword? action)
              (command->url action)
              action)
        htmx-attrs {:hx-post url
                    :hx-target target
                    :hx-swap swap
                    :hx-push-url (str push-url)}
        other-attrs (dissoc opts :action :target :swap :push-url :validate)]
    [:form (merge htmx-attrs
                  other-attrs
                  (when-not validate {:novalidate true}))
     body]))

(defn button
  "HTMX button that triggers a command.

  Options:
    :action - Command name (keyword) or URL
    :target - CSS selector for swap target
    :method - :get or :post (default: :post)
    :confirm - Confirmation message before action
    :class - CSS class(es)

  Example:
    (htmx/button {:action :wizard/next :class \"primary\"} \"Next Step\")"
  [{:keys [action target method confirm]
    :or {method :post target "body"}
    :as opts}
   label]
  (let [url (if (keyword? action)
              (command->url action)
              action)
        hx-method-attr (case method
                         :get :hx-get
                         :post :hx-post
                         :delete :hx-delete
                         :hx-post)
        htmx-attrs {hx-method-attr url
                    :hx-target target}
        htmx-attrs (if confirm
                     (assoc htmx-attrs :hx-confirm confirm)
                     htmx-attrs)
        other-attrs (dissoc opts :action :target :method :confirm)]
    [:button (merge htmx-attrs other-attrs)
     label]))

(defn link
  "HTMX link that loads content.

  Options:
    :href - URL to load
    :target - CSS selector for swap target
    :swap - Swap strategy
    :push-url - Update browser URL?

  Example:
    (htmx/link {:href \"/wizard/welcome\"} \"Back to Start\")"
  [{:keys [href target swap push-url]
    :or {target "body" swap "outerHTML" push-url true}
    :as opts}
   & body]
  (let [htmx-attrs {:hx-get href
                    :hx-target target
                    :hx-swap swap
                    :hx-push-url (str push-url)}
        other-attrs (dissoc opts :href :target :swap :push-url)]
    [:a (merge {:href href} htmx-attrs other-attrs)
     body]))

(defn live-region
  "Create an SSE-driven live region that auto-updates.

  Options:
    :id - Element ID (required)
    :sse-url - SSE endpoint URL
    :swap - How to swap content (default: innerHTML)

  Example:
    (htmx/live-region {:id \"status\" :sse-url \"/sse/progress\"}
      [:div \"Loading...\"])"
  [{:keys [id sse-url swap]
    :or {swap "innerHTML"}
    :as opts}
   & body]
  (let [htmx-attrs {:id id
                    :hx-ext "sse"
                    :sse-connect sse-url
                    :sse-swap "message"
                    :hx-swap swap}
        other-attrs (dissoc opts :id :sse-url :swap)]
    [:div (merge htmx-attrs other-attrs)
     body]))

(defn spinner
  "Loading spinner that shows during HTMX requests.

  Example:
    (htmx/form {:action :wizard/process}
      [:input ...]
      [:button \"Submit\"]
      (htmx/spinner))"
  []
  [:div {:class "htmx-indicator"
         :style "display: none;"}
   "⏳ Loading..."])
