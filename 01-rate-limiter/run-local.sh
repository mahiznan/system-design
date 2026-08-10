#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

docker compose up -d

echo ""
echo "Rate limiter (hit this):  http://localhost:8080"
echo "Echo backend (internal):  http://localhost:8081"
echo "Prometheus:                http://localhost:9090"
echo "Grafana:                   http://localhost:3000 (admin/admin)"
echo ""
echo "Try it:"
echo "  curl -i -H 'X-API-Key: key-free-1' http://localhost:8080/hello"
echo ""
echo "Edit rate-limiter-service/config/api-keys.yaml and save to see the"
echo "key->tier mapping hot-reload without a restart (FR7 phase 1)."
