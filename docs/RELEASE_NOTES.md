Cherry Video 0.3.2 beta2 — Uji telefoto menyapu zoom pada kamera logis tersembunyi (misalnya ID 4); cubit zoom memakai hitungan jarak dua jari sendiri; ringkasan kegagalan uji di CI. versionCode 8. Fitur lain sama seperti 0.3.2 beta di bawah.

Cherry Video 0.3.2 beta — zoom dua jari, kamera Belakang/Depan, Simpel | Pro di Lainnya, LUT Cinematic, uji telefoto.

Zoom: cubit dua jari kini berfungsi (preview menerima sentuhan sejak jari pertama). Badge, tombol, dan slider mengikuti laporan kamera; teks singkat bila profil high-speed membatasi zoom; zoom tidak menghentikan rekaman. Pintasan 0.6× | 1× | 2× pada kamera belakang (hanya yang didukung).

Kamera: lensa belakang digabung menjadi "Belakang"; tombol Balik kamera berpindah Depan/Belakang. Pilihan lensa fisik ada di Pengaturan → Diagnostik → Pilih lensa teknis (uji).

Simpel | Pro: sakelar ada di laci Lainnya; penanda PRO di header saat Pro aktif.

Warna: LUT bawaan baru Cinematic 1 dan Cinematic 2 (Natural, Hangat, Teal, Mono tetap).

Diagnostik: tombol Uji telefoto mencatat tiap tahap (terdaftar, metadata, buka kamera, preview, frame) dan menyapu zoom untuk melihat apakah lensa berpindah. Hasilnya bukti, bukan jaminan telefoto dapat dipakai.

Qualification: JVM tests (zoom preset, analisis perpindahan lensa), lint, build, uji emulator Android 15/16 termasuk cubit lewat dispatch sentuhan nyata (dilewati bila kamera emulator tanpa zoom), Balik kamera, sakelar Pro, dan shader Cinematic. Hasil emulator tidak membuktikan perilaku Xiaomi 13T: uji cubit, pindah lensa, dan telefoto di HP.

APK label/package: Cherry Video Uji / id.tege.video.hotfix, versionCode 8. Kunci uji CI sama dengan 0.3.0/0.3.1, sehingga seharusnya terpasang menimpa. Personal debug beta, SDR 8-bit.
