# Daftar update Cherry Video — handoff sesi berikutnya

Tanggal keputusan: **25 Oktober 2026**. Basis pemeriksaan: **0.3.0-beta**, Xiaomi 13T **2306EPN60G**, Android 16 / API 36.

Dokumen ini mencatat permintaan dan hasil pengujian pengguna. **Perubahan aplikasi di bawah belum diimplementasikan.** Pembuatan dokumen ini tidak menjadi perintah untuk langsung mengerjakan seluruh catatan. Baca ruang lingkup terbaru sebelum melanjutkan di sesi lain.

## Fokus update yang diminta

### 1. Perbaiki zoom dengan dua jari

- [ ] Cubit pada preview untuk memperkecil; rentangkan dua jari untuk memperbesar.
- [ ] Gestur harus berfungsi di mode Simpel dan Pro, pada kamera/profil yang mendukung zoom.
- [ ] Tampilkan rasio aktual dan sinkronkan indikator serta slider dengan gestur. Slider boleh menjadi kontrol tambahan.
- [ ] Pertahankan batas zoom perangkat; jangan menampilkan perubahan rasio tanpa menerapkannya ke kamera.

**Petunjuk pemeriksaan:** `MainActivity.kt` sudah memiliki `ScaleGestureDetector`, tetapi listener preview mengembalikan `false` untuk sentuhan awal satu jari. Periksa konsumsi rangkaian sentuhan sejak `ACTION_DOWN`, distribusi sentuhan pada overlay grid, serta pergantian satu/dua jari. Ini kandidat penyebab dari pemeriksaan kode, belum pembuktian di perangkat.

`setZoom()` juga memblokir zoom ketika `selected.highSpeed` atau engine sibuk. Pisahkan keterbatasan profil tersebut dari bug gestur; jangan menghapus pembatas tanpa verifikasi dukungan kamera.

**Kriteria selesai:** uji cubit/rentang berulang pada Utama ID 0 di 1080p30, dalam Simpel dan Pro; rasio dan bidang pandang benar-benar berubah. Periksa juga saat merekam pada profil yang mendukung, setelah rotasi layar, dan saat grid aktif. Tindakan zoom tidak boleh menghentikan rekaman. Jika profil membatasi zoom, tampilkan keterangan yang jelas.

### 2. Ubah kontrol Simpel | Pro

- [ ] Ganti tombol tunggal `Simple`/`Pro` dengan sakelar dua posisi **Simpel | Pro**; kedua label selalu terlihat.
- [ ] Pilihan **aktif hijau**, pilihan **tidak aktif abu-abu**, berlaku sama untuk kedua mode.
- [ ] Posisi/penanda aktif mengikuti mode, dapat dipilih dengan mengetuk label, dan mendukung perpindahan bergeser sesuai desain sakelar.
- [ ] Pertahankan preferensi mode tersimpan, pembatasan pergantian saat merekam, serta tampilan potret/landscape yang tidak menutupi kontrol penting.

**Koreksi keputusan lama:** warna biru untuk Pro dibatalkan. Keputusan terakhir pengguna adalah hijau untuk aktif dan abu-abu untuk tidak aktif.

## Di luar lingkup update ini

| Topik | Keputusan terakhir |
| --- | --- |
| Lag preview/hasil video | **Abaikan untuk perbaikan saat ini.** Pengguna menemukan lag juga pada kamera bawaan dan menduga HP panas. Panas adalah dugaan pengguna, belum diagnosis terukur. |
| Ekspor JSON kosong | **Abaikan untuk saat ini.** Pengguna dapat menyalin teks melalui Diagnostik → JSON lengkap → pilih semua/salin. Tidak perlu mengerjakan perbaikan ekspor dalam paket ini. |
| Natural / Suara Jernih / Reduksi Kuat | **Pengujian ditunda.** Pengguna akan menguji lagi dengan sampel yang sama. Jangan mengubah algoritma atau preset berdasarkan rekaman berbeda kondisi. |

## Catatan lanjutan — belum menjadi fokus implementasi

