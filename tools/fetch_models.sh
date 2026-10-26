#!/usr/bin/env bash
# Downloads the MediaPipe person segmentation model (Apache License 2.0) into the APK assets.
# Runs on the device offline afterwards; the app never downloads anything itself.
set -euo pipefail
target="app/src/main/assets/selfie_segmenter.tflite"
url="https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite"
if [ ! -s "$target" ]; then
  curl -fsSL --retry 3 -o "$target" "$url"
fi
ls -l "$target"
sha256sum "$target"
