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

## Status terbaru — 0.4.0-beta (Dokumentasi & Bicara) — 27 Okt 2026

**Hasil uji 0.3.3/0.3.4 di Xiaomi 13T (27 Okt):** ketuk fokus, kunci AE/AF, panel Fokus/Exposure/WB, toast "Menyimpan…", Cinematic: semua berjalan baik (laporan Pak Guru). Bokeh kamera depan: topeng akurat saat diam, sedikit tertinggal saat menoleh cepat; blur 6% wajah tajam. Kamera belakang: kipas angin masih dianggap orang, blur kasar (belum diputuskan). Uji mode bokeh sistem: 13T tidak menawarkan bokeh/nilai F ke aplikasi lain (jalur ditutup). Bokeh dijeda untuk pengujian.

**Keputusan pengguna:** semua ide peningkatan untuk dua penggunaan (dokumentasi kegiatan; bicara sendiri menjelaskan materi) dibungkus dalam satu update.

**Selesai di kode (belum diuji di Xiaomi; kompilasi dan uji hanya di CI):**
- **Lainnya → Dokumentasi:** preset Dokumentasi (kamera belakang, filter angin, tombol volume rekam, audio Natural); nama kegiatan → folder `Movies/CherryVideo/<nama>` dan nama berkas diawali nama kegiatan; judul kegiatan + tanggal di video; filter angin (saring gemuruh <±100 Hz); hemat ruang (bitrate ±55%); zebra (preview saja); mode cahaya redup (peredam bintik terkuat, fps tetap); layar redup saat merekam (sentuhan pertama hanya menyalakan layar); tombol volume mulai/stop.
- **Lainnya → Bicara:** preset Bicara (kamera depan, hitung mundur 3 dtk, garis bantu, kunci exposure+WB saat merekam, peringatan cahaya, Suara Jernih); hitung mundur Mati/3/5/10 (ketuk angka untuk batal); teleprompter (naskah, ukuran huruf, kecepatan; tidak masuk video; ketuk untuk berhenti/lanjut); garis bantu wajah/mata; cermin preview depan (bisa dimatikan); layar sebagai lampu isi (bingkai putih + layar paling terang, kamera depan); kunci exposure+WB saat merekam; peringatan terlalu gelap/membelakangi cahaya (diukur tiap detik, muncul setelah 3 detik sama); mulai otomatis saat wajah terlihat (sekali, hanya bila kamera menawarkan deteksi wajah); teks judul materi (lower third, ikut masuk video).
- **Saat merekam (sisi kanan preview):** Jeda/Lanjut (berkas tetap satu, sambungan dimulai dari keyframe agar suara dan gambar tetap sinkron), Foto (JPEG ke `Pictures/CherryVideo…`, tampilan sama dengan video), Tandai (penanda waktu). Indikator level suara di kiri atas, merah + peringatan bila suara pecah. Peringatan baterai ≤10% dan sisa ruang (±menit) di baris info.
- **Setelah merekam (bilah di atas tombol rekam) dan di Galeri:** Buang take terakhir (konfirmasi), Potong (tanpa encode ulang; awal dibulatkan ke keyframe ≤1 dtk; penanda bisa dipakai), Rapikan bagian diam (dari log level suara 100 ms yang direkam; jeda diam >±1,5 dtk dibuang; hanya video 0.4 ke atas), Bagikan (WhatsApp mengecilkan sendiri). Hasil edit = berkas baru di folder yang sama; asli tidak diubah.
- **Bokeh → Latar: Blur | Gambar:** gambar pilihan menggantikan latar di belakang orang (ikut masuk video; perlu Bokeh aktif).
- Galeri kini menampilkan juga video di folder kegiatan.
- Uji JVM baru `ShootToolsTest` (jeda, filter angin, potong diam, cek cahaya, nama berkas); uji perangkat baru `pauseResumeAndLosslessCutStaySynchronized`.
- Tidak diubah: penyimpanan dasar, sinkron A/V rekaman biasa, LUT, audio, Bokeh (selain opsi latar), Cinematic. Versi 0.4.0-beta (versionCode 13). Paket dan kunci uji tetap.

**Belum dikerjakan / ditunda:** kompres ulang sendiri untuk WhatsApp (diganti Hemat Ruang + kompres bawaan WhatsApp); perluasan sensitivitas Bokeh kamera belakang; 24 fps; petunjuk shutter 1/48.

