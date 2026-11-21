# Cherry Video 0.5.4-beta — USB sebagai sumber kamera (satu mesin perekaman)

## Hasil uji perangkat — Xiaomi 13T, 21 November 2026

**Status: uji fungsional awal BERHASIL; uji ketahanan belum dilakukan.** Laporan pengguna untuk APK **Cherry Video Uji 0.5.4-beta** (webcam eksternal UVC NEMESIS Severus A96 melalui adaptor USB OTG):

| Pemeriksaan | Hasil lapangan |
|---|---|
| Koneksi webcam USB ke Cherry Video | **Lulus** |
| Gambar webcam tampil di preview Cherry Video | **Lulus** |
| Rekam video melalui tombol/mesin Cherry Video | **Lulus** |
| Audio terekam bersama video | **Lulus** |
| Kelancaran preview/rekaman saat uji | **Lulus secara visual** — pengguna tidak melihat lag; belum ada pengukuran FPS/frame drop |
| Seluruh pilihan resolusi yang ditawarkan dalam Cherry Video | **Dapat digunakan menurut pengguna** |
| LUT dan fitur kreatif Cherry Video yang dicoba | **Berfungsi menurut pengguna**; webcam diperlakukan sebagai sumber gambar pada UI dan mesin Cherry Video yang sama |
| Rekaman panjang, panas, stabilitas, sinkronisasi A/V terukur | **Belum diuji** |
| Pengujian di Redmi Pad 2 | **Belum dilaporkan** |

**Temuan kualitas yang harus ditindaklanjuti (FOV/cropping):** pengguna mengamati bahwa **semakin kecil resolusi yang dipilih, semakin sempit sudut pandang** baik pada preview **maupun** hasil video. Seluruh resolusi bisa direkam, tetapi hasilnya tampak seperti *digital crop*, bukan sekadar perbedaan kerapatan/detail. Ini **gejala yang teramati, bukan penjelasan akar masalah**. Kemungkinan berkaitan dengan mode/rasio frame UVC, kebijakan crop webcam, atau pemetaan texture/viewport pada `VideoRenderer`; perlu perbandingan frame pada posisi kamera yang sama, resolusi berbeda, tanpa mengubah jarak/zoom. Jangan menganggap penurunan resolusi selalu menghasilkan sudut pandang lebih sempit sebagai perilaku yang diinginkan.

**Riwayat kegagalan dan pemulihan:** versi 0.5.3-beta sebelumnya tidak menampilkan gambar dan pernah force close saat dicoba ulang pada Xiaomi 13T. Versi 0.5.4-beta **sudah berhasil dalam uji terbaru**. Pengguna sempat menduga aplikasi USB Camera masih aktif di latar saat kegagalan awal, tetapi belum ada logcat atau uji kontrol untuk menentukan apakah penyebabnya konflik akses, bug UVC yang diperbaiki, atau kombinasi keduanya.

**Batas klaim:** keberhasilan satu sesi tidak membuktikan perekaman 5–10 menit, FPS aktual di semua pilihan resolusi, kualitas optik, sinkronisasi A/V jangka panjang, termal, atau dukungan sensor manual/UVC di semua HP. Nama fungsi kreatif yang tidak diuji secara spesifik jangan dinyatakan sudah lulus satu per satu.

---

## Hotfix 0.5.4-beta — laporan kegagalan awal (20 November 2026)
Pengguna melaporkan bahwa webcam bekerja dengan aplikasi USB Camera, tetapi tidak menampilkan preview pada Cherry Video 0.5.3-beta; percobaan berikutnya membuat Cherry Video tertutup paksa. Pesan layar persis belum diketahui, dan logcat belum tersedia. Karena itu akar penyebab crash **belum terverifikasi**.

