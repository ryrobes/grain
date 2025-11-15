(ns ai.obney.grain.debug-example-service.interface.commands
  "Command registry for debug example service."
  (:require [ai.obney.grain.debug-example-service.core.commands :as core]))

(def commands core/commands)
