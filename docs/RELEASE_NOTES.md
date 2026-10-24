Cherry Video 0.2.0 beta — update after Xiaomi 13T bug reports.

- Rename app to Cherry Video. Keep package id.tege.video and existing preferences/signing identity for an in-place update. New recordings use Movies/CherryVideo; gallery includes the old folder.
- Correct the Camera2 SurfaceTexture clock to MONOTONIC before feeding MediaRecorder. Never substitute per-frame arrival time or reset video to zero before MediaRecorder. Final validation rejects displaced video, A/V start/end mismatch and inflated container duration. Regression tests cover the reported 15,070-second offset.
- Remove producer sensor rotation/mirroring once before applying preview rotation. Encode sensor-oriented pixels with per-take orientation metadata. Quadrant GPU and matrix tests cover four rotations and mirroring.
- Coalesce frame callbacks, latch latest buffers, cache shader uniforms, bypass unused color work and avoid an extra swap-vsync wait. Report preview submission rate, render duration and arrival gaps. These metrics do not measure physical photon-to-photon latency.
- Offer regular 1080p60 where supported; otherwise expose a conditional HS (uji) path at an advertised fixed 120+ fps, sampling to real-time 60 fps. HS disables manual camera controls/OIS/EIS. Unknown minimum stream duration no longer automatically hides an otherwise supported <=30 fps profile. 4K remains capability-dependent.
- Replace the large LUT modal with an inline dock beside/below preview, with current look/intensity. Main buttons show LUT, OFF/EIS/OIS and audio selection. About is concise, with source and full license on separate views. Diagnostics are readable and export structured capability, physical-camera and timing data.

Distribution requires compile/lint, JVM tests, Android 15/API 35 and Android 16/API 36 emulator instrumentation, real GLES grading/motion/orientation checks, and independent decoded-frame/ffprobe A/V/duration checks. Failed checks suppress APK publication.

Physical Xiaomi 13T verification is still required for upright/mirrored preview, latency, audio sync, 1080p30/60, 4K30, focus motor access, OIS/EIS, microphones and heat. Software emulator performance is not a passed 30/60 fps performance gate. No claim of cinematic image quality or Xiaomi stability certification is made.

Personal debug-signed beta. SDR 8-bit, 3D .cube up to 33³/4 MB. No LOG/HDR/RAW, 1D/shaper/65³ LUT, Gyroflow, manual audio gain or headphone monitoring. Backgrounding finalizes the take.
