#!/usr/bin/env bash
#
# run-tests.sh — Execute Selenium Cucumber tests from the JAR
#
# Usage:
#   ./run-tests.sh                              # Default: DEV + Chrome
#   ./run-tests.sh --env=uat                    # UAT environment
#   ./run-tests.sh --browser=firefox            # Specific browser
#   ./run-tests.sh --tags="@smoke"              # Run only @smoke tagged scenarios
#   ./run-tests.sh --parallel-cross-browser     # All browsers in parallel
#   ./run-tests.sh --headless --threads=8       # Headless with 8 threads
#   ./run-tests.sh --features=/path/to/features # External feature files
#   ./run-tests.sh --dry-run                    # Validate steps only
#   ./run-tests.sh --help                       # Show all options
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="automation-test-framework-1.0.0.jar"
JAR_PATH="$SCRIPT_DIR/target/$JAR_NAME"

# ── Check JAR exists ──────────────────────────────────────────────────
if [ ! -f "$JAR_PATH" ]; then
    echo "╔═══════════════════════════════════════════════════════╗"
    echo "║  JAR not found! Building first...                    ║"
    echo "╚═══════════════════════════════════════════════════════╝"
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests
    echo ""
fi

# ── Run ───────────────────────────────────────────────────────────────
echo "╔═══════════════════════════════════════════════════════╗"
echo "║  Launching Test Automation JAR                       ║"
echo "╠═══════════════════════════════════════════════════════╣"
echo "║  JAR:  $JAR_NAME"
echo "║  Args: $*"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""

java -jar "$JAR_PATH" "$@"

EXIT_CODE=$?

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  Exit Code: $EXIT_CODE"
echo "═══════════════════════════════════════════════════════"

exit $EXIT_CODE
