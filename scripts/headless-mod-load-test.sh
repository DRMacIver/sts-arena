#!/bin/bash
# Runs the headless mod loader test using ModTheSpire's actual loading pipeline.
# This catches runtime errors in mod loading that wouldn't be caught by static analysis.
#
# Options:
#   -v, --verbose         Verbose output
#   --skip-compile        Skip the expensive class compilation step
#   --stsarena-only       Only test STSArena patches (faster)
#   --fast                Shortcut for --skip-compile --stsarena-only

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "=== STS Arena Headless Mod Load Test ==="
echo "Project directory: $PROJECT_DIR"
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

echo "All required JARs found"
echo

# Build the project
echo "Building project..."
mvn package -DskipTests -q

if [ ! -f "target/STSArena.jar" ]; then
    echo "ERROR: Build did not produce target/STSArena.jar"
    exit 1
fi

echo "Build successful"
echo

# Build classpath for the test
# IMPORTANT: Only include test classes and ModTheSpire on the JVM classpath.
# Game jars (desktop-1.0.jar) and mod jars (BaseMod, STSArena) are loaded dynamically
# by HeadlessModLoader via URLClassLoader to ensure MTSClassLoader can patch them
# before they're loaded by the system classloader.
CLASSPATH="target/test-classes"
CLASSPATH="$CLASSPATH:target/classes"
CLASSPATH="$CLASSPATH:lib/ModTheSpire.jar"

# Add Maven dependencies needed by MTS
for jar in ~/.m2/repository/org/javassist/javassist/3.29.2-GA/javassist-3.29.2-GA.jar \
           ~/.m2/repository/com/google/code/gson/gson/2.*/gson-*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

# Process --fast shortcut
ARGS=()
for arg in "$@"; do
    if [ "$arg" = "--fast" ]; then
        ARGS+=("--skip-compile" "--stsarena-only")
    else
        ARGS+=("$arg")
    fi
done

echo "Running headless mod loading test..."
echo

# Check if we need Xvfb (for CI/headless environments)
if [ -z "$DISPLAY" ] && command -v xvfb-run &> /dev/null; then
    echo "No DISPLAY set, using xvfb-run..."
    xvfb-run -a java -Djava.awt.headless=true \
         -cp "$CLASSPATH" \
         stsarena.validation.HeadlessModLoader "${ARGS[@]}"
else
    # Run with headless mode
    java -Djava.awt.headless=true \
         -cp "$CLASSPATH" \
         stsarena.validation.HeadlessModLoader "${ARGS[@]}"
fi

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo
    echo "=== Mod loading test passed! ==="
else
    echo
    echo "=== Mod loading test FAILED ==="
fi

exit $EXIT_CODE
