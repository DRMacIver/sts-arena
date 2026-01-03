#!/bin/bash
# Run acceptance tests for STS Arena
#
# Prerequisites:
# - Game JARs in lib/ (desktop-1.0.jar, ModTheSpire.jar, BaseMod.jar, CommunicationMod.jar)
# - STS Arena mod built (target/STSArena.jar)
# - Xvfb installed for headless testing
# - LWJGL and OpenAL native libraries for the current platform
#
# Usage:
#   ./scripts/run-acceptance-tests.sh
#
# Note: ARM64 Linux requires additional native libraries to be set up.
# The script creates a mock Steam installation to bypass Steam detection.

set -e

# Safety check: only run in Linux devcontainer
if [ "$(uname)" != "Linux" ]; then
    echo "ERROR: This script only runs on Linux."
    echo "On macOS, run acceptance tests against your native Steam installation."
    exit 1
fi

if [ ! -f "/.dockerenv" ] && [ -z "$REMOTE_CONTAINERS" ] && [ -z "$CODESPACES" ]; then
    echo "ERROR: This script should only be run in a devcontainer."
    echo "Running on the host could damage your Steam installation."
    echo "If you really want to run this, set REMOTE_CONTAINERS=1"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ACCEPTANCE_DIR="$PROJECT_DIR/acceptance_tests"
MARKER_FILE="/tmp/sts-arena-tests-complete"
RESULT_FILE="/tmp/sts-arena-tests-result"
TIMEOUT_SECONDS=300  # 5 minutes max

cd "$PROJECT_DIR"

echo "=== STS Arena Acceptance Tests ==="
echo "Project directory: $PROJECT_DIR"
ARCH=$(uname -m)
echo "Architecture: $ARCH"
echo

# Check for ARM64 Linux - not supported
if [ "$ARCH" = "aarch64" ] && [ "$(uname)" = "Linux" ]; then
    echo "ERROR: ARM64 Linux is not supported for acceptance tests."
    echo "The game's native libraries (libGDX, LWJGL) crash on ARM64 Linux."
    echo ""
    echo "Options:"
    echo "  - Run on x86_64 Linux (e.g., GitHub Actions)"
    echo "  - Run on macOS with native Steam installation"
    echo "  - Use the devcontainer on an x86_64 host"
    exit 1
fi

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    # Kill any remaining game processes
    pkill -f "ModTheSpire" 2>/dev/null || true
    pkill -f "CardCrawlGame" 2>/dev/null || true
    # Kill Xvfb if we started it
    if [ -n "$XVFB_PID" ]; then
        kill $XVFB_PID 2>/dev/null || true
    fi
    # Clean up marker files
    rm -f "$MARKER_FILE" "$RESULT_FILE"
}
trap cleanup EXIT

# Check prerequisites
echo "Checking prerequisites..."

for jar in lib/desktop-1.0.jar lib/ModTheSpire.jar lib/BaseMod.jar lib/CommunicationMod.jar; do
    if [ ! -f "$jar" ]; then
        echo "ERROR: $jar not found"
        exit 1
    fi
done

# Build mod and test classes
echo "Building project..."
mvn compile test-compile package -DskipTests -q

echo "All prerequisites met"
echo

# Set up mock Steam installation (to bypass Steam detection in ModTheSpire)
echo "Setting up mock Steam installation..."
STEAM_DIR="$HOME/.steam/steam/steamapps"
rm -rf "$STEAM_DIR/common/SlayTheSpire/mods"
mkdir -p "$STEAM_DIR/common/SlayTheSpire/mods"
mkdir -p "$STEAM_DIR/common/SlayTheSpire/jre/bin"

# Create app manifest
cat > "$STEAM_DIR/appmanifest_646570.acf" << 'MANIFEST'
"AppState"
{
    "appid"        "646570"
    "name"        "Slay the Spire"
    "StateFlags"        "4"
    "installdir"        "SlayTheSpire"
}
MANIFEST

# Link game files
ln -sf "$PROJECT_DIR/lib/desktop-1.0.jar" "$STEAM_DIR/common/SlayTheSpire/"
ln -sf "$(which java)" "$STEAM_DIR/common/SlayTheSpire/jre/bin/java"

