#!/bin/bash
set -e

echo "=== HealthConnectExporter Setup ==="

# Java
if [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
elif [ -d "/usr/lib/jvm/java-17" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17"
fi
echo "[1/5] JAVA_HOME=$JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

# Android SDK
SDK_DIR="$HOME/android-sdk"
mkdir -p "$SDK_DIR"

# Download cmdline-tools if missing
if [ ! -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "[2/5] Downloading Android cmdline-tools..."
    mkdir -p "$SDK_DIR/cmdline-tools"
    CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    curl -fsSL "$CMDLINE_URL" -o /tmp/cmdline-tools.zip
    unzip -q /tmp/cmdline-tools.zip -d "$SDK_DIR/cmdline-tools"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm /tmp/cmdline-tools.zip
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

# Accept licenses and install required SDK components
echo "[3/5] Accepting licenses and installing SDK 36..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install "platforms;android-36" "build-tools;36.0.0" > /dev/null 2>&1

# Download Gradle 8.11.1 wrapper JAR if missing
WRAPPER_JAR="$HOME/HealthConnectExporter/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "[4/5] Downloading Gradle 8.11.1 wrapper..."
    mkdir -p "$HOME/HealthConnectExporter/gradle/wrapper"
    curl -fsSL "https://github.com/gradle/gradle/raw/v8.11.1/gradle/wrapper/gradle-wrapper.jar" -o "$WRAPPER_JAR"
fi

# Update gradle wrapper properties to 8.11.1
cat > "$HOME/HealthConnectExporter/gradle/wrapper/gradle-wrapper.properties" << 'GRADLEPROPS'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
GRADLEPROPS

# Build
echo "[5/5] Building APK..."
cd "$HOME/HealthConnectExporter"
chmod +x gradlew
./gradlew assembleDebug --no-daemon

if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
    cp app/build/outputs/apk/debug/app-debug.apk HealthConnectExporter.apk
    echo "=== SUCCESS ==="
    ls -lh HealthConnectExporter.apk
else
    echo "Build failed. Check output above."
    exit 1
fi
