#!/bin/bash
# Shinigami by Saizo - Build Script
# Run this to compile and produce the deployment jar

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Shinigami by Saizo Build ==="
echo ""

# 1. Set up JDK 25
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java
echo "[1/3] Checking Java version..."
java -version 2>&1 || { echo "ERROR: JDK 25 not found at $JAVA_HOME"; exit 1; }

# 2. Build fabric module
echo ""
echo "[2/3] Building Shinigami (fabric)..."
echo "      (first build downloads deps - may take 5-15 minutes)"
echo ""

# Only build fabric - skip forge/neoforge configuration
./gradlew :fabric:build -x test --no-daemon -p fabric 2>&1

# 3. Verify output
echo ""
echo "[3/3] Checking build output..."
JAR_FILE=$(ls fabric/build/libs/*.jar 2>/dev/null | grep -v sources | grep -v shadow | head -1)

if [ -n "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo "=== BUILD SUCCESSFUL ==="
    echo "Jar: $JAR_FILE"
    echo "Size: $JAR_SIZE"
    echo ""
    echo "To install:"
    echo "  1. Copy jar to your mods folder"
    echo "  2. Launch with Fabric Loader 0.18.6+"
    echo "  3. Press R to toggle, G for GUI"
    echo "  4. Use 'tag help' in chat for commands"
else
    echo "=== BUILD FAILED ==="
    echo "No jar file found. Check errors above."
    exit 1
fi
