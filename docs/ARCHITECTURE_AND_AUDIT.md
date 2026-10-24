# Cherry Video: implementation decisions and evidence

## 0.2.0 correction after physical bug reports

The original 0.1.1 emulator gate was insufficient: it checked cadence but not video/audio epoch alignment or container duration, and screenshots did not prove correct image orientation. The user's 1080p sample contains 256 changing frames at about 30 fps, but video starts at 15070.3593 s while audio starts at 0 s, producing an approximately four-hour timeline. 0.1.1 is not qualified for normal use.

Camera2 SurfaceTexture defaults to SENSOR timestamps; MediaRecorder audio needs MONOTONIC. API 33+ OutputConfiguration explicitly requests MONOTONIC; API 29–32 REALTIME sensors use the elapsedRealtimeNanos-minus-nanoTime offset. Relative cadence is preserved. Validation checks start/end alignment, actual sample durations and container duration, and rejects the supplied regression pattern.

SurfaceTexture also contains camera sensor orientation and optional mirroring. TextureGeometry removes the predicted camera transform while preserving producer crop, then applies one image-to-GL transform and requested preview rotation/mirror. The encoder receives unmirrored sensor pixels with orientation metadata. API 33+ sets producer mirroring NONE. Tests cover synthetic GPU quadrants plus sensor orientations 90/270 and all four display rotations.

