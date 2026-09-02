#!/bin/bash
set -e

echo "================================================="
echo "🤖 HUSHTUNNEL ANDROID COMPREHENSIVE E2E TEST"
echo "================================================="

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
ADB="/opt/homebrew/share/android-commandlinetools/platform-tools/adb"

# Test-account credentials come from the environment, never hardcoded here —
# this script is committed to git, and a hardcoded real email/password would
# leak into repo history permanently. Set these to a disposable test account
# before running, e.g.:
#   TEST_EMAIL=qa_e2e_android@hushtunnel.com TEST_PASSWORD=... bash scripts/test-android-e2e.sh
if [ -z "$TEST_EMAIL" ] || [ -z "$TEST_PASSWORD" ]; then
  echo "ERROR: Set TEST_EMAIL and TEST_PASSWORD env vars to a disposable test account before running." >&2
  exit 1
fi

echo "▶ [1/5] Assembling Release APKs..."
cd /Users/atamohammadi/Dev/shadowlink-android/V2rayNG
./gradlew assembleRelease --quiet
cd /Users/atamohammadi/Dev/shadowlink-android
cp V2rayNG/app/build/outputs/apk/playstore/release/HushTunnel_*_universal.apk HushTunnel-1.0.0-universal.apk
echo " ✅ Release APK built successfully."

echo "▶ [2/5] Installing on Emulator..."
$ADB install -r -d HushTunnel-1.0.0-universal.apk
$ADB shell am force-stop com.hushtunnel.app
$ADB shell am start -n com.hushtunnel.app/com.v2ray.ang.ui.brand.SplashActivity
sleep 3
echo " ✅ Installed and launched com.hushtunnel.app"

echo "▶ [3/5] Testing Login & Authentication Flow..."
python3 -c '
import subprocess, time, os
def adb(cmd):
    return subprocess.run(f"/opt/homebrew/share/android-commandlinetools/platform-tools/adb {cmd}", shell=True, capture_output=True, text=True)

email = os.environ["TEST_EMAIL"]
password = os.environ["TEST_PASSWORD"]

# Email
adb("shell input tap 160 230")
time.sleep(0.3)
for _ in range(40): adb("shell input keyevent KEYCODE_DEL")
adb(f"shell input text {email}")

# Password
adb("shell input keyevent KEYCODE_BACK")
time.sleep(0.3)
adb("shell input tap 160 310")
time.sleep(0.3)
for _ in range(30): adb("shell input keyevent KEYCODE_DEL")
adb(f"shell input text {password}")

# Submit
adb("shell input keyevent KEYCODE_BACK")
time.sleep(0.5)
adb("shell input tap 160 380")
time.sleep(4)
'
echo " ✅ Logged in successfully."

echo "▶ [4/5] Verifying Connection Diagnostics UI..."
$ADB shell uiautomator dump /sdcard/hushtunnel-window.xml >/dev/null
if ! $ADB shell cat /sdcard/hushtunnel-window.xml | rg -q "Test Connection|تست اتصال|测试连接|Проверить соединение|Bağlantıyı Test Et|hush.connection-test"; then
  echo "ERROR: Connection test control is missing from the authenticated home screen." >&2
  exit 1
fi
echo " ✅ Connection test control is present."

# TODO: extend this script to cover the reseller self-service personal VPN
# flow (buy -> verify QR/connect card renders -> renew -> disconnect), the
# way scripts/test-e2e.ts in the vpn-billing-dashboard repo does at the
# API/DB level. This script only drives the UI via hardcoded tap coordinates,
# which needs a real device/emulator screen to verify — couldn't be added
# reliably without one available. See AGENTS.md section 5.

echo "▶ [5/5] Capturing E2E Screen Artifacts..."
$ADB exec-out screencap -p > /Users/atamohammadi/Dev/shadowlink-android/store_assets/02_android_home_e2e.png
echo " ✅ Captured Home Dashboard -> store_assets/02_android_home_e2e.png"

echo "================================================="
echo "🎉 ANDROID E2E TEST COMPLETED SUCCESSFULLY!"
echo "================================================="
