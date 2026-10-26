# Daftar update Cherry Video — handoff sesi berikutnya

Tanggal keputusan: **25 Oktober 2026**. Basis pemeriksaan: **0.3.0-beta**, Xiaomi 13T **2306EPN60G**, Android 16 / API 36.

Dokumen ini mencatat permintaan dan hasil pengujian pengguna. **Perubahan aplikasi di bawah belum diimplementasikan.** Pembuatan dokumen ini tidak menjadi perintah untuk langsung mengerjakan seluruh catatan. Baca ruang lingkup terbaru sebelum melanjutkan di sesi lain.

## Rencana yang disepakati — 26 Oktober 2026 (urutan kerja)

**Keputusan pengguna:** zoom 2× tetap potong digital (opsi C); kamera tele tidak dipakai (Xiaomi tidak memberi perpindahan otomatis; ID 2 hanya terbuka langsung). Fokus berikutnya: Bokeh AI.

**Langkah 1 — Uji Bokeh (diagnostik, tanpa merekam) — dikerjakan di 0.3.2-beta3, menunggu uji Xiaomi.** Tombol di Pengaturan → Diagnostik. Memuat MediaPipe (segmentasi orang, di dalam HP, tanpa internet) dan mengukur waktu proses per gambar, FPS, dan suhu selama ±15 detik. Tidak mengubah rekaman.

**Langkah 2 — Bokeh langsung + penghalusan tepi + Cinematic Look.**
- Tombol "Bokeh (eksperimen)", mati secara bawaan; sakelar dan slider kekuatan blur. Hanya orang; hanya 1080p 30 fps ke bawah (720p 30 fps ikut). Mode 60 fps dan 4K tidak mengaktifkan Bokeh.
- **Tidak ada mati otomatis untuk Bokeh.** Bila HP panas: peringatan "HP panas, video akan dimatikan" dengan hitung mundur 30 detik; saat habis, rekaman berhenti, tersimpan, dan kembali ke beranda kamera (tanpa tombol "Matikan Bokeh"). Bila FPS turun: hanya teks peringatan.
- Penghalusan tepi (rambut, kedipan topeng).
- **Cinematic Look:** tombol "Cinematic" (mati secara bawaan) berisi Look (Cinematic 1/2), grain film, vinyet tipis, slider kekuatan, dan bilah 2,39:1 (preview, dengan pilihan ikut masuk ke video). 24 fps dan petunjuk shutter 1/48 (hanya Pro, hanya saran) ditunda dan diputuskan terpisah.
- Ukuran APK boleh bertambah belasan MB; lisensi MediaPipe dicantumkan di Tentang.

**Langkah 3 — Update 0.3.3 + toast "Menyimpan…":** toast "Menyimpan…" muncul bila penyimpanan video memakan waktu lama (berlaku untuk semua rekaman). Panel bawah untuk Fokus/Pull, Exposure, WB (tidak menutupi preview); ketuk untuk fokus; tahan lama untuk kunci AE/AF; buka kunci dengan ketuk atau tombol "Buka kunci"; geser naik/turun mengatur terang. Kunci tidak tersedia pada 60 fps high-speed dan tidak dipakai saat fokus/exposure manual Pro. Titik fokus hanya bisa dibuktikan di HP.

**Catatan:** hasil emulator tidak membuktikan kecepatan atau kualitas Bokeh di Xiaomi 13T. Semua langkah menunggu perintah eksplisit ("Update Kode") sebelum kode diubah.

## Status terbaru — 0.3.2-beta3 (Langkah 1: Uji Bokeh)

**Selesai di kode (belum diuji di Xiaomi; emulator hanya membuktikan model termuat dan gambar terproses, bukan kecepatan):**
- Tombol **Uji Bokeh (±15 detik)** di Pengaturan → Diagnostik (`BokehProbe.kt`). Kamera aplikasi dilepas dulu seperti Uji telefoto, lalu dibuka lagi.
- Kamera belakang dibuka dengan aliran 1080p 30 fps (dibuang, pengganti beban rekaman) plus **aliran gambar kecil tambahan** (640x360 bila tersedia) ke MediaPipe segmentasi orang (model Selfie Segmenter, di dalam APK).
- Diukur bergantian: **GPU ±10 detik, lalu CPU ±5 detik**. Dicatat: waktu per gambar (rata-rata, terlambat 10%, maksimum; konversi dan segmentasi terpisah), FPS diproses vs fps kamera, area orang terdeteksi, suhu baterai, status panas, dan headroom panas per detik. 5 gambar awal tiap tahap tidak dihitung (pemanasan).
- Hasil: ringkasan Indonesia + JSON lengkap + Ekspor JSON (`CherryVideo-uji-bokeh.json`). Tidak merekam dan tidak mengubah pengaturan.
- Model diunduh saat build oleh CI (`tools/fetch_models.sh`); aplikasi tidak pernah mengunduh. Lisensi MediaPipe (Apache 2.0) di Tentang dan NOTICE. Pustaka MediaPipe x86 32-bit tidak ikut APK.
- Versi 0.3.2-beta3 (versionCode 9). Paket `id.tege.video.hotfix` dan kunci uji tidak berubah.

