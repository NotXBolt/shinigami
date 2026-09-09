#!/bin/bash
# Unified Standalone Build & Release Script for Shinigami
# Fully automated build pipeline — universal across 26.1.2 Fabric and modern versions.

export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64

# Build full package (includes test exclusions for 26.1 APIs, skips proguard/dist packaging for dev speed)
./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon

# Verify output JAR exists (~2.0 MB target)
if [ -f "fabric/build/libs/baritone-fabric-1.17.0.jar" ]; then
    echo "[BUILD OK] Shinigami JAR built successfully"
    cp fabric/build/libs/baritone-fabric-1.17.0.jar /storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar
    echo "[DEPLOY OK] Deployed to /storage/emulated/0/1log/"
else
    echo "[BUILD FAIL] JAR not found"
    exit 1
fi

# Auto-sync to GitHub mirror (~/Ws/github/shini/)
cp -r /root/Ws/nxt/shini/shinigami/* ~/Ws/github/shini/shinigami/
cp -r /root/Ws/nxt/shini/.ai/* ~/Ws/github/shini/.ai/
cp /root/Ws/nxt/shini/*.md ~/Ws/github/shini/
echo "[SYNC OK] GitHub mirror updated"

echo "=== SHINIGAMI FULLY UNIFIED STANDALONE BUILD COMPLETE ==="
