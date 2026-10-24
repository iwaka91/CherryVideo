TeGe Video 0.1.0 beta — personal testing build for Toto Gutomo.

Video-only Camera2 app: Simple/Pro, 1080p30 default, normal-session 1080p60 and 4K30 only where camera and codec advertise support, AVC/HEVC, 3D .cube import up to 33³ / 4 MB, intensity and monitoring/baked grades, manual focus and A/B pull, ISO/shutter/EV, WB presets/locks, mutually exclusive OIS/EIS, camera or cable/USB microphone, recording audio meter, MediaStore gallery and actual file FPS diagnostics.

Distribution is gated by Android lint, JVM unit tests, emulator instrumentation, real GLES LUT checks, camera-emulator MP4 recording and independent ffprobe inspection. A passing emulator test validates the implementation path; it does not certify the Xiaomi 13T camera HAL, sensor cadence, OIS/EIS effect, external microphone routing, heat, or sustained performance. Keep important original recordings until tested on the phone.

Limitations: SDR 8-bit only; .cube 1D/shaper/65³, vendor LOG/HDR, RAW, Gyroflow offline stabilization and constrained high-speed sessions are not included. Recording finalizes when the app goes into the background. AAC gain/monitoring is not manually adjustable; the live dBFS meter is active only while recording. Gallery uses an installed video player. This APK uses an Android debug test signature retained in the repository's Actions cache and is debuggable, not a Play Store release.
