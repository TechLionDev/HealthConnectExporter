# HealthConnectExporter

A minimal Android app that exports ALL your Health Connect data to a single JSON file.

## What it does

Queries every Health Connect data type (steps, heart rate, sleep, nutrition, etc.) via the Health Connect SDK and writes the full history to a timestamped JSON file stored on your device.

## Prerequisites (CachyOS)

```bash
# Install build dependencies
./install.sh
```

The `install.sh` script will:
1. Install `jdk17-openjdk`, `android-sdk`, `android-sdk-build-tools`, `gradle` via yay/pacman
2. Locate or install the Android SDK
3. Accept SDK licenses
4. Build the APK automatically
5. Output `HealthConnectExporter.apk` in the project root

## Build manually (if install.sh fails)

```bash
export ANDROID_HOME=~/Android/Sdk  # or wherever SDK is
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ./HealthConnectExporter.apk
```

## Install on phone

1. Transfer `HealthConnectExporter.apk` to your S23
2. Install it (enable "Install from unknown sources" if needed)
3. Open Health Connect Exporter
4. Tap "Export All Data"
5. Grant all permissions when prompted
6. Wait for export to complete
7. The JSON file will be saved in the app's private storage. Use a file manager or ADB pull to retrieve it:

```bash
adb shell run-as com.techlion.healthconnectexporter ls files/exports/
adb pull /data/data/com.techlion.healthconnectexporter/files/exports/health_export_*.json ~/
```

## Data types exported

Steps, Heart Rate, Resting HR, HRV, Blood Pressure, Blood Glucose, Body Temperature, Oxygen Saturation, Body Fat, Weight, Height, Lean Body Mass, Basal Metabolic Rate, Active Calories, Total Calories, Exercise Sessions, Total Steps, Distance, Floors Climbed, Nutrition, Hydration, Sleep Sessions, Sleep Stages, Vo2 Max, Power, Speed, Cycling Wheel Revolutions, Cycling RPM, Respiratory Rate

## Requirements

- Android 14+ (SDK 28+)
- Health Connect app installed and up to date
- ~500MB free space for the export