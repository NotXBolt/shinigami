#!/bin/bash
BUILD_FILE=".build_counter"
if [ -f "$BUILD_FILE" ]; then
    COUNT=$(cat "$BUILD_FILE")
    COUNT=$((COUNT + 1))
else
    COUNT=1
fi
echo $COUNT > "$BUILD_FILE"

VER="1.17.$COUNT"
echo "=== Building Shinigami v$VER ==="

export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64
./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon 2>&1 | tail -3

# Update version in fabric.mod.json
sed -i "s/\"version\": \".*\"/\"version\": \"$VER\"/" fabric/src/main/resources/fabric.mod.json 2>/dev/null

SRC="fabric/build/libs/baritone-fabric-1.17.0.jar"
DST="/storage/emulated/0/1log/Shinigami-by-Saizo-$VER.jar"
cp "$SRC" "$DST"
echo "Deployed: $DST ($(ls -lh "$DST" | awk '{print $5}'))"
