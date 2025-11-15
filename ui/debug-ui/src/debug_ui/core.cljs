(ns debug-ui.core
  "Main entry point for debug UI"
  (:require [reagent.dom.client :as rdom-client]
            [re-frame.core :as rf]
            [store.db]
            [store.events :as events]
            [store.effects]
            [store.subs]
            [components.app :refer [app]]))

;; Create React 18 root once at load time
(defonce root
  (when-let [el (js/document.getElementById "app")]
    (rdom-client/create-root el)))

(defn ^:dev/after-load mount-root
  "Mount the root component using React 18 createRoot API"
  []
  (when root
    (rdom-client/render root [app])))

(defn init!
  "Initialize the application"
  []
  (rf/dispatch-sync [::events/initialize])
  (rf/dispatch [::events/connect-sse])
  (mount-root)
  (js/console.log "🌳 Grain Debug Tracer initialized"))
