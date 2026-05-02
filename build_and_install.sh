#!/bin/bash
# IPFilter 一键编译安装脚本
set -e

echo "=== IPFilter Build & Install ==="
echo ""

# 编译
echo "[1/2] Building..."
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found at $APK"
    exit 1
fi

# 安装
echo "[2/2] Installing..."
adb install -r "$APK"

echo ""
echo "Done! Restart 小红书 to activate."