Cherry Video 0.3.1 beta — new portrait layout and Simpel | Pro switch.

UI only. The camera, recording, audio, LUT and zoom code paths are unchanged from 0.3.0; the two-finger zoom gesture fix is a separate follow-up.

Layout: title with a two-position Simpel | Pro switch (both labels always visible, active green, inactive grey; tap to choose; stored preference kept; locked while recording). Below: lens, quality and zoom buttons, a large preview with a zoom-ratio badge and a small recording/preview info label, then Galeri, a round record button and Lainnya. Lainnya opens a drawer with LUT, Stabilisasi, Audio, Pengaturan and, in Pro, Fokus/Exposure/WB. Back closes the drawer. Footer "Cherry Video ©2026 | totogutomo.com". Landscape keeps a right-hand sidebar with the switch under the title.

Qualification: JVM tests, lint, build and Android 15/16 emulator instrumentation, including Simpel/Pro switching, LUT dock placement below the preview and a fully visible landscape record button. Emulator results do not certify the Xiaomi 13T layout, comfort or colors; check portrait and landscape on the phone.

APK label/package: Cherry Video Uji / id.tege.video.hotfix, versionCode 6. CI uses the cached explicit test key, so it should install over 0.3.0 Uji; if Android refuses, uninstall first (export recordings beforehand). Production package id.tege.video is unchanged. Personal debug beta, SDR 8-bit.