# Copy mods to game mods directory
cp "$PROJECT_DIR/lib/BaseMod.jar" "$STEAM_DIR/common/SlayTheSpire/mods/"
cp "$PROJECT_DIR/lib/CommunicationMod.jar" "$STEAM_DIR/common/SlayTheSpire/mods/"
cp "$PROJECT_DIR/target/STSArena.jar" "$STEAM_DIR/common/SlayTheSpire/mods/"

# Also copy mods to lib/mods (MTS relative path)
rm -rf "$PROJECT_DIR/lib/mods"
mkdir -p "$PROJECT_DIR/lib/mods"
cp "$PROJECT_DIR/lib/BaseMod.jar" "$PROJECT_DIR/lib/mods/"
cp "$PROJECT_DIR/lib/CommunicationMod.jar" "$PROJECT_DIR/lib/mods/"
cp "$PROJECT_DIR/target/STSArena.jar" "$PROJECT_DIR/lib/mods/"

echo "Mock Steam setup complete"
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

# Wrapper script that runs tests and signals completion
WRAPPER_SCRIPT="/tmp/sts-arena-test-wrapper.sh"
cat > "$WRAPPER_SCRIPT" << EOF
#!/bin/bash
cd "$ACCEPTANCE_DIR"
uv run python run_agent.py
RESULT=\$?
echo \$RESULT > "$RESULT_FILE"
touch "$MARKER_FILE"
# Keep process alive briefly so CommunicationMod can process final messages
sleep 2
exit \$RESULT
EOF
chmod +x "$WRAPPER_SCRIPT"

cat > "$CONFIG_DIR/config.properties" << EOF
command=$WRAPPER_SCRIPT
runAtGameStart=true
verbose=false
maxInitializationTimeout=60
EOF

echo "Agent command configured"
echo

# Start Xvfb if needed
if [ -z "$DISPLAY" ]; then
    echo "Starting Xvfb..."
    Xvfb :99 -screen 0 1920x1080x24 &
    XVFB_PID=$!
    export DISPLAY=:99
    sleep 1
fi

echo "Starting game with ModTheSpire..."
echo

# Clean up any previous marker
rm -f "$MARKER_FILE" "$RESULT_FILE"

# Set up native library path for ARM64 if available
JAVA_OPTS=""
if [ -d "$PROJECT_DIR/lib/natives-arm64-complete" ]; then
    NATIVES="$PROJECT_DIR/lib/natives-arm64-complete"
    export LD_LIBRARY_PATH="$NATIVES:/usr/lib/aarch64-linux-gnu:/usr/lib/jni:$LD_LIBRARY_PATH"
    JAVA_OPTS="-Djava.library.path=$NATIVES:/usr/lib/aarch64-linux-gnu:/usr/lib/jni"
fi

# Run from lib directory (where mods/ is relative to)
cd "$PROJECT_DIR/lib"
java $JAVA_OPTS -jar ModTheSpire.jar --mods basemod,CommunicationMod,stsarena 2>&1 &
GAME_PID=$!
cd "$PROJECT_DIR"

echo "Game started (PID: $GAME_PID)"
echo "Waiting for tests to complete (timeout: ${TIMEOUT_SECONDS}s)..."
echo

# Wait for marker file or timeout
START_TIME=$(date +%s)
while [ ! -f "$MARKER_FILE" ]; do
    # Check if game is still running
    if ! kill -0 $GAME_PID 2>/dev/null; then
        echo "ERROR: Game process exited unexpectedly"
        exit 1
    fi

    # Check timeout
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    if [ $ELAPSED -ge $TIMEOUT_SECONDS ]; then
        echo "ERROR: Tests timed out after ${TIMEOUT_SECONDS}s"
        exit 1
    fi

    sleep 1
done

# Tests completed - read result
EXIT_CODE=0
if [ -f "$RESULT_FILE" ]; then
    EXIT_CODE=$(cat "$RESULT_FILE")
fi

echo
if [ "$EXIT_CODE" -eq 0 ]; then
    echo "=== Acceptance tests passed! ==="
else
    echo "=== Acceptance tests FAILED (exit code: $EXIT_CODE) ==="
fi

exit $EXIT_CODE
