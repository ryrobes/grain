(ns ai.obney.grain.behavior-tree-v2.core.interactive-nodes
  "Interactive behavior tree nodes for flows that need to block.

  The standard :parallel node executes all children in futures and waits
  for them to complete. This doesn't work for interactive flows where
  some children return :running to indicate they're waiting.

  These nodes provide alternatives that handle :running correctly."
  (:require [ai.obney.grain.behavior-tree-v2.interface.protocol :as p :refer [opts+children]]))

;;
;; Parallel-View-Action Pattern
;;
;; Special node for view + await pattern.
;; Executes view first (always succeeds), then action (might return :running).
;;
;; Usage:
;;   [:view-action
;;     [:view {:id :welcome} welcome-view]
;;     [:action {:id :await} await-fn]]

(defmethod p/tick :view-action
  [{:keys [children] :as _node} context]
  (let [[view-node action-node] children

        ;; Execute view first (should return :success immediately)
        view-result (p/tick view-node context)

        ;; Then execute action
        action-result (p/tick action-node context)]

    ;; Return action result (might be :running)
    action-result))

(defmethod p/build :view-action
  [node-type args]
  (let [[opts children] (opts+children args)]
    (assoc opts
           :type node-type
           :children (mapv #(p/build (first %) (rest %)) children))))