Perubahan:
- **Stop ganda dihapus**: pemanggilan `UVCCamera.stopPreview()` sebelum `UVCCamera.destroy()` dihilangkan, karena `destroy()` memanggil `close()` dan `stopPreview()` sendiri. Berisiko native crash ketika dibuka ulang.
- **Negosiasi format lebih dahulu**: resolusi MJPEG/YUYV ditanyakan ke webcam dan dicek kompatibel dengan encoder. Dibuat `VideoRenderer` dengan ukuran yang benar setelah mode UVC dipilih, bukan mengunci 1280×720 sebelum USB dibuka.
- **Status dan diagnostik**: file `usb-last-diagnostic.txt` merekam langkah USB-01 hingga USB-10 (juga error USB-OPEN), serta ditampilkan dalam menu Pengaturan → Diagnostik perangkat & FPS dan Ekspor JSON.
- **Timeout aman**: jika UVC berhasil dibuka tetapi tidak ada frame pertama dalam 8 detik, Cherry Video menampilkan pesan USB-PREVIEW dan melepas sumber secara terkendali, bukan terus menunggu.
- **UI**: profil resolusi sesungguhnya dan status siap disinkronkan; pesan error lama dibersihkan setelah berhasil membuka kamera.
- **Jalur yang dipertahankan**: kamera USB tetap hanya menjadi sumber frame untuk mesin perekam Cherry Video; tidak ada recorder AUSBC kedua.

Build `:core:test :app:assembleDebug` **berhasil**, GitHub Actions run `44685366821`. SHA256 APK debug uji: `89854c046d778521a46d99d3e44c1c25361801dda66eb02640d0000a756ea083`. APK sudah disalin ke chat. Artifact APK dan workflow sementara GitHub Actions sudah dihapus.

**Catatan historis (sebelum uji 21 November):** keberhasilan kompilasi saja memang bukan bukti pemulihan. Setelah itu, pengguna melaporkan uji fungsional 0.5.4 berhasil di Xiaomi 13T; penyebab force close versi sebelumnya tetap belum teridentifikasi melalui logcat.

**Prosedur pemulihan jika kegagalan terulang:** tutup aplikasi USB Camera dan lepas-pasang adaptor, buka Cherry Video Uji 0.5.4, pilih Sumber USB; jika gagal tangkap screenshot status layar, beralih ke Internal, lalu buka Lainnya → Pengaturan → Diagnostik perangkat & FPS dan ekspor JSON (mengandung `lastUsbDiagnostic`).

---

## Arsitektur
**Tidak ada aplikasi atau layar perekam kedua.** Pilihan USB/Internal ada di `MainActivity`. Frame video dari webcam UVC diterima oleh `UvcSource` melalui `MultiCameraClient` (izin/hotplug) dan `UVCCamera` (native USB/UVC), lalu masuk langsung ke **`VideoRenderer.input` milik Cherry Video**.

Alur: Kamera internal Camera2 **atau** USB UVC → VideoRenderer (GPU/overlay/LUT) → SurfaceRecorder (MP4 + mikrofon/audio Cherry Video) → VideoFiles (validasi dan galeri).

Implementasi USB **tidak** memakai `AUSBC.captureVideoStart()`, `AUSBC.captureImage()`, maupun perekam bawaan library. Kelas `UsbCameraActivity` lama dihapus.

## Cara memakai (Xiaomi 13T sudah diuji, Redmi Pad 2 belum)
1. Pasang webcam ke adaptor USB-C OTG, lalu hubungkan ke Xiaomi 13T atau tablet.
2. Buka **Cherry Video → Lainnya → Sumber kamera: USB / Internal**. Saat Android meminta izin USB, pilih izinkan.
3. Tunggu tulisan **Siap · Webcam USB** serta preview. Tombol **Kualitas** menawarkan ukuran yang dilaporkan webcam dan didukung encoder; ingat bahwa uji Xiaomi 13T menemukan sudut pandang lebih sempit pada resolusi yang lebih kecil.
4. Gunakan tombol rekam Cherry Video yang sama. Audio memakai pengaturan **Audio** Cherry Video. Foto, tanda waktu, jeda/lanjut, galeri, LUT dan overlay tetap melalui jalur Cherry Video.
5. Tekan tombol **Kamera USB** di baris atas untuk kembali ke kamera internal.

