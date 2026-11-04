#!/usr/bin/env bash
# Installs the debug APK, opens the app and fails if it crashes in the first seconds.
set -euo pipefail
mkdir -p screenshots
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb logcat -c
adb shell am start -W -n dev.garrido.relanti/dev.garrido.relanti.android.MainActivity
sleep 15
adb exec-out screencap -p > screenshots/android-launch.png
if ! adb shell pidof dev.garrido.relanti > /dev/null; then
  echo "Relanti crashed after launch"
  adb logcat -d -s AndroidRuntime:E | tail -n 60
  exit 1
fi
if adb logcat -d -s AndroidRuntime:E | grep -q "FATAL EXCEPTION"; then
  adb logcat -d -s AndroidRuntime:E | tail -n 60
  exit 1
fi
echo "Relanti is running"
