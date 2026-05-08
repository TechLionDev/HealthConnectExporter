#!/bin/bash
set -e

echo "=== HealthConnectExporter Setup ==="

# Detect package manager
if command -v yay &>/dev/null; then
    PKGMGR="yay"
elif command -v pacman &>/dev/null; then
    PKGMGR="sudo pacman"
else
    echo "ERROR: Need yay or pacman"
    exit 1
fi

echo "[1/5] Installing build dependencies..."

if [ "$PKGMGR" = "yay" ]; then
    yay -S --noconfirm \
        jdk17-openjdk \
        android-sdk \
        android-sdk-build-tools \
        android-sdk-platform-tools \
        android-platform \
        gradle
else
    sudo pacman -S --noconfirm \
        jdk17-openjdk \
        android-sdk \
        android-sdk-build-tools \
        android-sdk-platform-tools \
        android-platform \
        gradle
fi

echo "[2/5] Setting up Android SDK..."

# Android SDK paths to check
SDK_PATHS=(
    "$HOME/Android/Sdk"
    "/opt/android-sdk"
    "/usr/lib/android-sdk"
)

SDK_DIR=""
for path in "${SDK_PATHS[@]}"; do
    if [ -d "$path" ]; then
        SDK_DIR="$path"
        break
    fi
done

# If no SDK found, install android-sdk from AUR
if [ -z "$SDK_DIR" ]; then
    echo "Installing Android SDK via AUR..."
    cd /tmp && git clone https://aur.archlinux.org/android-sdk.git && cd android-sdk && makepkg -si --noconfirm
    SDK_DIR="$HOME/android-sdk"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$PATH:$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$SDK_DIR/build-tools/35.0.0"

# Accept licenses
yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
yes | "$SDK_DIR/tools/bin/sdkmanager" --licenses >/dev/null 2>&1 || true

echo "[3/5] SDK manager check..."
if [ -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --install "platforms;android-35" "build-tools;35.0.0" "platform-tools" 2>/dev/null || true
elif [ -f "$SDK_DIR/tools/bin/sdkmanager" ]; then
    "$SDK_DIR/tools/bin/sdkmanager" --install "platforms;android-35" "build-tools;35.0.0" "platform-tools" 2>/dev/null || true
fi

echo "[4/5] Generating Gradle wrapper..."
cd "$(dirname "$0")"
chmod +x gradlew

# Point to correct SDK location in local.properties
echo "sdk.dir=$SDK_DIR" > local.properties

echo "[5/5] Building APK..."
./gradlew assembleDebug --no-daemon -q

APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
if [ -f "$APK" ]; then
    cp "$APK" ./HealthConnectExporter.apk
    echo ""
    echo "=== BUILD SUCCESS ==="
    echo "APK: $(pwd)/HealthConnectExporter.apk"
    echo "Size: $(du -h HealthConnectExporter.apk | cut -f1)"
else
    echo "BUILD FAILED - check output above"
    exit 1
fi