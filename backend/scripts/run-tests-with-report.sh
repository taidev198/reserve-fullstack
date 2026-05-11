#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$BACKEND_DIR" || exit 1

echo "Running tests (continue even if failures)..."
./../mvnw -f pom.xml test -Dmaven.test.failure.ignore=true
TEST_EXIT=$?

echo "Generating visual test report..."
python3 scripts/generate_test_report.py
REPORT_EXIT=$?

if [[ $REPORT_EXIT -eq 0 ]]; then
  REPORT_PATH="$BACKEND_DIR/target/test-report/index.html"
  echo "Report ready: $REPORT_PATH"
  if command -v open >/dev/null 2>&1; then
    open "$REPORT_PATH"
  fi
fi

if [[ $TEST_EXIT -ne 0 ]]; then
  echo "Tests had failures/errors. See visual report for breakdown."
fi

exit $TEST_EXIT
