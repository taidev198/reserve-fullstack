#!/usr/bin/env bash
set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
NDJSON_FILE="${NDJSON_FILE:-monitoring/elk/kibana/warehouse-observability.ndjson}"

curl -sS -X POST \
  "${KIBANA_URL}/api/saved_objects/_import?createNewCopies=true" \
  -H "kbn-xsrf: true" \
  --form "file=@${NDJSON_FILE}" \
  | python3 -c 'import json,sys; payload=json.load(sys.stdin); print(json.dumps(payload, indent=2)); raise SystemExit(0 if payload.get("success") else 1)'
