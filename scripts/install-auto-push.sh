#!/bin/bash
# Install the auto-push watcher as a macOS launchd agent.
# Run once: bash scripts/install-auto-push.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PLIST_NAME="com.drafty.auto-push"
PLIST_SRC="$SCRIPT_DIR/$PLIST_NAME.plist"
PLIST_DEST="$HOME/Library/LaunchAgents/$PLIST_NAME.plist"

echo "Installing Drafty auto-push watcher..."

# Make scripts executable
chmod +x "$SCRIPT_DIR/git-auto-push.sh"
chmod +x "$SCRIPT_DIR/git-setup.sh"

# Replace placeholder path in plist with actual project path
sed "s|DRAFTY_PATH_PLACEHOLDER|$PROJECT_DIR|g" "$PLIST_SRC" > "$PLIST_DEST"

# Unload if already loaded
launchctl unload "$PLIST_DEST" 2>/dev/null || true

# Load the agent
launchctl load "$PLIST_DEST"

echo ""
echo "Auto-push watcher installed!"
echo "  - Runs every 5 minutes"
echo "  - Only pushes to 'claude-cowork' branch (never main)"
echo "  - Logs to: $SCRIPT_DIR/auto-push.log"
echo ""
echo "To stop it:   launchctl unload $PLIST_DEST"
echo "To restart it: launchctl load $PLIST_DEST"
echo "To uninstall:  rm $PLIST_DEST"
