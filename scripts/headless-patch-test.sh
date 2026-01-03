#!/bin/bash
# Runs headless patch validation using ModTheSpire's actual patching logic.
# This catches runtime errors like "No method named [foo] found on class [Bar]".

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "=== STS Arena Headless Patch Test ==="
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
CLASSPATH="target/test-classes"
CLASSPATH="$CLASSPATH:target/classes"
CLASSPATH="$CLASSPATH:lib/desktop-1.0.jar"
CLASSPATH="$CLASSPATH:lib/ModTheSpire.jar"
CLASSPATH="$CLASSPATH:lib/BaseMod.jar"

# Add Maven dependencies (JUnit for assertions)
for jar in ~/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar \
           ~/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar \
           ~/.m2/repository/org/javassist/javassist/3.29.2-GA/javassist-3.29.2-GA.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

echo "Running headless patch validation..."
echo

# Run the test with AWT headless mode
java -Djava.awt.headless=true \
     -cp "$CLASSPATH" \
     stsarena.validation.HeadlessPatchTest

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo
    echo "=== All patches validated successfully! ==="
else
    echo
    echo "=== Patch validation FAILED ==="
fi

exit $EXIT_CODE
