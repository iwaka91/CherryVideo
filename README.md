# Cherry Video

Kamera **video saja** untuk Xiaomi 13T, dengan antarmuka Indonesia, Simple Mode dan Pro Mode. Package `id.tege.video`, Android 10+, target Android 16/API 36, OpenGL ES 3.0+.

![Android qualification](https://github.com/iwaka91/CherryVideo/actions/workflows/android.yml/badge.svg)

## Hotfix 0.2.1

Perbaikan sinkronisasi awal rekaman: audio dan video memakai satu epoch relatif sebelum encoding, waktu PCM mengacu timestamp tangkapan mikrofon, dan bagian awal yang belum tumpang tindih dipangkas pada keyframe video. Validasi sinkronisasi tetap aktif. Diagnostik ekspor menyertakan percobaan rekaman terakhir, termasuk yang gagal. Berkas sementara lama tidak dihapus atau dipulihkan otomatis. Uji ulang Xiaomi 13T tetap diperlukan.

Versi APK hotfix bernama **Cherry Video Uji** (`id.tege.video.hotfix`), dipasang berdampingan agar berkas sementara versi lama tetap aman. Paket produksi tetap `id.tege.video`.

## Fitur

- 1080p30 sebagai pilihan awal; 4K30 jika kamera/encoder mendukung. 1080p60 dapat memakai sesi standar atau constrained high-speed 120+ fps dengan sampling 60 fps waktu normal. Jalur high-speed ditandai HS (uji), tanpa kontrol manual/OIS/EIS; perlu verifikasi Xiaomi. AVC/HEVC.
- Warna Natural, Cinema hangat, Teal lembut, Monokrom. Impor **3D LUT `.cube` hingga 33³ / 4 MiB**, intensitas 0–100%, pilihan pratinjau saja atau diterapkan ke video.
- Fokus manual dan perpindahan A/B, ISO/shutter, EV, AE lock, preset WB/AWB lock, pilihan OIS atau EIS, lampu kontinu jika tersedia.
- Audio kamera, mikrofon kabel/USB, atau tanpa audio. Peak meter saat merekam.
- Panel LUT di luar preview; tombol menampilkan pilihan warna, OFF/EIS/OIS dan audio aktif. About ringkas dengan lisensi terpisah.
- Penyimpanan ke `Movies/CherryVideo`, galeri (termasuk rekaman TeGe lama), diagnostik timing preview dan ekspor JSON dengan FPS aktual.
- Finalisasi saat masuk latar, suhu berat atau ruang menipis. Rekaman ditahan sebagai berkas sementara sampai pemeriksaan dan publikasi selesai.

## Build dan uji

JDK 17 dan Android SDK 36 diperlukan.

```bash
bash gradlew :core:test :camera:lintDebug :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
bash gradlew :app:connectedDebugAndroidTest
```

Workflow GitHub Actions menjalankan build, lint, tes JVM dan instrumentasi emulator API 35/36: UI, pemilih LUT, transform rotasi/mirror, render GLES, validasi warna dan gerakan pada MP4, serta rekam/stop kamera emulator. Timestamp awal, sinkronisasi A/V dan durasi kontainer wajib lolos; regresi offset 15.070 detik sengaja ditolak. Pemeriksaan terpisah memakai ffprobe dan frame hasil decode. APK dan prerelease hanya diterbitkan setelah gerbang internal lolos. Bukti tes tersedia di artifact workflow.

APK adalah build uji bertanda tangan debug. Pemeriksaan sertifikat menemukan kunci berbeda dari 0.1.1: cache keystore tidak tersimpan karena lokasi yang dikonfigurasi tidak ada. APK 0.2.0 tidak dapat menimpa 0.1.1. Jika memilih instalasi bersih, amankan video, berkas LUT asli dan pengaturan dahulu; penghapusan aplikasi menghapus data privat serta salinan LUT internal. Kunci penandatanganan permanen belum tersedia. Build emulator memeriksa jalur implementasi, belum mensertifikasi kualitas, panas atau perilaku Camera2 di Xiaomi 13T.

## Batas versi awal

SDR 8-bit; belum ada LOG/HDR, RAW, LUT 1D/shaper/65³, Gyroflow, gain audio manual atau monitor headphone. Rekaman disimpan ketika aplikasi masuk latar. Galeri membuka pemutar video yang terpasang. 4K/60 fps dan latensi preview harus diuji di perangkat sebelum dianggap terbukti.

[Arsitektur, keputusan sumber dan rencana validasi](docs/ARCHITECTURE_AND_AUDIT.md) · [Catatan rilis](docs/RELEASE_NOTES.md) · [Lisensi Apache 2.0](LICENSE) · [Atribusi](NOTICE)

`CubeLut.kt` dan `FocusPull.kt` diadaptasi dari [OpenCineCam](https://github.com/LibreStatic/opencinecam) pada revisi yang dicatat dalam NOTICE. Modul kamera/GPU/rekaman/UI lainnya berada di repo ini. Tidak ada fitur fotografi.
