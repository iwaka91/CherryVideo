Cherry Video 0.2.1 beta — recording startup synchronization hotfix.

- Feed surface video and PCM/AAC timestamps relative to one MONOTONIC epoch before encoding. Gate stale preview frames before they enter the video encoder; never discard the initial encoded keyframe merely because it predates a start-call timestamp.
- Anchor PCM to AudioRecord TIMEBASE_MONOTONIC capture timestamps when available. Fall back to the first blocking PCM read completion, accounting for captured sample count, rather than the time startRecording returned.
- Buffer startup packets until audio and a decodable video keyframe overlap. Trim pre-roll using one shared offset for both tracks; do not independently reset audio/video to zero. Preserve the existing A/V start/end and container-duration validators, including rejection of the 15,070-second clock regression.
- Add regression tests for delayed video, delayed microphone, AAC priming, codec output order, no-audio recording, and playable delayed-start AVC/HEVC files when an encoder is available.
- Include actual start timestamps in validation errors and retain last-recording-attempt.json (also included in diagnostic export) even when publication fails. Existing pending recordings are not deleted or automatically rewritten.

Scope: startup/finalization bug only. No zoom, camera-lens selection or audio-input feature changes. Compile/lint/JVM tests, Android API 35/36 instrumentation, and independent ffprobe/decoded-frame inspection must pass before APK publication. Emulator results do not certify Xiaomi 13T microphone acoustics, latency, 4K/60 fps or device stability. The original failing Xiaomi file/log was not available; physical retesting is required.

Personal debug-signed beta. Verify the signing certificate against the installed APK before assuming in-place update support. Do not uninstall or clear data while old pending recordings need to be retained. This hotfix does not recover previously failed takes.
