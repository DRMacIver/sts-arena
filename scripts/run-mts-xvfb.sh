#!/bin/bash
# Run ModTheSpire with a virtual X display

cd /workspaces/sts-arena

# Ensure mods are in place
mkdir -p mods
cp target/STSArena.jar mods/ 2>/dev/null || true
cp lib/BaseMod.jar mods/ 2>/dev/null || true

JAVA=/usr/local/sdkman/candidates/java/current/bin/java

echo "Running ModTheSpire with Xvfb..."
echo "Java: $JAVA"
echo

exec /usr/bin/xvfb-run -a $JAVA -jar lib/ModTheSpire.jar --skip-launcher --mods basemod,stsarena
