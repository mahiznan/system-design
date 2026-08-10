#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

load=false
chaos=false

for arg in "$@"; do
  case "$arg" in
    --load) load=true ;;
    --chaos) chaos=true ;;
    *) echo "Unknown flag: $arg" >&2; exit 1 ;;
  esac
done

if [ "$load" = true ]; then
  echo "Load test not implemented yet — see load-test/README.md" >&2
  exit 1
fi

if [ "$chaos" = true ]; then
  echo "Chaos test not implemented yet — see chaos/README.md" >&2
  exit 1
fi

echo "==> Running unit + integration tests"
mvn -q test
