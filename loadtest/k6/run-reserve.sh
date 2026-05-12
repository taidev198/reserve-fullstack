#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/loadtest/k6"
mkdir -p reports

# URL reachable from *this machine* (curl). Override port if the API is not on 8080, e.g.:
#   BASE_URL=http://localhost:8081 ./loadtest/k6/run-reserve.sh
HOST_API_URL="${BASE_URL:-http://localhost:8080}"

uses_docker_k6() {
  ! command -v k6 >/dev/null 2>&1 && command -v docker >/dev/null 2>&1
}

# k6 inside Docker cannot use localhost to reach services on the host; host.docker.internal works there.
# Curl runs on the host, so it must keep localhost/127.0.0.1 (host.docker.internal often does not resolve on the host).
if uses_docker_k6 && [[ "${DOCKER_K6_NO_HOST_REWRITE:-}" != "1" ]]; then
  if [[ "${HOST_API_URL}" == *"localhost"* ]] || [[ "${HOST_API_URL}" == *"127.0.0.1"* ]]; then
    export BASE_URL="${HOST_API_URL//localhost/host.docker.internal}"
    export BASE_URL="${BASE_URL//127.0.0.1/host.docker.internal}"
    echo "Docker k6 will use ${BASE_URL} (host-side curl still uses ${HOST_API_URL})" >&2
  else
    export BASE_URL="${HOST_API_URL}"
  fi
else
  export BASE_URL="${HOST_API_URL}"
fi

export K6_TARGET_VUS="${K6_TARGET_VUS:-100}"
export K6_RAMP="${K6_RAMP:-1m}"
export K6_HOLD="${K6_HOLD:-3m}"

if [[ "${SKIP_HEALTH:-}" != "1" ]] && command -v curl >/dev/null 2>&1; then
  echo "Checking ${HOST_API_URL}/actuator/health ..."
  curl -sfS "${HOST_API_URL}/actuator/health" >/dev/null || {
    echo "Health check failed. Start the API and ensure HOST_API_URL matches (same host/port as Spring server.port)." >&2
    exit 1
  }
  echo "Tip: start the API with SPRING_PROFILES_ACTIVE=loadtest so the reservation rate limiter does not return 429 during load tests." >&2
  echo "     (Or set RESERVATION_RATE_LIMIT_FOR_PERIOD to a very large integer without changing profiles.)" >&2
fi

run_k6() {
  k6 run reserve.js "$@"
}

print_report_hint() {
  echo ""
  echo "Chart report: ${ROOT}/loadtest/k6/reports/k6-report-latest.html"
  echo "(Open in a browser; Chart.js loads from the CDN. Use OPEN_REPORT=1 on macOS to open automatically.)"
  if [[ "${OPEN_REPORT:-}" == "1" ]] && command -v open >/dev/null 2>&1; then
    open "${ROOT}/loadtest/k6/reports/k6-report-latest.html"
  fi
}

if command -v k6 >/dev/null 2>&1; then
  echo "Using local k6."
  ec=0
  run_k6 "$@" || ec=$?
  print_report_hint
  exit "$ec"
elif command -v docker >/dev/null 2>&1; then
  echo "Using grafana/k6 Docker image."
  NET_ARG=()
  if [[ "$(uname -s)" == "Darwin" ]] || [[ "$(uname -s)" == "Linux" ]]; then
    NET_ARG=(--add-host "host.docker.internal:host-gateway")
  fi
  ec=0
  docker run --rm -i "${NET_ARG[@]}" \
    -e BASE_URL \
    -e SKIP_SETUP \
    -e K6_TARGET_VUS \
    -e K6_RAMP \
    -e K6_HOLD \
    -e SKUS \
    -e ITEM_QTY \
    -e THINK_MS \
    -e K6_EXPORT_FULL_SUMMARY \
    -v "$ROOT/loadtest/k6:/scripts" \
    -w /scripts \
    grafana/k6:latest \
    run reserve.js "$@" || ec=$?
  print_report_hint
  exit "$ec"
else
  echo "Install k6 (https://k6.io/docs/get-started/installation/) or Docker." >&2
  exit 1
fi
