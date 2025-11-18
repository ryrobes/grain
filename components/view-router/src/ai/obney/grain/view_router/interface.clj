(ns ai.obney.grain.view-router.interface
  "Public interface for view routing and rendering"
  (:require [ai.obney.grain.view-router.core :as core]
            [ai.obney.grain.view-router.htmx :as htmx]))

;; Core rendering
(def render-view core/render-view)
(def extract-view-nodes core/extract-view-nodes)
(def generate-routes core/generate-routes)

;; HTMX helpers
(def htmx-form htmx/form)
(def htmx-button htmx/button)
(def htmx-link htmx/link)
