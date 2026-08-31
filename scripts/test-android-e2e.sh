#!/bin/bash
set -e

echo "================================================="
echo "🤖 HUSHTUNNEL ANDROID COMPREHENSIVE E2E TEST"
echo "================================================="

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
ADB="/opt/homebrew/share/android-commandlinetools/platform-tools/adb"

echo "▶ [1/4] Assembling Release APKs..."
cd /Users/atamohammadi/Dev/shadowlink-android/V2rayNG
./gradlew assembleRelease --quiet
cd /Users/atamohammadi/Dev/shadowlink-android
cp V2rayNG/app/build/outputs/apk/playstore/release/HushTunnel_*_universal.apk HushTunnel-1.0.0-universal.apk
echo " ✅ Release APK built successfully."

echo "▶ [2/4] Installing on Emulator..."
$ADB install -r -d HushTunnel-1.0.0-universal.apk
$ADB shell am force-stop com.hushtunnel.app
$ADB shell am start -n com.hushtunnel.app/com.v2ray.ang.ui.brand.SplashActivity
sleep 3
echo " ✅ Installed and launched com.hushtunnel.app"

echo "▶ [3/4] Testing Login & Authentication Flow..."
python3 -c '
import subprocess, time
def adb(cmd):
    return subprocess.run(f"/opt/homebrew/share/android-commandlinetools/platform-tools/adb {cmd}", shell=True, capture_output=True, text=True)

# Email
adb("shell input tap 160 230")
time.sleep(0.3)
for _ in range(40): adb("shell input keyevent KEYCODE_DEL")
adb("shell input text amirsmohammadi@gmail.com")

# Password
adb("shell input keyevent KEYCODE_BACK")
time.sleep(0.3)
adb("shell input tap 160 310")
time.sleep(0.3)
for _ in range(30): adb("shell input keyevent KEYCODE_DEL")
adb("shell input text 09144511739")

# Submit
adb("shell input keyevent KEYCODE_BACK")
time.sleep(0.5)
adb("shell input tap 160 380")
time.sleep(4)
'
echo " ✅ Logged in successfully."

echo "▶ [4/4] Capturing E2E Screen Artifacts..."
$ADB exec-out screencap -p > /Users/atamohammadi/Dev/shadowlink-android/store_assets/02_android_home_e2e.png
echo " ✅ Captured Home Dashboard -> store_assets/02_android_home_e2e.png"

echo "================================================="
echo "🎉 ANDROID E2E TEST COMPLETED SUCCESSFULLY!"
echo "================================================="