**Yang perlu Bapak uji di HP:** Jeda/Lanjut lalu putar videonya (suara pas dengan gambar?); Foto saat merekam; posisi teks judul+tanggal dan lower third di video tegak dan mendatar, kamera depan dan belakang; teleprompter; hitung mundur; lampu isi; peringatan cahaya; mulai saat wajah terlihat (apakah 13T menawarkan deteksi wajah); Potong dan Rapikan bagian diam; latar gambar; filter angin di luar ruangan; hemat ruang (ukuran berkas). Risiko belum terbukti: arah teks/latar gambar di video (dihitung dari tag orientasi), dan foto saat merekam bisa membuat satu frame tersendat.

## Riwayat — 0.3.4-beta (Bokeh lebih halus) — 27 Okt 2026

**Hasil uji 0.3.3-beta di Xiaomi 13T (27 Okt):** kamera belakang: Bokeh salah mengira benda (motor, gerobak) sebagai orang, blur terlalu kasar. Kamera depan (dekat): cukup bagus, tetapi blur terlalu kuat (6% cukup) dan tepi kasar serta tertinggal saat orang bergerak. Kamera bawaan Xiaomi punya pengaturan nilai F pada mode bokeh. Langkah 3 (ketuk fokus, kunci, panel bawah) belum dilaporkan.

**Keputusan pengguna:** blur standar 6% tetapi bisa diubah; tepi lembut + blur bertahap; Bokeh tetap untuk kamera belakang juga; uji mode bokeh sistem; topeng mengikuti tepi gambar asli; tampilan nilai F; ditambah sensitivitas, ikut gerak Halus/Cepat, lihat topeng (uji), dan preset.

**Selesai di kode (belum diuji di Xiaomi; kompilasi dan uji JVM hanya di CI):**
- **Blur bawaan 6%:** nilai lama yang tersimpan diganti 6% satu kali saat pembaruan; setelah itu nilai pilihan Bapak disimpan.
- **Nilai F:** slider tampil "Bukaan F13 · blur 6%" (0% = F16, 100% = F1.0). Hanya simulasi; lensa HP tidak berubah. Tombol Bokeh menampilkan nilai F saat aktif.
- **Tepi lembut + blur bertahap:** peralihan orang ke latar berupa gradasi; latar dekat orang sedikit kurang buram, makin jauh makin buram.
- **Topeng mengikuti tepi gambar asli:** di sekitar tepi, topeng kecil (640x360) dirapikan memakai warna gambar 1080p, sehingga rambut dan bahu lebih rapi. Hanya dihitung di daerah tepi agar beban GPU kecil.
- **Sensitivitas deteksi orang** (bawaan 50%): turunkan bila benda ikut tajam, naikkan bila bagian tubuh ikut buram.
- **Ikut gerak:** Halus (tepi tenang, sedikit tertinggal) atau Cepat (cepat mengikuti, tepi bisa berkedip).
- **Preset:** Potret (6%), Sinematik (12%, tepi lebih lembut, gradasi kuat), Lembut (3%, tepi sangat lembut). Mengubah slider/ikut gerak membuat preset tidak terpilih (kustom).
- **Lihat topeng (uji):** area orang diberi warna hijau di preview saja; tidak masuk video; mati lagi tiap aplikasi dibuka.
- **Diagnostik → Uji mode bokeh sistem:** membaca (tanpa membuka kamera, tanpa merekam) apakah HP menawarkan bokeh video/foto sistem atau ekstensi Bokeh ke aplikasi lain; hasil + JSON.
- Laporan video mencatat nilai F dan pengaturan Bokeh. Uji JVM baru: `BokehTuningTest`.
- Tidak diubah: rekaman, audio, LUT, Cinematic, panas, Langkah 3. Versi 0.3.4-beta (versionCode 12). Paket dan kunci uji tidak berubah.

**Yang perlu Bapak uji di HP:** kamera depan dan belakang dengan preset Potret; nyalakan Lihat topeng untuk melihat area orang; geser sensitivitas sampai motor/benda tidak ikut tajam; coba Halus vs Cepat saat bergerak; rekam 1–2 menit (suhu, FPS, sinkron suara); jalankan Uji mode bokeh sistem dan kirim hasilnya. Risiko belum terbukti: beban GPU dan panas perapian tepi, dan apakah sensitivitas cukup untuk salah deteksi di kamera belakang (model dibuat untuk swafoto).