**Yang perlu Bapak uji di HP:** buka Pengaturan → Diagnostik → **Uji Bokeh**, arahkan kamera belakang ke orang selama ±15 detik, lalu kirim JSON-nya. Langkah 2 belum dimulai.

## Riwayat — 0.3.2-beta2

**Hasil uji pengguna pada 0.3.2-beta (Xiaomi 13T, 25 Okt 2026):** cubit zoom, tombol 0.6×, sakelar Pro, dan LUT Cinematic 1 dan 2 berfungsi. Tombol 2× tetap tidak berpindah ke kamera tele.

**Temuan Uji telefoto (bukti dari HP):**
- ID tersembunyi **2** = telefoto (fokus 7,1 mm, sekitar 2× kamera utama); preview terbuka dan frame masuk bila dibuka langsung.
- ID tersembunyi **4** = kamera logis gabungan (lensa fisik 2, 3, 7; zoom 0,6×–20×); preview terbuka. ID 5 (lensa 2 + 7, zoom 1×–4×) dan ID 6 juga terbuka.
- Kamera 0 yang dipakai aplikasi **tidak pernah berpindah ke tele** sampai zoom 10× (lensa aktif tetap 7). Zoom 2× hanya memotong gambar lensa utama.
- Belum terbukti: apakah ID 4 berpindah ke tele saat zoom ≥2×, dan apakah perekaman video dari ID tersembunyi diizinkan.

**0.3.2-beta2 (versionCode 8):** Uji telefoto kini juga menyapu zoom pada setiap kamera logis tersembunyi (misalnya ID 4) dan mencatat lensa fisik yang terlihat. Cubit zoom memakai hitungan jarak dua jari sendiri (detektor bawaan Android tidak aktif pada sentuhan uji). CI kini menampilkan ringkasan uji emulator yang gagal sebagai anotasi.

**Rencana berikut (0.3.3), urutan:** (0) bila sapuan ID 4 membuktikan perpindahan ke tele: pakai ID 4 sebagai "Belakang" dengan cadangan ke ID 0 dan uji rekam; lencana lensa aktif (UW/1×/Tele). (1) Panel bawah untuk Fokus/Pull, Exposure, WB (tidak menutupi preview). (2) Ketuk untuk fokus; tahan lama untuk kunci AE/AF; buka kunci dengan ketuk atau tombol "Buka kunci"; geser naik/turun mengatur terang. Kunci tidak tersedia pada 60 fps high-speed dan tidak dipakai saat fokus/exposure manual Pro. Titik fokus hanya bisa dibuktikan di HP.

### Riwayat — 0.3.2-beta


Hasil uji pengguna pada 0.3.1-beta (Xiaomi 13T): cubit tidak berfungsi; kamera utama sudah berpindah ke wide otomatis di zoom 0.6 (sama seperti kamera bawaan); telefoto tidak pernah aktif sampai zoom 10×; peralihan Simpel ↔ Pro kurang terasa.

**Bagian 1 — selesai di kode (belum diuji di Xiaomi; emulator tidak membuktikan cubit nyata, pindah lensa, atau telefoto):**

