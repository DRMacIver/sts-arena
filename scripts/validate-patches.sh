#!/bin/bash
# Validates that all patches in the mod target valid game classes and methods.
# This catches errors like "No method named [foo] found on class [Bar]" before runtime.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "=== STS Arena Patch Validation ==="
echo "Project directory: $PROJECT_DIR"

# Check if game JAR exists
GAME_JAR="lib/desktop-1.0.jar"
if [ ! -f "$GAME_JAR" ]; then
    echo "ERROR: Game JAR not found at $GAME_JAR"
    echo "Please copy desktop-1.0.jar from your Slay the Spire installation to lib/"
    exit 1
fi

echo "Game JAR found: $GAME_JAR"

# Compile the project first
echo ""
echo "Compiling project..."
mvn compile test-compile -q

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed"
    exit 1
fi

echo "Compilation successful"

# Run the patch validation tests
echo ""
echo "Running patch validation tests..."
mvn test -Dtest=PatchValidationTest -q

if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Patch validation failed!"
    echo "Check the test output above for details."
    exit 1
fi

echo ""
echo "=== All patches validated successfully! ==="
