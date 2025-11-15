#!/bin/bash
# Send a command via HTTP with proper Transit JSON encoding
# Usage: ./curl-command.sh debug-example/simple-task

COMMAND=${1:-"debug-example/simple-task"}

# Transit JSON encoding for: {:command {:name :debug-example/simple-task}}
# Format: ["^ " = map start, "~:" = keyword prefix]
TRANSIT_BODY='["^ ","~:command",["^ ","~:name","~:'$COMMAND'"]]'

echo "🚀 Sending command: $COMMAND"
echo ""

curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/transit+json" \
  -d "$TRANSIT_BODY" \
  -w "\n\n"

echo "✅ Check the debug UI at http://localhost:8082"