- **A. Zoom.** Preview kini menerima seluruh rangkaian sentuhan sejak jari pertama, sehingga cubit dua jari terbaca (penyebab: jari pertama sebelumnya ditolak, jari kedua tidak pernah dikirim ke preview). Badge, tombol, dan slider mengikuti laporan nyata kamera. Teks singkat di layar bila profil high-speed atau lensa membatasi zoom. Zoom tidak menghentikan rekaman. Pembatas high-speed tidak dihapus.
- **B. Kamera.** Kamera belakang digabung menjadi satu **Belakang**; tombol **Balik kamera** berpindah Depan ↔ Belakang (terkunci saat merekam). Pintasan zoom 0.6× | 1× | 2× pada kamera belakang, hanya yang didukung kamera. Pilihan lensa fisik dipindah ke Pengaturan → Diagnostik → **Pilih lensa teknis (uji)**; ID 7 yang sama dengan Utama tidak diulang di pemilih (data tetap ada di Diagnostik). Pilihan kamera lama (indeks) dikonversi ke Belakang/Depan.
- **C. Simpel | Pro.** Sakelar dipindah ke laci **Lainnya** (mati = Simpel, hidup = Pro). Penanda hijau **PRO** di header saat Pro aktif. Pro menambah Fokus · Exposure · WB. Preferensi tersimpan, terkunci saat merekam. Tata letak Lainnya mengikuti gaya layar utama; tidak ada mockup baru.
- **D. LUT bawaan baru:** **Cinematic 1** (kontras film, bayangan teal, highlight hangat) dan **Cinematic 2** (lembut, hitam terangkat, hangat, warna sedikit pudar). Empat LUT lama tidak berubah.
- **E. Uji telefoto** (Pengaturan → Diagnostik). Melepas kamera sementara, lalu mencatat per tahap untuk setiap ID kandidat: terdaftar, metadata terbaca, kamera terbuka, sesi preview, frame masuk. Menyapu zoom (0.6 → 10×) pada kamera utama dan mencatat lensa fisik aktif serta panjang fokus tiap langkah, lalu menyimpulkan apakah lensa berpindah. Hasil bisa disalin atau diekspor sebagai JSON. Tidak merekam dan tidak mengubah pengaturan. **Tidak ada jaminan telefoto bisa dipakai;** hasilnya adalah bukti.
- **Versi:** 0.3.2-beta (versionCode 7). Paket `id.tege.video.hotfix` dan kunci uji tidak berubah.

**Yang perlu Bapak uji di HP:** cubit/rentang di Simpel dan Pro (juga saat merekam, setelah rotasi, grid aktif); tombol 0.6/1/2×; Balik kamera; sakelar Simpel | Pro di Lainnya dan penanda PRO; LUT Cinematic 1 dan 2; lalu jalankan **Uji telefoto** dan kirim JSON-nya.

**Bagian 2 — terpisah, belum dikerjakan (urutan disepakati):** (1) Cinematic Look: warna film + efek film halus, bilah sinematik opsional; 24 fps butuh profil baru dan keputusan terpisah. (2) Bokeh AI: eksperimen, hanya orang, MediaPipe, kinerja diukur dulu dan mati otomatis bila FPS turun atau HP panas.

**Tidak dikerjakan:** keterangan "Guru SMA Negeri 3 Maliku" di Tentang dan penyimpanan mockup ke `docs/mockup` (diputuskan diabaikan), mockup Lainnya/landscape.

### Riwayat — 0.3.1-beta

- **Selesai di kode (belum diuji di Xiaomi):** UI potret baru sesuai mockup ("Mockup Cherry Video", layar utama Simpel potret) dan sakelar **Simpel | Pro** (butir 2 di bawah). Mode dipilih dengan ketuk; geser tidak diwajibkan pengguna.
- **Keputusan pengguna:** UI dirombak lebih dulu; perbaikan gestur zoom dua jari (butir 1) dikerjakan terpisah setelahnya, di UI baru. Keterangan saat profil membatasi zoom: teks singkat di layar.
- **Belum dikerjakan:** butir 1 (gestur zoom). Laci Lainnya dan landscape belum punya mockup; tata letaknya mengikuti gaya layar utama.
- Pengguna bersedia uninstall bila APK baru tidak bisa menimpa versi lama.

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

- [x] Ganti tombol tunggal `Simple`/`Pro` dengan sakelar dua posisi **Simpel | Pro**; kedua label selalu terlihat.
- [x] Pilihan **aktif hijau**, pilihan **tidak aktif abu-abu**, berlaku sama untuk kedua mode.
- [ ] Posisi/penanda aktif mengikuti mode, dapat dipilih dengan mengetuk label, dan mendukung perpindahan bergeser sesuai desain sakelar.
- [x] Pertahankan preferensi mode tersimpan, pembatasan pergantian saat merekam, serta tampilan potret/landscape yang tidak menutupi kontrol penting.

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
