#!/usr/bin/env bash
set -euo pipefail
evidence_path="${1:-build/device-evidence/api35}"
mkdir -p "$evidence_path"
# Keep the test emulator offline and stop unrelated first-boot Google synchronization.
# Never change packages/network state when this script is used on a physical phone.
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
  adb shell pm disable-user --user 0 com.google.android.gms || true
  adb shell pm disable-user --user 0 com.google.android.googlequicksearchbox || true
  adb shell svc wifi disable
  adb shell svc data disable
fi
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