## Riwayat — 0.3.3-beta (Langkah 3: kontrol sentuh + panel bawah) — 26 Okt 2026

**Selesai di kode (belum diuji di Xiaomi; kompilasi dan uji JVM hanya di CI):**
- **Panel bawah:** Fokus / Pull, Exposure, WB (mode Pro) kini panel di bawah preview (sama seperti Bokeh/Cinematic), tidak lagi jendela yang menutupi gambar. Isi dan fungsi sama.
- **Ketuk untuk fokus:** titik ketuk dipetakan ke sensor (memperhitungkan rotasi, cermin kamera depan, tepi hitam preview, dan zoom) lalu dipakai sebagai area AF dan AE. Cincin putih muncul sebentar di titik itu.
- **Kunci AE/AF:** tahan lama di preview; fokus ke titik itu, ±0,7 detik kemudian AE dikunci dan AF ditahan (pemicu fokus sekali). Cincin hijau tetap dan tombol "AE/AF terkunci · Buka kunci" muncul. Buka kunci dengan ketuk layar atau tombol. Kunci dilepas otomatis bila kamera dimulai ulang atau bila fokus/exposure manual dipilih.
- **Geser terang:** geser satu jari naik/turun di preview mengubah kompensasi exposure (EV) per langkah, tampil sebagai teks singkat. Tidak aktif saat terkunci (ada pesan) dan saat exposure manual.
- **Tidak tersedia:** 60 fps high-speed (kunci, ketuk fokus, geser terang) dan fokus/exposure manual Pro (kunci). Pesan singkat muncul di layar.
- **Toast "Menyimpan…":** muncul sekali bila setelah Stop penyimpanan belum selesai dalam ±1 detik; berlaku untuk semua rekaman (juga saat berhenti karena panas).
- Tidak diubah: Bokeh, Cinematic, peringatan panas, rekam/audio/LUT/zoom. 24 fps dan petunjuk shutter 1/48 tetap ditunda.
- Uji JVM baru: `TapMathTest` (pemetaan titik, area, geser terang).
- Versi 0.3.3-beta (versionCode 11). Paket dan kunci uji tidak berubah.

**Yang perlu Bapak uji di HP:** ketuk fokus (dekat/jauh, potret dan lanskap, kamera depan); tahan lama untuk kunci lalu geser/ganti pandangan (fokus dan terang harus tetap); buka kunci lewat ketuk dan tombol; geser naik/turun untuk terang (juga saat merekam); panel Fokus/Exposure/WB tidak menutupi preview; toast "Menyimpan…" setelah rekaman panjang; rekaman biasa tidak berubah. Risiko belum terbukti: ketepatan titik fokus di Xiaomi 13T (pemetaan rotasi/cermin), dan apakah kunci AF menahan fokus selama rekaman.

## Riwayat — 0.3.2-beta4 (Langkah 2: Bokeh langsung + Cinematic + peringatan panas)

**Hasil Uji Bokeh di Xiaomi 13T (26 Okt 2026, 16,6 detik):** kamera 0, aliran 640x360 plus aliran 1080p 30 fps yang dibuang. GPU: rata-rata 24,2 ms (segmentasi 18,1; konversi 6,1), p90 29,4, maks 36,1, 29,7 FPS diproses dari 29,6 FPS kamera, muat 1,06 dtk. CPU: rata-rata 24,0 ms (segmentasi 18,0; konversi 5,9), p90 29,5, maks 37,3, 29,9 FPS dari 29,9, muat 21 ms. Area orang ±19% (GPU) / ±39% (CPU). Baterai 29,9 °C, suhu normal. Catatan: GPU dan CPU hampir sama (GPU mungkin tidak benar-benar dipakai); sebagian gambar melebihi 33 ms; uji tidak menggambar blur dan tidak menyimpan, jadi beban rekaman nyata dan panas rekaman panjang belum terbukti. Kesimpulan: Bokeh langsung di 1080p 30 fps layak dilanjutkan.