References: [Android output timestamp/mirror contract](https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration), [AndroidX camera transform rationale](https://android.googlesource.com/platform/frameworks/support/+/6353c5d0cbdb0a766f9640dde3ffcb0f9e4cfcf1/camera/camera-core/src/main/java/androidx/camera/core/processing/SurfaceOutputImpl.java). TextureGeometry is a small original implementation of these matrix operations, not an imported CameraX module.

1080p60 can use an advertised regular profile or fixed constrained high-speed capture at 120+ fps with a real-time frame gate. Availability is not proof that a vendor delivers the requested rate; the UI marks HS as device-test pending. Manual controls and stabilization are disabled in HS sessions. Diagnostics include high-speed ranges and candidate rejection inputs, not guessed support. Older normal-session-only statements below describe the 0.1.1 baseline.

0.2.0 stores new video in Movies/CherryVideo and retains package id.tege.video for updates. Internal qualification now runs API 35 and 36, decoded motion at distinct times, A/V epochs/duration, orientation/mirror and UI selection/dock checks. Physical-device latency, smoothness, image orientation and sustained 1080p/4K performance remain mandatory follow-up checks.

Target: Xiaomi 13T 2306EPN60G, Android 16/API 36. App label TeGe Video, package id.tege.video. No photography path, vendor binaries, login, networking or cloud processing.

## Source decisions

| Candidate | Decision in this implementation |
|---|---|
| LibreStatic/opencinecam | Adapt only `CubeLut.kt` and `FocusPull.kt` from revision `e8f063abdb97780cda2b0b17bbcf55eb07d2450a`, Apache 2.0. Retain license, NOTICE and source attribution. The upstream full application also includes photography and experimental RAW/LOG paths; these are not imported. |
| OpenMotionFX/OpenCamera | Not integrated; a separate project from Mark Harman's Open Camera. No experimental RAW pipeline is enabled. |
| Mark Harman Open Camera | Behavior reference for practical video operation; no GPL implementation copied. |
| MotionCam | No code copied; RAW recording and its thermal/storage costs are outside this SDR first release. |
| Gyroflow | No code linked or copied. Offline gyro stabilization remains future work, pending timestamp, gyro synchronization and rolling-shutter qualification. |
| FadCam | No code copied. Foreground/background surveillance recording is outside the foreground cinema-camera lifecycle chosen here. |

This is a small new Android application with two audited source adaptations, not a renamed full upstream fork. The repository does not claim that unintegrated candidates were tested on the Xiaomi 13T.

## Modules and data flow

- `core`: bounded .cube parser and trilinear reference sampler, focus-pull interpolation/session cancellation, normal-session video admission, encoded-timestamp cadence inspection. Pure Kotlin/JVM; deterministic unit tests.
- `camera`: Camera2 catalog/control ownership; single camera Handler; hardware stream/codec intersection; GPU surface pipeline; MediaRecorder AAC/MP4 finalization; MediaStore pending-to-visible publishing.
- `app`: native Android UI in Indonesian. Simple/Pro, file picker, persistent LUT library, diagnostic export, own-video gallery, thermal and lifecycle handling.

Camera2 `TEMPLATE_RECORD` -> `SurfaceTexture` OES -> GLES 3 shader -> preview EGL surface and MediaRecorder input EGL surface -> AVC/HEVC + optional AAC -> temporary app-private MP4 -> MediaExtractor validation -> MediaStore `Movies/TeGeVideo`.

LUT uploaded once as an RGBA16F 2D atlas of blue slices (maximum 33 x 1089). Bilinear sampling inside each slice plus explicit blue interpolation implements trilinear sampling. This replaces the original 3D texture path after GPU corner/highlight tests exposed black blue slices on the emulator. No per-frame CPU bitmap copy. The same grade shader serves both destinations, with an explicit original-color bypass for monitor-only mode. Strength blends source and graded SDR RGB. LUT input must correspond to SDR/Rec.709; importing a camera LOG conversion does not create a LOG recording profile. Supported cube sizes 2..33, 4 MiB byte bound, exact entry count, finite numbers, defined domain and strict UTF-8. 1D/shaper/65³ rejected. Headers from other cube dialects may be rejected rather than guessed.

## Device evidence provided by user

Camera2 screenshots report logical rear ID 0, physical IDs [3,7], LEVEL_3, MANUAL_SENSOR, RAW, manual post processing, OIS/EIS and constrained high-speed capability. Normal AE target ranges shown top out at 30 fps. Front ID 1 reports no OIS/autofocus. A reported AF OFF/manual mode alone is not proof of a movable focus motor; app additionally requires positive minimum focus distance.

Read-only inspection of supplied samples found main-camera `1003507083.mp4` (selected 1080p30) at 30.01 fps / 103 frames and `1003507090.mp4` (selected 1080p60) at 30.01 fps / 90 frames. Other GCam 4K samples averaged about 34.63 and 35.76 fps, despite nominal 60 flags. These are short indoor samples and do not establish a device-wide 60 fps limit.

1080p30 is the first profile requested. 1080p60 appears only when normal AE ranges, stream min duration and encoder size/rate support intersect. 4K30 is conditional. Constrained high-speed capability does not qualify a standard 60 fps session. Vendor allowlists and private GCam settings are not used. This may deliberately offer fewer profiles than the stock/mod interface.

## Stability and limits

One camera executor serializes state changes; separate GL executor owns all EGL resources. Camera/profile/codec/LUT changes are locked while recording. Focus/exposure remain live. OIS/EIS are mutually exclusive choices; no unsupported combination is forced. Recording finalizes when backgrounded or the screen is locked. Orientation metadata is fixed for each take. The phone must remain in the camera app for uninterrupted takes.

Recorder errors are visible; short/invalid takes are not published as successful. Finalized MP4 is validated for video dimensions, sample timestamps and expected audio before publishing. MediaStore uses IS_PENDING until copy completes; a failed publish retains the internal temporary MP4 for recovery. Abrupt process death before MP4 finalization cannot be recovered by this implementation.

Before a take at least 250 MiB free is required. Severe Android thermal status and low free-space trigger finalization; these are Android status signals, not calibrated sensor temperature guarantees. Long-take heat, sustained bitrate and 4K throughput require the phone. The recorder's platform-selected codec may be hardware or software; admission alone does not prove efficient hardware encoding.

Microphone camera/kabel-USB/off; wired/USB requested routing must be accepted before start. AAC 48 kHz stereo at 192 kb/s requested. Android may resample or map microphones; actual acoustics and external device routing require physical testing. Meter uses recorder peak amplitude while recording. No manual gain or headphone monitor is claimed.

## Internal qualification gates

GitHub Actions compiles the exact source, runs Kotlin/JVM tests and Android lint, builds the app/test APKs, runs API 35 emulator instrumentation with real EGL shaders and a camera emulator, and independently inspects MP4 samples with ffprobe. The APK upload/release steps run only after all gates succeed. Test failures keep evidence available and suppress distribution. A debug signature is retained in Actions cache for personal-test updates; this is not a production release key.

Instrumentation covers UI Simple/Pro and portrait/landscape, record/stop and audio-bearing MP4 publish, document-picker result import plus malformed LUT rejection, actual GPU LUT preview, encoded LUT color, and monitor-only original-color preservation. Synthetic constant-color fixtures test the actual production renderer; they are not used to claim camera imagery quality or sensor FPS.

An emulator pass cannot certify Xiaomi Camera2 HAL behavior, physical main/aux lens mapping, AF accuracy, mechanical focus pull smoothness, OIS/EIS effectiveness, rolling shutter, microphone routing, sustained cadence or heat. Results must state this scope explicitly.

## Next qualification on Xiaomi 13T

1. Install and grant Camera/Microphone; main lens, Natural, AVC, 1080p30. Record and inspect actual FPS using in-app diagnostics.
2. Test LUT bake vs monitoring-only using the same scene; inspect clipping, skin tones and preview/file agreement.
3. Test manual focus A/B, manual exposure, AWB lock and OIS/EIS independently.
4. Record sustained 10–20 minute takes with audio; check heat, frame gaps, A/V sync and recovery on normal backgrounding.
5. Qualify offered 1080p60/4K30 separately. Do not promote unsupported combinations through a UI toggle.