## Penerapan fitur
- Jalur bersama: preview, warna/LUT, efek sinematik berbasis GPU, teks judul/lower third, cap lokasi (apabila diaktifkan), snapshot, tombol rekam, jeda/lanjut, audio dan penyimpanan MP4.
- Perangkat USB menyediakan stream mentah ke GPU; fitur sensor Camera2 seperti ISO/shutter manual, OIS/EIS, ketuk fokus/AE-lock, zoom sensor, dan deteksi wajah **tidak otomatis tersedia** pada USB. UI pengaturan internal yang masih tampak tidak berarti webcam menerima kontrol itu.
- Bokeh person-mask berbasis stream sekunder Camera2 belum diterapkan untuk USB; tidak boleh dianggap aktif pada hasil webcam.
- Timestamp UVC dilatch menjadi waktu monotonic saat frame diterima agar cocok dengan `SurfaceRecorder`/audio Cherry Video.
- Resolusi dipilih dari mode video UVC yang dilaporkan, dengan filter kemampuan encoder Android. Saat ini target rekaman **30 fps**; nilai FPS aktual hanya dapat dinilai dari file hasil dan report.
- Pilihan HEVC/AVC masih memakai kebijakan Cherry Video. Penggunaan mic USB perlu dipilih/diperiksa berdasarkan rute input Android; keberadaan mic pada webcam tidak menjamin audio otomatis dipakai oleh perekam.

## Batasan
Kamera USB dalam Cherry Video **bukan kamera virtual** Android: Google Meet, Zoom, dan aplikasi live lain belum otomatis bisa memilih webcam tersebut. Satu webcam eksternal berhasil diuji secara fungsional pada Xiaomi 13T (21 November 2026), tetapi uji kestabilan jangka panjang, perbandingan sudut pandang antarresolusi, dan perangkat Redmi Pad 2 belum selesai. Status tetap **beta/uji**, belum rilis stabil.

## Pengujian perangkat
- [ ] Aplikasi versi uji dapat dibuka saat tidak ada webcam.
- [x] Pada Xiaomi 13T: USB tersambung dan preview Cherry Video muncul tanpa crash pada sesi berhasil (detail dialog izin tidak dilaporkan).
- [ ] Uji layar potret/lanskap dan putar smartphone/tablet.
- [x] Pada Xiaomi 13T: semua resolusi yang tersedia di Cherry Video dapat digunakan menurut pengguna. **Masalah terbuka:** resolusi lebih kecil menghasilkan FOV/sudut pandang lebih sempit baik di preview maupun video.
- [ ] Bandingkan resolusi dengan objek, jarak dan posisi kamera tetap; catat resolusi, rasio aspek dan apakah crop dari webcam atau renderer.
- [x] LUT dan fitur lain yang dicoba pengguna bekerja baik melalui sumber USB (pengguna tidak merinci hasil tiap kontrol).
- [ ] Verifikasi secara terpisah foto, teleprompter/teks, Cinematic, dan setiap fitur khusus yang memerlukan bukti individual.
- [x] Rekam video dan audio berhasil pada Xiaomi 13T; pengguna melaporkan tanpa lag terlihat.
- [ ] Catat durasi sesi sebenarnya, pemeriksaan MP4 terukur, FPS aktual/frame gap dan sinkronisasi A/V.
- [ ] Uji jeda/lanjut, rekaman 5–10 menit, suhu, kestabilan dan ruang penyimpanan.
- [ ] Cabut OTG setelah stop; kembali ke Internal dan rekam dengan kamera tablet.
- [ ] Jika USB gagal, catat layar kesalahan dan ekspor Diagnostik.

## Bukti build dan riwayat versi

- **0.5.4-beta (terbaru):** `:core:test :app:assembleDebug` berhasil pada GitHub Actions **20 November 2026**, run `44685366821`. SHA-256 APK uji: `89854c046d778521a46d99d3e44c1c25361801dda66eb02640d0000a756ea083`. **Uji fungsional Xiaomi 13T berhasil pada 21 November 2026** menurut laporan pengguna; uji ketahanan dan masalah FOV masih terbuka.
- **0.5.3-beta (riwayat):** build berhasil **19 November 2026**, run `44562986286`. SHA-256 APK uji: `057e2ffd2e5644a835d9b2f3dcd1764f7ae389bb5825ad0a241846b1948effd4`. Uji webcam awal gagal pada Xiaomi 13T.

APK uji diserahkan melalui percakapan. **Tidak ada APK/ZIP hasil build yang disimpan dalam commit repo**; artifact sementara dari kedua workflow telah dihapus dari GitHub Actions dan workflow sementara dibersihkan. Branch: `feature/usb-uvc-camera`, PR #1 masih draft dan belum merged ke `main`.
