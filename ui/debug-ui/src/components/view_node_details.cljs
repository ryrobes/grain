(ns components.view-node-details
  "View node details component - shows view-specific information"
  (:require [re-frame.core :as rf]
            [store.subs :as subs]
            [store.events :as events]))

(defn is-view-node?
  "Check if a node is a view node by checking for view-specific data"
  [node-id tree-structure]
  (when (and node-id tree-structure)
    (letfn [(find-node [tree target-id]
              (cond
                (and (map? tree) (= target-id (:node-id tree)))
                tree

                (and (map? tree) (:children tree))
                (some #(find-node % target-id) (:children tree))

                :else nil))]
      (when-let [node (find-node tree-structure node-id)]
        (= :view (:type node))))))

(defn extract-view-config
  "Extract view configuration from tree structure"
  [node-id tree-structure]
  (letfn [(find-node [tree target-id]
            (cond
              (and (map? tree) (= target-id (:node-id tree)))
              tree

              (and (map? tree) (:children tree))
              (some #(find-node % target-id) (:children tree))

              :else nil))]
    (when-let [node (find-node tree-structure node-id)]
      (:view-config node))))

(defn view-node-details
  "Render details for a view node"
  [{:keys [node-id trace-id tree-structure]}]
  (let [api-base @(rf/subscribe [::subs/api-base])
        view-config (extract-view-config node-id tree-structure)
        view-id (:id view-config)
        route (:route view-config)
        preview-url (str api-base "/debug/trace/" trace-id "/view/" (name view-id))]
    [:div
     [:div {:style {:font-size "0.875rem"
                    :line-height "1.25rem"
                    :color "#a78bfa"
                    :font-family "ui-monospace, 'Fira Code'"
                    :margin-bottom "1rem"
                    :padding "0.75rem"
                    :background-color "#3b0764"
                    :border-radius "0.375rem"
                    :border "1px solid #6b21a8"}}
      [:div {:style {:font-size "1.125rem"
                     :font-weight "600"
                     :margin-bottom "0.5rem"
                     :color "#e9d5ff"}}
       " View Node"]

      [:div {:style {:margin-bottom "0.5rem"}}
       [:strong "ID: "] (str view-id)]

      (when route
        [:div {:style {:margin-bottom "0.5rem"}}
         [:strong "Route: "] [:code {:style {:background-color "#581c87"
                                              :padding "0.125rem 0.375rem"
                                              :border-radius "0.25rem"}}
                              route]])]

     [:div {:style {:margin-top "1rem"}}
      [:button {:on-click #(rf/dispatch [::events/show-view-preview preview-url])
                :style {:display "inline-flex"
                        :align-items "center"
                        :gap "0.5rem"
                        :background-color "#7c3aed"
                        :color "white"
                        :padding "0.5rem 1rem"
                        :border-radius "0.375rem"
                        :border "none"
                        :font-weight "500"
                        :transition "background-color 0.2s"
                        :cursor "pointer"}
                :onMouseOver (fn [e] (set! (-> e .-target .-style .-backgroundColor) "#6d28d9"))
                :onMouseOut (fn [e] (set! (-> e .-target .-style .-backgroundColor) "#7c3aed"))}
       [:span ""]
       [:span "Preview This View"]]]

     [:div {:style {:margin-top "1.5rem"
                    :padding "0.75rem"
                    :background-color "#1e293b"
                    :border-radius "0.375rem"
                    :font-size "0.75rem"
                    :color "#94a3b8"}}
      [:p {:style {:margin "0 0 0.5rem 0"}}
       "💡 This is a view node that rendered UI during execution."]
      [:p {:style {:margin 0}}
       "Click Preview to see the HTML that was rendered at this step."]]]))
