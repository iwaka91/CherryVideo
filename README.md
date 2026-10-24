# TeGe Video

Kamera **video saja** untuk Xiaomi 13T, dengan antarmuka Indonesia, Simple Mode dan Pro Mode. Package `id.tege.video`, Android 10+, target Android 16/API 36, OpenGL ES 3.0+.

![Android qualification](https://github.com/iwaka91/CherryVideo/actions/workflows/android.yml/badge.svg)

## Fitur

- 1080p30 sebagai pilihan awal; 1080p60 dan 4K30 hanya ditawarkan jika sesi Camera2 standar serta encoder mendukung. AVC/HEVC.
- Warna Natural, Cinema hangat, Teal lembut, Monokrom. Impor **3D LUT `.cube` hingga 33³ / 4 MiB**, intensitas 0–100%, pilihan pratinjau saja atau diterapkan ke video.
- Fokus manual dan perpindahan A/B, ISO/shutter, EV, AE lock, preset WB/AWB lock, pilihan OIS atau EIS, lampu kontinu jika tersedia.
- Audio kamera, mikrofon kabel/USB, atau tanpa audio. Peak meter saat merekam.
- Penyimpanan ke `Movies/TeGeVideo`, galeri, diagnostik dan ekspor JSON dengan FPS aktual dari timestamp file.
- Finalisasi saat masuk latar, suhu berat atau ruang menipis. Rekaman ditahan sebagai berkas sementara sampai pemeriksaan dan publikasi selesai.

## Build dan uji

JDK 17 dan Android SDK 36 diperlukan.

```bash
bash gradlew :core:test :camera:lintDebug :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
bash gradlew :app:connectedDebugAndroidTest
```

Workflow GitHub Actions menjalankan build, lint, tes JVM dan instrumentasi emulator API 35: UI, pemilih berkas LUT, render GLES sebenarnya, validasi warna pada MP4 hasil encode, serta rekam/stop kamera emulator. Pemeriksaan terpisah memakai ffprobe. APK dan prerelease hanya diterbitkan sesudah gerbang internal lolos. Bukti tes tersedia di artifact workflow.

APK adalah build uji bertanda tangan debug. Identitas debug dipertahankan melalui Actions cache; belum memakai kunci rilis produksi. Build emulator memeriksa jalur implementasi, belum mensertifikasi kualitas, panas atau perilaku Camera2 di Xiaomi 13T.

## Batas versi awal

SDR 8-bit; belum ada LOG/HDR, RAW, LUT 1D/shaper/65³, Gyroflow, sesi constrained high-speed, gain audio manual atau monitor headphone. Rekaman disimpan ketika aplikasi masuk latar. Galeri membuka pemutar video yang terpasang. 60 fps harus lolos uji di perangkat sebelum dianggap terbukti.

[Arsitektur, keputusan sumber dan rencana validasi](docs/ARCHITECTURE_AND_AUDIT.md) · [Catatan rilis](docs/RELEASE_NOTES.md) · [Lisensi Apache 2.0](LICENSE) · [Atribusi](NOTICE)

`CubeLut.kt` dan `FocusPull.kt` diadaptasi dari [OpenCineCam](https://github.com/LibreStatic/opencinecam) pada revisi yang dicatat dalam NOTICE. Modul kamera/GPU/rekaman/UI lainnya berada di repo ini. Tidak ada fitur fotografi.
