Cherry Video 0.3.0 beta — audio presets, zoom and lens/input discovery.

Audio modes: Natural bypasses the application DSP; Suara Jernih and Reduksi Kuat use one pinned SpeexDSP engine with separate channel states and maximum noise attenuation presets -12 dB and -28 dB. These are configuration limits, not measured reduction promises. System NS/AGC are disabled where the effect controller permits it, and their actual states are reported. A hidden OEM processing path cannot be claimed raw. No application AGC or VAD gate is enabled.

SpeexDSP is selected over RNNoise for the first integration because its API directly exposes attenuation control for the requested two strengths, with no model download. Upstream source and BSD notices are retained. The native processor is built for arm64-v8a, armeabi-v7a and x86_64. The 10 ms WOLA delay is compensated on the shared A/V clock and one tail frame is drained before AAC EOS.

Zoom is controlled by pinch and a compact inline slider, using Camera2 zoom ratio or active-array crop. Limits follow each camera. High-speed zoom is disabled. The lens list includes public cameras and discoverable physical candidates, opened through their logical parent. Lens type names are approximate classifications based on focal length/sensor size. An advertised physical camera can still be rejected by a vendor session; only a successful real preview/recording confirms access.

External microphone selection includes Android-exposed USB, wired/line and Bluetooth microphone inputs. Wireless USB receivers follow USB routing; Bluetooth needs a supported microphone profile and permission. Input routing is verified during recording and route changes rechecked. Missing or rejected external input causes a clear error rather than silent builtin fallback. Temporary communication routing is restored on release.

Diagnostics retain actual input, active microphone descriptions, channel count, input/output RMS, processor mode/latency, system effect states, requested/reported zoom and physical ID. Previous pending recordings are not changed or automatically recovered.

Qualification requires native signal/noise/channel/tail regressions, JVM tests/lint/build, Android 15/16 instrumentation and independent MP4 timing/motion checks. Synthetic audio tests do not certify intelligibility, microphone acoustics or thermal behavior on Xiaomi 13T. Real speech, external microphones, lens switching, 4K/60 fps and sustained performance still require device testing.

APK label/package: Cherry Video Uji / id.tege.video.hotfix, same as the 0.2.1 Uji hotfix. CI uses the cached explicit test key. Installation compatibility must be checked against the prior APK certificate. Production package id.tege.video is unchanged. Personal debug beta, SDR 8-bit.
