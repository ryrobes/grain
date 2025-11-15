#!/bin/bash
# Helper script to test debug commands

COMMAND_NAME=${1:-"debug-example/simple-task"}

# Using Transit JSON format
curl -v -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d "{\"command\": {\"name\": \"${COMMAND_NAME}\"}}" \
  2>&1 | grep -A 20 "< HTTP"

echo ""
echo "Available commands:"
echo "  debug-example/simple-task"
echo "  debug-example/robot-mission"
echo "  debug-example/make-decision"
echo "  debug-example/parallel-tasks"
echo "  debug-example/error-handling"
