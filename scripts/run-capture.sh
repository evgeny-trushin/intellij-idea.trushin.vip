#!/usr/bin/env bash
# Run the Git Flow Capture workflow tests against a running IntelliJ IDEA instance.
# Prereq: IntelliJ IDEA must be running with Remote Robot plugin on port 8082.
set -e -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SCRIPT_DIR"

ROBOT_HOST="${ROBOT_HOST:-127.0.0.1}"
ROBOT_PORT="${ROBOT_PORT:-8082}"
CAPTURES_DIR="${CAPTURES_DIR:-$SCRIPT_DIR/captures}"
EVENT_LOG="${EVENT_LOG:-$HOME/git-flow-log.jsonl}"

echo "=== IntelliJ Git Flow Capture ==="
echo "Robot Server: http://$ROBOT_HOST:$ROBOT_PORT"
echo "Captures dir: $CAPTURES_DIR"
echo "Event log:    $EVENT_LOG"
echo ""

# Check robot server is available
echo "Checking Robot Server..."
if ! curl -s -o /dev/null -w "%{http_code}" "http://$ROBOT_HOST:$ROBOT_PORT" | grep -q "200"; then
    echo "ERROR: Robot Server not available at http://$ROBOT_HOST:$ROBOT_PORT"
    echo ""
    echo "Make sure:"
    echo "  1. IntelliJ IDEA is running"
    echo "  2. Robot Server Plugin is installed and enabled"
    echo "  3. Port $ROBOT_PORT is correct"
    exit 1
fi
echo "Robot Server: OK"
echo ""

# Create captures directory
mkdir -p "$CAPTURES_DIR"

# Select which workflow to run
WORKFLOW="${1:-all}"

run_test() {
    local test_class="$1"
    local display_name="$2"
    echo ""
    echo "--- Running: $display_name ---"
    ./gradlew integrationTest --tests "$test_class" \
        -Drobot.host="$ROBOT_HOST" \
        -Drobot.port="$ROBOT_PORT" \
        -Dcaptures.dir="$CAPTURES_DIR" \
        -Devent.log="$EVENT_LOG" \
        --info 2>&1 | tail -20
    echo "--- $display_name: Done ---"
}

case "$WORKFLOW" in
    feature)
        run_test "vip.trushin.intellij.workflows.FeatureBranchFlowTest" "Feature Branch Flow (R8.1)"
        ;;
    pull-merge)
        run_test "vip.trushin.intellij.workflows.PullMergeFlowTest" "Pull & Merge Flow (R8.2)"
        ;;
    rebase)
        run_test "vip.trushin.intellij.workflows.RebaseFlowTest" "Rebase Flow (R8.3)"
        ;;
    stash)
        run_test "vip.trushin.intellij.workflows.StashFlowTest" "Stash Flow (R8.4)"
        ;;
    cherry-pick)
        run_test "vip.trushin.intellij.workflows.CherryPickFlowTest" "Cherry-Pick Flow (R8.5)"
        ;;
    rollback)
        run_test "vip.trushin.intellij.workflows.RollbackFlowTest" "Rollback Flow (R8.6)"
        ;;
    pr)
        run_test "vip.trushin.intellij.workflows.PrReviewFlowTest" "PR Review Flow (R8.7)"
        ;;
    all)
        run_test "vip.trushin.intellij.workflows.FeatureBranchFlowTest" "Feature Branch Flow (R8.1)"
        run_test "vip.trushin.intellij.workflows.PullMergeFlowTest" "Pull & Merge Flow (R8.2)"
        run_test "vip.trushin.intellij.workflows.RebaseFlowTest" "Rebase Flow (R8.3)"
        run_test "vip.trushin.intellij.workflows.StashFlowTest" "Stash Flow (R8.4)"
        run_test "vip.trushin.intellij.workflows.CherryPickFlowTest" "Cherry-Pick Flow (R8.5)"
        run_test "vip.trushin.intellij.workflows.RollbackFlowTest" "Rollback Flow (R8.6)"
        run_test "vip.trushin.intellij.workflows.PrReviewFlowTest" "PR Review Flow (R8.7)"
        ;;
    *)
        echo "Usage: $0 [feature|pull-merge|rebase|stash|cherry-pick|rollback|pr|all]"
        exit 1
        ;;
esac

echo ""
echo "=== Capture Complete ==="
echo ""

# Generate report
echo "Generating use-case report..."
./gradlew generateReport \
    -Devent.log="$EVENT_LOG" \
    -Dcaptures.dir="$CAPTURES_DIR" \
    -Dreport.output="$SCRIPT_DIR/use-case-report.md" 2>&1 | tail -5

echo ""
echo "Artifacts:"
echo "  Event log:  $EVENT_LOG"
echo "  Screenshots: $CAPTURES_DIR/"
echo "  Report:      $SCRIPT_DIR/use-case-report.md"
