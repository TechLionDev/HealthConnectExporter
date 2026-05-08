#!/bin/bash
set -e

echo "=== HealthConnectExporter Setup ==="

# Detect package manager
if command -v yay &>/dev/null; then
    PKGMGR="yay"
elif command -v paru &>/dev/null; then
    PKGMGR="paru"
else
    echo "ERROR: Install yay or paru first"
    exit 1
fi

# Fix JAVA_HOME for CachyOS (Arch)
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
echo "[1/5] JAVA_HOME=$JAVA_HOME"
java -version 2>&1 | head -1

# Install Android SDK if needed
SDK_DIR="/opt/android-sdk"
if [ ! -d "$SDK_DIR" ]; then
    echo "[2/5] Installing Android SDK..."
    sudo $PKGMGR -S --noconfirm android-sdk
fi
echo "[2/5] SDK: $SDK_DIR exists"

# Install build tools and platform via SDK manager
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

if [ ! -d "$SDK_DIR/platforms/android-34" ]; then
    echo "[3/5] Installing Android SDK platforms..."
    yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --install \
        "platforms;android-34" "build-tools;34.0.0" 2>/dev/null || \
    yes | "$SDK_DIR/tools/bin/sdkmanager" --install \
        "platforms;android-34" "build-tools;34.0.0" 2>/dev/null || true
fi
echo "[3/5] Platforms ready"

# Fix Gradle wrapper to use AGP 8.2.0 compatible version (8.5, NOT 9.x)
echo "[4/5] Generating Gradle wrapper (8.5)..."
cat > gradle/wrapper/gradle-wrapper.properties << 'WRAPPER'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
WRAPPER

chmod +x gradlew
./gradlew wrapper --no-daemon 2>/dev/null || true
echo "[4/5] Gradle wrapper ready"

# Build
echo "[5/5] Building APK..."
./gradlew assembleDebug --no-daemon -q

APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ]; then
    cp "$APK" ./HealthConnectExporter.apk
    echo ""
    echo "=== SUCCESS ==="
    echo "APK: $(pwd)/HealthConnectExporter.apk"
    ls -lh HealthConnectExporter.apk
else
    echo "Build failed. Check output above."
    exit 1
fi