**Selesai di kode (belum diuji di Xiaomi; kompilasi dan uji JVM hanya di CI):**
- **Bokeh langsung:** tombol "Bokeh (eksperimen)" di Lainnya; mati secara bawaan dan mati lagi tiap aplikasi dibuka; slider kekuatan blur (tersimpan). Hanya orang, 1080p/720p 30 fps ke bawah, 16:9, bukan lensa teknis. Aliran gambar kecil 640x360 ke segmentasi MediaPipe (CPU), topeng dihaluskan terhadap waktu (kedipan, rambut), blur latar di GPU (turun ke 1/4 resolusi, 4 lintasan Gaussian) dan dipakai sama untuk preview dan video. Bila tidak ada orang di gambar, video tetap tajam. Mengubah sakelar memulai ulang kamera sebentar. Bila kamera menolak aliran tambahan atau Bokeh gagal, aplikasi jalan terus tanpa Bokeh dan memberi tahu.
- **Cinematic:** tombol "Cinematic" (mati secara bawaan); Look Cinematic 1/2, grain, vinyet tipis, satu slider kekuatan, bilah 2,39:1 di preview dengan pilihan ikut masuk ke video. Saat aktif menggantikan pilihan Warna / LUT sementara; pilihan lama kembali setelah dimatikan.
- **Tidak ada mati otomatis Bokeh.** Merekam + panas berat (status 3): peringatan "HP panas, video akan dimatikan dalam N detik" 30 detik, lalu rekaman berhenti, tersimpan, kembali ke beranda. Panas kritis (status 4): berhenti segera. FPS turun (<85% dua kali berturut-turut, saat Bokeh): hanya teks peringatan.
- **Peringatan panas di beranda (semua rekaman):** status panas sedang ke atas → toast 2–3 detik "HP panas, dinginkan HP dulu sebelum mulai merekam."; tombol rekam diredupkan dan tidak memulai, tiap ketukan memunculkan peringatan lagi; terbuka kembali saat suhu turun.
- Tentang dan Pengaturan diperbarui; Diagnostik memuat bagian "bokeh". 24 fps dan petunjuk shutter 1/48 tetap ditunda. **Langkah 3 (0.3.3) belum dikerjakan.**
- Versi 0.3.2-beta4 (versionCode 10). Paket dan kunci uji tidak berubah.

**Yang perlu Bapak uji di HP:** nyalakan Bokeh (1080p 30), rekam 1–2 menit, cek tepi rambut, suhu, FPS aktual, sinkron suara, hasil video; coba Cinematic (preview, bilah, dan hasil video); coba toast panas bila HP panas; pastikan rekaman tanpa Bokeh/Cinematic tidak berubah.

## Riwayat — 0.3.2-beta3 (Langkah 1: Uji Bokeh)

**Selesai di kode (belum diuji di Xiaomi; emulator hanya membuktikan model termuat dan gambar terproses, bukan kecepatan):**
- Tombol **Uji Bokeh (±15 detik)** di Pengaturan → Diagnostik (`BokehProbe.kt`). Kamera aplikasi dilepas dulu seperti Uji telefoto, lalu dibuka lagi.
- Kamera belakang dibuka dengan aliran 1080p 30 fps (dibuang, pengganti beban rekaman) plus **aliran gambar kecil tambahan** (640x360 bila tersedia) ke MediaPipe segmentasi orang (model Selfie Segmenter, di dalam APK).
- Diukur bergantian: **GPU ±10 detik, lalu CPU ±5 detik**. Dicatat: waktu per gambar (rata-rata, terlambat 10%, maksimum; konversi dan segmentasi terpisah), FPS diproses vs fps kamera, area orang terdeteksi, suhu baterai, status panas, dan headroom panas per detik. 5 gambar awal tiap tahap tidak dihitung (pemanasan).
- Hasil: ringkasan Indonesia + JSON lengkap + Ekspor JSON (`CherryVideo-uji-bokeh.json`). Tidak merekam dan tidak mengubah pengaturan.
- Model diunduh saat build oleh CI (`tools/fetch_models.sh`); aplikasi tidak pernah mengunduh. Lisensi MediaPipe (Apache 2.0) di Tentang dan NOTICE. Pustaka MediaPipe hanya untuk HP 64-bit ARM (seperti Xiaomi 13T) dan disimpan terkompresi agar tambahan ukuran tetap belasan MB. MediaPipe tidak punya versi x86_64, sehingga **emulator CI tidak bisa menguji segmentasi**; CI hanya memastikan kamera, model di APK, dan laporan berjalan.
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
