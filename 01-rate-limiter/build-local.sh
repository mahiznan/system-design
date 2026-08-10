#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "==> Building Maven modules"
mvn -q clean package -DskipTests

echo "==> Building Docker images"
docker compose build

echo "==> Build complete"
