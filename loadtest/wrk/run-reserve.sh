#!/usr/bin/env bash
# Load-test POST /reservations using wrk (https://github.com/wg/wrk).
# Prereq: API up (e.g. ./mvnw spring-boot:run), same defaults as loadtest/k6/run-reserve.sh.
#
# Usage:
#   ./loadtest/wrk/run-reserve.sh
#   BASE_URL=http://localhost:8081 WRK_THREADS=8 WRK_CONNECTIONS=200 WRK_DURATION=60s ./loadtest/wrk/run-reserve.sh
#
# Optional env: SKUS, ITEM_QTY (exported for the Lua script via os.getenv).
# For sustained creates without 429s, start the API with SPRING_PROFILES_ACTIVE=loadtest
# or raise RESERVATION_RATE_LIMIT_FOR_PERIOD (see application.yml / k6 runner hints).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="$ROOT/loadtest/wrk"

BASE_URL="${BASE_URL:-http://localhost:8080}"
export SKUS="${SKUS:-A100,B200,C300,D400,E500}"
export ITEM_QTY="${ITEM_QTY:-1}"

WRK_THREADS="${WRK_THREADS:-4}"
WRK_CONNECTIONS="${WRK_CONNECTIONS:-100}"
WRK_DURATION="${WRK_DURATION:-30s}"

if ! command -v wrk >/dev/null 2>&1; then
  echo "wrk is not on PATH. Build or install from https://github.com/wg/wrk" >&2
  echo "  macOS (Homebrew): brew install wrk" >&2
  exit 1
fi

if [[ "${SKIP_HEALTH:-}" != "1" ]] && command -v curl >/dev/null 2>&1; then
  echo "Checking ${BASE_URL}/actuator/health ..."
  curl -sfS "${BASE_URL}/actuator/health" >/dev/null || {
    echo "Health check failed. Start the API and set BASE_URL to the server root (scheme://host:port)." >&2
    exit 1
  }
  echo "Tip: use SPRING_PROFILES_ACTIVE=loadtest (or a high RESERVATION_RATE_LIMIT_FOR_PERIOD) to avoid 429 during heavy runs." >&2
fi

echo "wrk threads=${WRK_THREADS} connections=${WRK_CONNECTIONS} duration=${WRK_DURATION} target=${BASE_URL}" >&2
exec wrk -t"${WRK_THREADS}" -c"${WRK_CONNECTIONS}" -d"${WRK_DURATION}" --latency \
  -s "${SCRIPT_DIR}/reserve.lua" \
  "${BASE_URL}"
