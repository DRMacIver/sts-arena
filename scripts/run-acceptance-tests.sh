#!/bin/bash
# Run acceptance tests for STS Arena
#
# Prerequisites:
# - Game JARs in lib/ (desktop-1.0.jar, ModTheSpire.jar, BaseMod.jar, CommunicationMod.jar)
# - STS Arena mod built (target/STSArena.jar)
# - Xvfb installed for headless testing
#
# Usage:
#   ./scripts/run-acceptance-tests.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ACCEPTANCE_DIR="$PROJECT_DIR/acceptance_tests"

cd "$PROJECT_DIR"

echo "=== STS Arena Acceptance Tests ==="
echo "Project directory: $PROJECT_DIR"
echo

# Check prerequisites
echo "Checking prerequisites..."

for jar in lib/desktop-1.0.jar lib/ModTheSpire.jar lib/BaseMod.jar lib/CommunicationMod.jar; do
    if [ ! -f "$jar" ]; then
        echo "ERROR: $jar not found"
        exit 1
    fi
done

if [ ! -f "target/STSArena.jar" ]; then
    echo "Building STSArena.jar..."
    mvn package -DskipTests -q
fi

echo "All prerequisites met"
echo

# Sync uv dependencies
echo "Syncing Python dependencies..."
cd "$ACCEPTANCE_DIR"
uv sync --quiet
cd "$PROJECT_DIR"
echo

# Configure CommunicationMod
echo "Configuring CommunicationMod..."
CONFIG_DIR="$HOME/.config/ModTheSpire/CommunicationMod"
mkdir -p "$CONFIG_DIR"

AGENT_CMD="uv run --directory $ACCEPTANCE_DIR python $ACCEPTANCE_DIR/run_agent.py"
cat > "$CONFIG_DIR/config.properties" << EOF
command=$AGENT_CMD
runAtGameStart=true
verbose=false
maxInitializationTimeout=60
EOF

echo "Agent command: $AGENT_CMD"
echo

# Start Xvfb if needed
if [ -z "$DISPLAY" ]; then
    echo "Starting Xvfb..."
    Xvfb :99 -screen 0 1920x1080x24 &
    XVFB_PID=$!
    export DISPLAY=:99
    sleep 1

    # Cleanup on exit
    trap "kill $XVFB_PID 2>/dev/null" EXIT
fi

echo "Starting game with mods..."
echo

# Start the game
cd lib
java -jar ModTheSpire.jar --mods basemod,communicationmod,stsarena 2>&1 | while read line; do
    echo "[GAME] $line"
done

EXIT_CODE=${PIPESTATUS[0]}

echo
if [ $EXIT_CODE -eq 0 ]; then
    echo "=== Acceptance tests passed! ==="
else
    echo "=== Acceptance tests FAILED (exit code: $EXIT_CODE) ==="
fi

exit $EXIT_CODE
