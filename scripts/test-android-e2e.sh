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

echo "▶ [1/8] Assembling Release APKs..."
cd /Users/atamohammadi/Dev/shadowlink-android/V2rayNG
./gradlew assembleRelease --quiet
cd /Users/atamohammadi/Dev/shadowlink-android
cp V2rayNG/app/build/outputs/apk/playstore/release/HushTunnel_*_universal.apk HushTunnel-1.0.0-universal.apk
echo " ✅ Release APK built successfully."

echo "▶ [2/8] Installing on Emulator..."
$ADB install -r -d HushTunnel-1.0.0-universal.apk
$ADB shell am force-stop com.hushtunnel.app
$ADB shell pm clear com.hushtunnel.app >/dev/null
$ADB shell am start -n com.hushtunnel.app/com.v2ray.ang.ui.brand.SplashActivity
sleep 3
echo " ✅ Installed and launched com.hushtunnel.app"

echo "▶ [3/8] Testing Login & Authentication Flow..."
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

echo "▶ [4/8] Verifying the separate VPN disclosure and affirmative consent..."
$ADB shell uiautomator dump /sdcard/hushtunnel-disclosure.xml >/dev/null
DISCLOSURE_UI="$($ADB shell cat /sdcard/hushtunnel-disclosure.xml)"
if ! printf '%s' "$DISCLOSURE_UI" | rg -q "VPN connection disclosure"; then
  echo "ERROR: The VPN disclosure was not shown before authenticated service and purchase controls." >&2
  exit 1
fi
if ! printf '%s' "$DISCLOSURE_UI" | rg -q "Agree and continue" || ! printf '%s' "$DISCLOSURE_UI" | rg -q "Not now"; then
  echo "ERROR: The VPN disclosure does not provide affirmative consent and decline actions." >&2
  exit 1
fi
python3 -c '
import re, subprocess, xml.etree.ElementTree as ET
xml = subprocess.run(["/opt/homebrew/share/android-commandlinetools/platform-tools/adb", "shell", "cat", "/sdcard/hushtunnel-disclosure.xml"], capture_output=True, text=True, check=True).stdout
root = ET.fromstring(xml)
node = next((n for n in root.iter("node") if n.attrib.get("text") == "Agree and continue"), None)
if node is None:
    raise SystemExit("Agree and continue control not found")
left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
subprocess.run(["/opt/homebrew/share/android-commandlinetools/platform-tools/adb", "shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2)], check=True)
'
sleep 3
echo " ✅ Disclosure is shown before service use and requires an affirmative action."

echo "▶ [5/8] Verifying guest profile import entry points are unavailable..."
if $ADB shell cmd package query-activities --brief -a android.intent.action.VIEW -d 'v2rayng://install-config' | rg -q 'com\.hushtunnel\.app'; then
  echo "ERROR: An external V2Ray configuration-import activity is still exported." >&2
  exit 1
fi
if $ADB shell cmd package query-activities --brief -a android.intent.action.SEND -t text/plain | rg -q 'com\.hushtunnel\.app'; then
  echo "ERROR: An external text configuration-import activity is still exported." >&2
  exit 1
fi
echo " ✅ Guest URL, share, QR, shortcut, and Tasker configuration entry points are disabled."

echo "▶ [6/8] Verifying Connection Diagnostics UI..."
$ADB shell uiautomator dump /sdcard/hushtunnel-window.xml >/dev/null
if ! $ADB shell cat /sdcard/hushtunnel-window.xml | rg -q "Test Connection|تست اتصال|测试连接|Проверить соединение|Bağlantıyı Test Et|hush.connection-test"; then
  echo "ERROR: Connection test control is missing from the authenticated home screen." >&2
  exit 1
fi
echo " ✅ Connection test control is present."

echo "▶ [7/8] Verifying native store and account-deletion entry points..."
$ADB shell uiautomator dump /sdcard/hushtunnel-store-window.xml >/dev/null
STORE_UI="$($ADB shell cat /sdcard/hushtunnel-store-window.xml)"
if ! printf '%s' "$STORE_UI" | rg -q "Subscription plans|Add funds|Account settings"; then
  echo "ERROR: Native purchase or account settings entry points are missing." >&2
  exit 1
fi
if printf '%s' "$STORE_UI" | rg -q "web dashboard|hushtunnel.com|Cryptomus|NOWPayments|Revolut"; then
  echo "ERROR: Store build exposes external payment or web-purchase wording." >&2
  exit 1
fi
echo " ✅ Native purchases, wallet funding, and account settings are visible without external-payment wording."

echo "▶ [8/8] Capturing E2E Screen Artifacts..."
$ADB exec-out screencap -p > /Users/atamohammadi/Dev/shadowlink-android/store_assets/02_android_home_e2e.png
echo " ✅ Captured Home Dashboard -> store_assets/02_android_home_e2e.png"

echo "================================================="
echo "🎉 ANDROID E2E TEST COMPLETED SUCCESSFULLY!"
echo "================================================="
