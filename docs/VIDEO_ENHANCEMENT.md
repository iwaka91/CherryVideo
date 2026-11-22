# Video Enhancement GPU — experimental integration

Status: **implemented in feature branch; compilation, emulator qualification and physical-device testing still pending**.

## Scope

A lightweight original GLSL pass integrated into Cherry Video's existing Camera2 → SurfaceTexture OES → OpenGL ES 3 → preview/MediaCodec rendering path. The effects are applied in the same draw function used by preview, encoder and video-derived snapshots. No new camera output surfaces, audio components, codec configuration, external libraries, or runtime permissions.

This is **not** a port of GCam HDR+ or of the Luma C++ engine. The implementation adopts broad computational-photography concepts, with its **own shader code**. No third-party source code is copied.

Processing (single pass, five camera pixel samples):

1. Edge-aware **spatial** noise reduction, using local colour similarity weights.
2. Luminance- and neighbourhood-adaptive **SDR tonal adjustment** (does not reconstruct clipped detail or constitute HDR).
3. Bounded unsharp detail enhancement; heavy sharpening is intentionally avoided.

**Not implemented:** multi-frame temporal denoising, motion compensation, super-resolution, AI segmentation for this enhancement, 10-bit HDR, LOG capture, lens corrections, neural inference or optical stabilization. The existing Bokeh, LUT, cinematic effects and text overlays remain separate.

## User interface

Open **Lainnya → Peningkatan Video (uji)**:

- Main **Aktifkan peningkatan video** switch, **OFF by default** for fresh installs.
- Three presets: **Ringan / Seimbang / Kuat**, stored in preferences (default Seimbang).
- The current ON/OFF and selected preset appear on the drawer button.
- A change takes effect on GPU preview and future encoded frames **without restarting the camera**.
- Changes while recording are blocked like existing LUT controls, preventing inconsistent recordings. Once recording ends, users can toggle and compare.
- Last user choice is persisted across launches; OFF must leave the original picture untouched.

Eligibility is conservative: regular streams of up to 1920 x 1080, at up to 30 fps. 4K, 60 fps and high-speed modes display an unavailable explanation. This is a **software safeguard pending benchmarks**, not proof that the device cannot handle higher profiles.

USB/UVC: if the USB source enters the same OES-to-GL renderer in a newer Cherry Video build, this shader can run after it; this branch itself does **not** add USB camera capture. The inspected `main` branch at implementation time reports 0.5.1-beta and does not document the later USB additions reported in device testing.

## Important regression checks

1. Verify that 1080p30 and 720p30 compile/run and the camera stays responsive with the switch OFF, ON Ringan/Seimbang/Kuat and OFF again.
2. Record the **same scene** twice (OFF/ON): static low light, moving subject, backlight, faces and fine text. Inspect detail, colour, edge halos, shadow noise and stutter. More processing does not guarantee better image quality.
3. Confirm preview and decoded MP4 match, including portrait and landscape, front and back camera, mirror policy, colours and orientation.
4. Exercise LUT, Cinematic, Bokeh and documentation stamps, separately and combined. Observe the separate GPU cost when Bokeh is enabled.
5. Validate AVC/HEVC output, record/stop, pause/resume, audio A/V sync, 5–10 minute continuous take, GPU render time, recorded FPS, dropped frames and thermals.
6. Switch to 4K or high-speed: switch must show unavailable, shader must remain bypassed, and no preference corruption should occur.
7. On a build with USB capture integrated, repeat the same tests for USB (both input types).
8. If the shader fails to compile on a particular GPU, investigate the actual GLES error; no claim of physical compatibility is made before tests.

The `VideoEnhancementTest` JVM suite checks presets, bounds and eligibility. Android instrumentation `enhancementShaderTogglesWithoutModifyingDefaultColor` exercises the real GLES shader and checks restored OFF pixels. `enhancementUiDefaultsOffAndStoresPreset` exercises the UI and persistence. These tests are **added, not yet reported as passed**.

## Releasing

Keep this work in the feature branch until the existing compilation/lint/emulator gates and Xiaomi device testing pass. No APK or release tag is created by this branch. Note that the current GitHub workflow runs on pushes to `main` (or manual dispatch) and could publish a test release. Do not merge solely to trigger an unverified APK workflow; validate first in a controlled build.

**Licensing:** the GLSL and Kotlin additions in this branch are original Cherry Video changes; no Luma or RawLens source code imported.
