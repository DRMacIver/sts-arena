#!/bin/bash
# NOTE: This script requires a Steam installation to work properly.
# For CI/testing without Steam, use headless-patch-test.sh instead.
#
# Uses ModTheSpire's actual patching process to validate the mod.
# This runs the same patching code that would run when loading the game,
# catching any errors like "No method named [foo] found on class [Bar]".
#
# This approach is more thorough than static analysis because it uses
# ModTheSpire's own annotation scanning and Javassist patching logic.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "=== STS Arena - ModTheSpire Patch Validation ==="
echo

# Check prerequisites
if [ ! -f "lib/desktop-1.0.jar" ]; then
    echo "ERROR: lib/desktop-1.0.jar not found"
    echo "Please copy the game JAR from your Slay the Spire installation"
    exit 1
fi

if [ ! -f "lib/ModTheSpire.jar" ]; then
    echo "ERROR: lib/ModTheSpire.jar not found"
    exit 1
fi

if [ ! -f "lib/BaseMod.jar" ]; then
    echo "ERROR: lib/BaseMod.jar not found"
    exit 1
fi

# Build the project
echo "Building project..."
mvn package -DskipTests -q

if [ ! -f "target/STSArena.jar" ]; then
    echo "ERROR: Build did not produce target/STSArena.jar"
    exit 1
fi

echo "Build successful"
echo

# Create a temporary mods directory
TEMP_MODS_DIR=$(mktemp -d)
mkdir -p "$TEMP_MODS_DIR/mods"
cp target/STSArena.jar "$TEMP_MODS_DIR/mods/"
cp lib/BaseMod.jar "$TEMP_MODS_DIR/mods/"

# Copy game jar to temp dir (ModTheSpire looks for it relative to itself)
cp lib/desktop-1.0.jar "$TEMP_MODS_DIR/"

echo "Running ModTheSpire in out-jar mode..."
echo "(This runs the full patching process without starting the game)"
echo

# Run ModTheSpire with --out-jar to just do patching
# We redirect to temp since we don't need the output jar
cd "$TEMP_MODS_DIR"

# Create minimal config to avoid Steam lookup
mkdir -p "sendToDevs"

# Run with output redirected, capturing any errors
OUTPUT=$(java -Djava.awt.headless=true \
     -cp ".:lib/ModTheSpire.jar" \
     -Dmts.jar="ModTheSpire.jar" \
     -jar "$PROJECT_DIR/lib/ModTheSpire.jar" \
     --skip-launcher \
     --out-jar \
     --mods stsarena \
     2>&1) || EXIT_CODE=$?

cd "$PROJECT_DIR"

# Clean up temp directory
rm -rf "$TEMP_MODS_DIR"

# Check for errors
if echo "$OUTPUT" | grep -qi "error\|exception\|no method named\|not found"; then
    echo "VALIDATION FAILED"
    echo
    echo "ModTheSpire output:"
    echo "$OUTPUT"
    exit 1
fi

if [ -n "$EXIT_CODE" ] && [ "$EXIT_CODE" -ne 0 ]; then
    echo "VALIDATION FAILED with exit code $EXIT_CODE"
    echo
    echo "ModTheSpire output:"
    echo "$OUTPUT"
    exit 1
fi

echo "ModTheSpire output:"
echo "$OUTPUT"
echo
echo "=== Patch validation passed! ==="
