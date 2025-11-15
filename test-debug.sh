#!/bin/bash
# Quick test script for debug commands

COMMAND=${1:-"debug-example/simple-task"}

echo "🚀 Running command: $COMMAND"
echo ""

cd /home/ryanr/repos/grain
clj run-debug-command.clj "$COMMAND"

echo ""
echo "✅ Check the debug UI at http://localhost:8082"
