#!/usr/bin/env bash
set -euo pipefail
evidence_path="${1:-build/device-evidence/api35}"
mkdir -p "$evidence_path"
# Keep the test emulator offline and stop unrelated first-boot Google synchronization.
# Never change packages/network state when this script is used on a physical phone.
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]]; then
  # A full-resolution Pixel launcher and Google first-boot jobs can exhaust the
  # shared software-rendered runner. They are unrelated to this offline app.
  for package in com.google.android.apps.nexuslauncher com.google.android.apps.wellbeing com.google.android.googlequicksearchbox com.google.android.partnersetup com.google.android.gms; do
    adb shell pm disable-user --user 0 "$package" || true
  done
  adb shell svc wifi disable
  adb shell svc data disable
  adb shell settings put global heads_up_notifications_enabled 0
  adb shell wm size 480x854
  adb shell wm density 160
  adb emu avd hostmicoff
  mkdir -p build/grpc-python
  /tmp/cherry-grpc-venv/bin/python tools/inject_audio.py "$ANDROID_HOME/emulator/lib" build/grpc-python > "$evidence_path/grpc-audio.txt" 2>&1 &
  injection_pid=$!
  trap 'kill "$injection_pid" 2>/dev/null || true' EXIT
fi
bash gradlew --no-daemon :app:connectedDebugAndroidTest || {
  timeout 15s adb logcat -d -v threadtime > "$evidence_path/logcat.txt" || true
  timeout 15s adb shell screencap -p /sdcard/qualification-failure.png || true
  timeout 15s adb pull /sdcard/qualification-failure.png "$evidence_path/" || true
  timeout 15s adb pull /sdcard/Android/data/id.tege.video/files/qualification/. "$evidence_path/" || true
  exit 1
}
adb pull /sdcard/Android/data/id.tege.video/files/qualification/. "$evidence_path/"
adb logcat -d -v threadtime > "$evidence_path/logcat.txt"
adb shell dumpsys package id.tege.video > "$evidence_path/package.txt"
cp -R app/build/outputs/androidTest-results "$evidence_path/test-results"
