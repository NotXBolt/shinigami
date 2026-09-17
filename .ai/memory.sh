#!/bin/bash
# Memory CLI — query and manage project memory
# Usage: ./memory.sh <command> [args]

CMD="${1:-help}"
shift 2>/dev/null || true

case "$CMD" in
  add)
    echo "Usage: ./memory.sh add \"content\" [tags]"
    ;;
  search|query|q)
    echo "Usage: ./memory.sh search \"query\""
    ;;
  list|l)
    echo "Usage: ./memory.sh list [limit]"
    ;;
  help|*)
    echo "Memory System — Shinigami by Saizo"
    echo ""
    echo "Usage:"
    echo "  ./memory.sh add \"content\" [tags]    Store a memory"
    echo "  ./memory.sh search \"query\"           Semantic search"
    echo "  ./memory.sh list [limit]              List recent memories"
    echo ""
    echo "Local .ai/ directory:"
    echo "  .ai/architecture/    — Architecture decisions & rationale"
    echo "  .ai/memory/          — Key facts & context"
    echo "  .ai/dependency_maps/ — System dependency maps"
    echo "  .ai/timelines/       — Session timelines"
    echo "  .ai/issues/          — Known issues tracking"
    echo "  .ai/summaries/       — Session summaries"
    echo ""
    echo "Web UI: http://127.0.0.1:4747"
    ;;
esac
