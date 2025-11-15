#!/bin/bash
# Quick wrapper for sending debug commands via HTTP
# Usage: ./cmd.sh simple-task (or robot-mission, make-decision, parallel-tasks, error-handling)

COMMAND_NAME=${1:-"simple-task"}

# Add prefix if not provided
if [[ ! $COMMAND_NAME =~ / ]]; then
  COMMAND_NAME="debug-example/$COMMAND_NAME"
fi

echo "🚀 Executing: $COMMAND_NAME"
clj -Sdeps '{:deps {com.cognitect/transit-clj {:mvn/version "1.0.333"}}}' -M send-command.clj "$COMMAND_NAME"
