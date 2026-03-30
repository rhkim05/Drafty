#!/bin/bash
# Auto-push watcher for Drafty project
# Checks for unpushed commits on claude-cowork and pushes them.
# Designed to be run by launchd every 5 minutes.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOG_FILE="$PROJECT_DIR/scripts/auto-push.log"
BRANCH="claude-cowork"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOG_FILE"
}

cd "$PROJECT_DIR" || { log "ERROR: Cannot cd to $PROJECT_DIR"; exit 1; }

# Ensure we're in a git repo
if [ ! -d ".git" ]; then
    log "SKIP: Not a git repo yet. Run git-setup.sh first."
    exit 0
fi

# Ensure we're on the right branch
CURRENT_BRANCH=$(git branch --show-current 2>/dev/null)
if [ "$CURRENT_BRANCH" != "$BRANCH" ]; then
    log "SKIP: On branch '$CURRENT_BRANCH', expected '$BRANCH'."
    exit 0
fi

# Safety: never push to main
if [ "$CURRENT_BRANCH" = "main" ] || [ "$CURRENT_BRANCH" = "master" ]; then
    log "BLOCKED: Refusing to push to $CURRENT_BRANCH."
    exit 1
fi

# Check if there are any staged/unstaged changes to auto-commit
CHANGES=$(git status --porcelain 2>/dev/null)
if [ -n "$CHANGES" ]; then
    git add -A
    DIFF_STAT=$(git diff --cached --stat | tail -1)
    git commit -m "Auto-commit: $DIFF_STAT

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
    log "COMMIT: $DIFF_STAT"
fi

# Check for unpushed commits
UNPUSHED=$(git log origin/$BRANCH..$BRANCH --oneline 2>/dev/null)
if [ -z "$UNPUSHED" ]; then
    # No unpushed commits
    exit 0
fi

# Push
if git push origin $BRANCH 2>> "$LOG_FILE"; then
    COMMIT_COUNT=$(echo "$UNPUSHED" | wc -l | tr -d ' ')
    log "PUSHED: $COMMIT_COUNT commit(s) to $BRANCH"
else
    log "ERROR: Push failed. Check network or credentials."
    exit 1
fi
