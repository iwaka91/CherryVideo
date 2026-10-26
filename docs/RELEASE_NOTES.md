Cherry Video 0.3.3 beta — Langkah 3: kontrol sentuh dan panel bawah. Panel Fokus / Exposure / WB (mode Pro) kini muncul di bawah preview dan tidak menutupi gambar. Ketuk preview untuk fokus di titik itu; tahan lama untuk mengunci AE/AF (muncul tombol "Buka kunci"; ketuk layar juga membuka kunci); geser jari naik/turun untuk mengatur terang. Kunci dan geser terang tidak tersedia pada 60 fps high-speed dan saat fokus/exposure manual Pro. Toast "Menyimpan…" muncul sekali bila penyimpanan video lebih dari sekitar 1 detik (semua rekaman). Bokeh, Cinematic, dan peringatan panas dari 0.3.2 beta4 tidak diubah. Belum diuji di Xiaomi 13T; titik fokus, kunci, dan geser terang hanya bisa dibuktikan di HP (emulator tidak membuktikannya). versionCode 11.

Cherry Video 0.3.2 beta4 — Langkah 2: Bokeh langsung (eksperimen), Cinematic Look, dan peringatan HP panas. Bokeh: tombol "Bokeh (eksperimen)" di Lainnya, mati secara bawaan, hanya orang, hanya 1080p/720p 30 fps ke bawah (60 fps dan 4K tidak memakai Bokeh); slider kekuatan blur; tepi dihaluskan; berlaku di preview dan di video. Cinematic: tombol "Cinematic" (mati secara bawaan) dengan Look Cinematic 1/2, grain, vinyet tipis, slider kekuatan, bilah 2,39:1 di preview (opsi ikut masuk ke video). Panas: di beranda, saat HP panas (status sedang ke atas) muncul peringatan "HP panas, dinginkan HP dulu sebelum mulai merekam." dan tombol rekam tidak bisa memulai (tiap ketukan memunculkan peringatan lagi) sampai suhu turun. Saat merekam: bila panas berat, hitung mundur 30 detik lalu rekaman berhenti dan tersimpan; panas kritis menyimpan segera. Bila FPS turun saat Bokeh: hanya teks peringatan. Belum diuji di Xiaomi 13T; emulator tidak bisa menjalankan segmentasi (hanya arm64). versionCode 10. Fitur lain sama seperti 0.3.2 beta3.

Cherry Video 0.3.2 beta3 — tombol Uji Bokeh di Pengaturan → Diagnostik: segmentasi orang MediaPipe di dalam HP (tanpa internet) diukur ±15 detik pada GPU lalu CPU (waktu per gambar, FPS, suhu). Tidak merekam dan tidak mengubah pengaturan. Lisensi MediaPipe (Apache 2.0) di Tentang. versionCode 9. Fitur lain sama seperti 0.3.2 beta2.

Cherry Video 0.3.2 beta2 — Uji telefoto menyapu zoom pada kamera logis tersembunyi (misalnya ID 4); cubit zoom memakai hitungan jarak dua jari sendiri; ringkasan kegagalan uji di CI. versionCode 8. Fitur lain sama seperti 0.3.2 beta di bawah.

Cherry Video 0.3.2 beta — zoom dua jari, kamera Belakang/Depan, Simpel | Pro di Lainnya, LUT Cinematic, uji telefoto.

Zoom: cubit dua jari kini berfungsi (preview menerima sentuhan sejak jari pertama). Badge, tombol, dan slider mengikuti laporan kamera; teks singkat bila profil high-speed membatasi zoom; zoom tidak menghentikan rekaman. Pintasan 0.6× | 1× | 2× pada kamera belakang (hanya yang didukung).

Kamera: lensa belakang digabung menjadi "Belakang"; tombol Balik kamera berpindah Depan/Belakang. Pilihan lensa fisik ada di Pengaturan → Diagnostik → Pilih lensa teknis (uji).

Simpel | Pro: sakelar ada di laci Lainnya; penanda PRO di header saat Pro aktif.

Warna: LUT bawaan baru Cinematic 1 dan Cinematic 2 (Natural, Hangat, Teal, Mono tetap).

Diagnostik: tombol Uji telefoto mencatat tiap tahap (terdaftar, metadata, buka kamera, preview, frame) dan menyapu zoom untuk melihat apakah lensa berpindah. Hasilnya bukti, bukan jaminan telefoto dapat dipakai.

Qualification: JVM tests (zoom preset, analisis perpindahan lensa), lint, build, uji emulator Android 15/16 termasuk cubit lewat dispatch sentuhan nyata (dilewati bila kamera emulator tanpa zoom), Balik kamera, sakelar Pro, dan shader Cinematic. Hasil emulator tidak membuktikan perilaku Xiaomi 13T: uji cubit, pindah lensa, dan telefoto di HP.

APK label/package: Cherry Video Uji / id.tege.video.hotfix, versionCode 8. Kunci uji CI sama dengan 0.3.0/0.3.1, sehingga seharusnya terpasang menimpa. Personal debug beta, SDR 8-bit.
