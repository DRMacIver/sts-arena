#!/bin/bash
# Script to launch StS and capture logs for testing
# Note: This captures logs but can't automate button clicks easily

STEAM_PATH="$HOME/Library/Application Support/Steam/steamapps"
STS_PATH="$STEAM_PATH/common/SlayTheSpire/SlayTheSpire.app"
LOG_FILE="/tmp/sts-arena-test.log"

echo "Building mod..."
cd "$(dirname "$0")/.."
mvn package -q

echo "Launching Slay the Spire with logging..."
echo "Logs will be written to: $LOG_FILE"
echo "Press Ctrl+C to stop"

# Launch via ModTheSpire, redirecting stderr (where log4j writes) to file
# Note: This launches the mod loader UI, not directly into the game
open "$STS_PATH" 2>&1 | tee "$LOG_FILE"

# Alternative: Direct java launch with logging (bypasses Steam but might work)
# cd "$STS_PATH/Contents/Resources"
# java -jar ModTheSpire.jar 2>&1 | tee "$LOG_FILE"
