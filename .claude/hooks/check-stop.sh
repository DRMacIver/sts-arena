#!/bin/bash
# Stop hook for STS Arena project
# This hook prevents stopping until all work is complete
#
# Exit codes:
# - 0: Allow stopping (with optional JSON output)
# - 2: Block stopping (message in stderr)

set -e

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(dirname "$(dirname "$(dirname "$0")")")}"
cd "$PROJECT_DIR" || exit 1

# Check for open beads issues (not closed or needing-review)
check_open_issues() {
    if command -v bd &> /dev/null; then
        # bd ready shows issues that are ready to work on (open, not blocked)
        # Format: "📋 Ready work (N issues with no blockers):" - extract N from header
        # Use || true to prevent set -e from exiting, then default empty to 0
        READY_COUNT=$(bd ready 2>/dev/null | grep -oP '\(\d+ issues' | grep -oP '\d+' || true)
        READY_COUNT=${READY_COUNT:-0}
        # Also check for in_progress issues
        # grep -c outputs count and exits 1 if no matches - use || true to suppress error
        IN_PROGRESS_COUNT=$(bd list --status=in_progress 2>/dev/null | grep -cE '^\d+\.' || true)
        IN_PROGRESS_COUNT=${IN_PROGRESS_COUNT:-0}
        echo $((READY_COUNT + IN_PROGRESS_COUNT))
    else
        echo 0
    fi
}

# Check git status for uncommitted changes
check_uncommitted() {
    if [ -d .git ]; then
        git status --porcelain 2>/dev/null | wc -l || echo 0
    else
        echo 0
    fi
}

# Check for open GitHub issues and add them to beads
check_github_issues() {
    if command -v gh &> /dev/null && gh auth status &>/dev/null; then
        # Get open issues from GitHub
        OPEN_GITHUB=$(gh issue list --state open --json number,title,labels 2>/dev/null || echo "[]")
        if [ "$OPEN_GITHUB" != "[]" ]; then
            echo "$OPEN_GITHUB"
        else
            echo ""
        fi
    else
        echo ""
    fi
}

# Import GitHub issues into beads if not already tracked
import_github_issues_to_beads() {
    if ! command -v bd &>/dev/null || ! command -v gh &>/dev/null; then
        return
    fi

    GITHUB_ISSUES=$(gh issue list --state open --json number,title,body,labels 2>/dev/null || echo "[]")
    if [ "$GITHUB_ISSUES" = "[]" ]; then
        return
    fi

    echo "$GITHUB_ISSUES" | jq -r '.[] | "\(.number)|\(.title)|\(.labels | map(.name) | join(","))"' 2>/dev/null | while IFS='|' read -r NUMBER TITLE LABELS; do
        # Check if this issue is already tracked in beads (by looking for github:#NUMBER in title or notes)
        if ! bd list 2>/dev/null | grep -q "github:#$NUMBER"; then
            # Map GitHub labels to beads priority and type
            PRIORITY="2"  # default medium
            TYPE="task"
            if echo "$LABELS" | grep -qi "bug"; then TYPE="bug"; fi
            if echo "$LABELS" | grep -qi "enhancement"; then TYPE="feature"; fi
            if echo "$LABELS" | grep -qi "priority:high"; then PRIORITY="1"; fi
            if echo "$LABELS" | grep -qi "priority:critical"; then PRIORITY="0"; fi

            # Create beads issue
            bd create --title="[github:#$NUMBER] $TITLE" --type="$TYPE" --priority="$PRIORITY" 2>/dev/null || true
            echo "Imported GitHub issue #$NUMBER: $TITLE" >&2
        fi
    done
}

# Import any open GitHub issues to beads before checking
import_github_issues_to_beads

OPEN_ISSUES=$(check_open_issues)
UNCOMMITTED=$(check_uncommitted)

# If there are open issues, block stopping
if [ "$OPEN_ISSUES" -gt 0 ]; then
    cat >&2 << 'EOF'
STOP BLOCKED: You have open issues to work on!

Before stopping, you must:
1. Review your open issues with: bd ready
2. Work on the highest priority open issue
3. Close or update issues as appropriate: bd close <id>

Once all issues are closed or marked as needing-review, you can stop.

Open issues:
EOF
    bd ready 2>&1 | head -20 >&2 || true
    echo "" >&2
    bd list --status=in_progress 2>&1 | head -10 >&2 || true
    exit 2
fi

# If there are uncommitted changes, warn but allow with review request
if [ "$UNCOMMITTED" -gt 0 ]; then
    cat >&2 << 'EOF'
STOP BLOCKED: You have uncommitted changes!

Before stopping, you must:
1. Review the changes with: git status
2. Commit relevant changes: git add && git commit
3. Push to remote: git push
4. Sync beads: bd sync

Uncommitted files:
EOF
    git status --short >&2
    exit 2
fi

# No open issues and no uncommitted changes
# Request code review and test run before allowing stop
cat << 'EOF'
No open issues found. Before stopping, please complete these final checks:

1. THOROUGH CODE REVIEW:
   - Review any recent changes for quality
   - Check for TODOs or FIXMEs that should be filed as issues
   - Ensure code is well-documented

2. RUN ALL TESTS:
   mvn test && ./scripts/headless-mod-load-test.sh --fast

3. ADD ANY PENDING ISSUES:
   - File beads issues for any follow-up work discovered
   - File issues for known bugs or improvements

4. FINAL SYNC:
   bd sync && git push

If all checks pass, you may stop working.

Run the tests now to confirm everything is ready.
EOF

# Allow stopping - the message above is informational
exit 0