### Telefoto / kamera bawah

- Pengguna menguji dengan menutup lensa: Utama ID 0 dan pilihan fisik ID 7 memakai lensa utama atas yang sama. ID 3 ultrawide dan ID 1 depan cocok.
- JSON perangkat: ID 0 melaporkan `physicalIds: ["3", "7"]`. Pada zoom sekitar `2.4048`, kamera terpilih `0`, tetapi `activePhysicalId` tetap `7`; rekaman terakhir juga mencatat ID fisik `7`.
- Kandidat telefoto ID 2 berasal dari konfigurasi GCam model yang sama. **ID 2 belum terbukti tersedia atau dapat direkam pada HP pengguna.** Ia tidak dilaporkan sebagai anggota ID 0 dalam JSON ini; jangan menyimpulkan hilang hanya karena filter profil video.
- Jika kelak dilanjutkan, bedakan penemuan ID, keberhasilan membaca metadata, keberhasilan sesi preview, dan keberhasilan rekaman. Catat alasan kegagalan. Rapikan duplikasi ID 7 dalam pemilih pengguna tanpa menghilangkan data diagnostik; Utama ID 0 mempertahankan akses zoom.

### 1080p60 dan bukti video

- `1003512830.mp4`: 1920×1080, 126 frame, **30,0 fps aktual**, jeda frame sekitar 33,3 ms. Pengguna menyebut memilih 1080p60; hasil file belum 60 fps.
- `1003512834.mp4`: durasi kontainer sekitar 17,74 detik, 292 frame, **16,7 fps aktual**, jeda maksimum sekitar 466,6 ms. Penanganan lag ini dikeluarkan dari fokus atas instruksi terbaru pengguna.
- Kode 0.3.0 dapat menawarkan keluaran 60 fps melalui pengambilan high-speed 120 fps. Dukungan metadata bukan bukti keluaran aktual. Masalah pencapaian 60 fps dicatat untuk tindak lanjut terpisah, tidak otomatis dikerjakan dalam paket zoom/UI ini.

### Bokeh / cinematic di mode Simpel

- Pengguna menanyakan kemungkinan sakelar cinematic/bokeh. **Belum ada keputusan final untuk implementasi.**
- Preset LUT dapat memberi gaya warna cinematic, tetapi tidak membuat latar buram.
- Bokeh video membutuhkan pemisahan subjek/latar atau kedalaman dan pemrosesan blur. Kandidat komponen terbuka yang pernah dibahas: MediaPipe Image Segmenter. Kualitas tepi subjek, dukungan objek selain manusia, serta kinerja di perangkat harus diuji dahulu.
- Jangan menjanjikan bokeh video dari ekstensi bokeh foto Camera2/CameraX, dan jangan menggabungkan bokeh AI ke paket perbaikan zoom/UI tanpa keputusan baru.

## Titik masuk kode dan batas pekerjaan

- `app/src/main/kotlin/id/tege/video/MainActivity.kt`: gestur preview, `setZoom()`, kontrol mode dan penyimpanan preferensi.
- `camera/src/main/kotlin/id/tege/video/camera/CameraCatalog.kt`: batas zoom, katalog kamera/profil.
- `camera/src/main/kotlin/id/tege/video/camera/CameraEngine.kt`: penerapan zoom Camera2 dan ID fisik aktif.
- APK pengujian saat ini: **Cherry Video Uji**, paket `id.tege.video.hotfix`. Saat kelak membuat APK pembaruan, pertahankan identitas dan kunci penandatanganan uji yang ada serta verifikasi sertifikat; jangan meminta pengguna menghapus aplikasi untuk pembaruan rutin.
- Jangan menyatakan selesai hanya berdasarkan build/emulator. Zoom harus diuji sebagai perubahan bidang pandang nyata pada HP; warna dan sakelar mode harus diperiksa dalam potret dan landscape.
- Dokumen ini dibuat tanpa perubahan kode aplikasi, build APK baru, atau perubahan pemrosesan audio.
