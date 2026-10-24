#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/device-evidence
bash gradlew --no-daemon :app:connectedDebugAndroidTest || {
  adb logcat -d -v threadtime > build/device-evidence/logcat.txt
  adb shell screencap -p /sdcard/qualification-failure.png
  adb pull /sdcard/qualification-failure.png build/device-evidence/ || true
  adb pull /sdcard/Android/data/id.tege.video/files/qualification/. build/device-evidence/ || true
  exit 1
}
adb pull /sdcard/Android/data/id.tege.video/files/qualification/. build/device-evidence/
adb logcat -d -v threadtime > build/device-evidence/logcat.txt
adb shell dumpsys package id.tege.video > build/device-evidence/package.txt
