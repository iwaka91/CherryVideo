# Cherry Video

Kamera **video saja** untuk Xiaomi 13T, dengan antarmuka Indonesia, Simple Mode dan Pro Mode. Package `id.tege.video`, Android 10+, target Android 16/API 36, OpenGL ES 3.0+.

![Android qualification](https://github.com/iwaka91/CherryVideo/actions/workflows/android.yml/badge.svg)

[Daftar update dan handoff sesi berikutnya](docs/DAFTAR_UPDATE.md) — status terbaru 0.3.3-beta (kontrol sentuh).

## Update 0.3.3

- Panel **Fokus / Exposure / WB** (Pro) pindah ke panel bawah; preview tetap terlihat.
- **Ketuk** preview untuk fokus di titik itu, **tahan lama** untuk mengunci AE/AF (tombol Buka kunci, atau ketuk layar), **geser naik/turun** untuk mengatur terang. Tidak tersedia pada 60 fps high-speed dan saat fokus/exposure manual Pro.
- Toast **Menyimpan…** bila penyimpanan video lama.

## Update 0.3.2

- Cubit dua jari memperbesar/memperkecil langsung di preview (Simpel dan Pro), badge/tombol/slider sinkron dengan kamera, teks singkat bila zoom dibatasi profil, dan zoom tidak menghentikan rekaman.
- Kamera belakang digabung menjadi **Belakang** + tombol **Balik kamera**; pintasan zoom 0.6× | 1× | 2×. Pilihan lensa teknis ada di Pengaturan → Diagnostik.
- Sakelar **Simpel | Pro** pindah ke laci Lainnya, dengan penanda **PRO** di header.
- LUT bawaan baru **Cinematic 1** dan **Cinematic 2**.
- **Uji telefoto** di Diagnostik: mencatat tahap demi tahap dan menyapu zoom untuk melihat apakah HP berpindah lensa.

## Update 0.3.1 (UI baru)

- Tata letak potret mengikuti mockup: judul dan sakelar **Simpel | Pro** di atas, baris Kamera · Kualitas · Zoom, preview besar dengan badge rasio zoom, lalu Galeri · tombol Rekam bulat · Lainnya.
- Sakelar Simpel | Pro: kedua label selalu terlihat, mode aktif hijau, tidak aktif abu-abu. Dipilih dengan ketuk; preferensi tetap tersimpan dan sakelar terkunci saat merekam.
- Laci **Lainnya** memuat LUT, Stabilisasi, Audio, Pengaturan, dan (mode Pro) Fokus · Exposure · WB. Tombol Kembali menutup laci.
- Info rekam/preview tampil sebagai label kecil di sudut preview; footer "Cherry Video ©2026 | totogutomo.com".
- Landscape: kontrol di panel kanan, sakelar di bawah judul. Logika kamera, rekaman, audio, LUT, dan zoom tidak diubah; perbaikan gestur zoom dua jari menyusul terpisah.

## Update 0.3.0

- Audio: Natural (tanpa DSP tambahan aplikasi), Suara Jernih, Reduksi Kuat. Dua preset memakai SpeexDSP dengan batas peredaman -12/-28 dB; angka ini parameter, bukan jaminan hasil. Penguatan otomatis aplikasi tidak digunakan. Efek NS/AGC sistem dicoba nonaktifkan; status sebenarnya dilaporkan.
- Pemrosesan stereo terpisah, kompensasi jeda 10 ms dan pembilasan ujung audio. Sampel/timestamp tetap mengacu satu epoch seperti hotfix 0.2.1.
- Zoom: cubit layar dan slider ringkas. Mengikuti zoom-ratio atau crop yang tersedia. Mode high-speed menonaktifkan zoom aplikasi.
- Katalog lensa mencakup kamera publik dan kandidat fisik yang memiliki profil video. Pemilihan fisik memakai sesi kamera logis; akses sebenarnya tetap bergantung pada HAL perangkat. Nama ultrawide/telefoto adalah klasifikasi perkiraan dari panjang fokus relatif ukuran sensor.
- Input mic eksternal USB/kabel dan Bluetooth yang diekspos Android. Pilihan input diverifikasi saat rekam dan perubahan rute diperiksa selama rekaman; kegagalan tidak diam-diam memakai mikrofon internal. Mic Bluetooth memerlukan izin dan profil input yang sesuai.
- Diagnostik ekspor: input aktual, kanal, level masuk/keluar, preset DSP, status NS/AGC sistem, zoom, dan lensa fisik aktif.

APK pengujian bernama **Cherry Video Uji** (`id.tege.video.hotfix`), sama dengan 0.2.1 Uji. Kunci uji CI dipertahankan; kesamaan sertifikat APK perlu diverifikasi sebelum menganggap update langsung didukung. Paket produksi tetap `id.tege.video`. Data dan berkas sementara lama tidak dihapus atau dipulihkan otomatis.

## Fitur

- 1080p30 sebagai pilihan awal; 4K30 jika kamera/encoder mendukung. 1080p60 dapat memakai sesi standar atau constrained high-speed 120+ fps dengan sampling 60 fps waktu normal. Jalur high-speed ditandai HS (uji), tanpa kontrol manual/OIS/EIS; perlu verifikasi Xiaomi. AVC/HEVC.
- Warna Natural, Cinema hangat, Teal lembut, Monokrom. Impor **3D LUT `.cube` hingga 33³ / 4 MiB**, intensitas 0–100%, pilihan pratinjau saja atau diterapkan ke video.
- Fokus manual dan perpindahan A/B, ISO/shutter, EV, AE lock, preset WB/AWB lock, pilihan OIS atau EIS, lampu kontinu jika tersedia.
- Audio kamera, mikrofon kabel/USB, atau tanpa audio. Peak meter saat merekam.
- Panel LUT di luar preview; tombol menampilkan pilihan warna, OFF/EIS/OIS dan audio aktif dan preset peredaman. About ringkas dengan lisensi terpisah.
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
