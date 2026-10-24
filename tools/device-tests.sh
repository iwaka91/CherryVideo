#!/usr/bin/env bash
set -euo pipefail
evidence_path="${1:-build/device-evidence/api35}"
mkdir -p "$evidence_path"
bash gradlew --no-daemon :app:connectedDebugAndroidTest || {
  adb logcat -d -v threadtime > "$evidence_path/logcat.txt"
  adb shell screencap -p /sdcard/qualification-failure.png
  adb pull /sdcard/qualification-failure.png "$evidence_path/" || true
  adb pull /sdcard/Android/data/id.tege.video/files/qualification/. "$evidence_path/" || true
  exit 1
}
adb pull /sdcard/Android/data/id.tege.video/files/qualification/. "$evidence_path/"
adb logcat -d -v threadtime > "$evidence_path/logcat.txt"
adb shell dumpsys package id.tege.video > "$evidence_path/package.txt"
cp -R app/build/outputs/androidTest-results "$evidence_path/test-results"
