# Cherry Video 0.5.3-beta — USB sebagai sumber kamera (satu mesin perekaman)

## Arsitektur
**Tidak ada aplikasi atau layar perekam kedua.** Pilihan USB/Internal ada di `MainActivity`. Frame video dari webcam UVC diterima oleh `UvcSource` melalui `MultiCameraClient` (izin/hotplug) dan `UVCCamera` (native USB/UVC), lalu masuk langsung ke **`VideoRenderer.input` milik Cherry Video**.

Alur: Kamera internal Camera2 **atau** USB UVC → VideoRenderer (GPU/overlay/LUT) → SurfaceRecorder (MP4 + mikrofon/audio Cherry Video) → VideoFiles (validasi dan galeri).

Implementasi USB **tidak** memakai `AUSBC.captureVideoStart()`, `AUSBC.captureImage()`, maupun perekam bawaan library. Kelas `UsbCameraActivity` lama dihapus.

## Cara memakai pada Redmi Pad 2
1. Pasang webcam ke adaptor USB-C OTG, lalu hubungkan ke tablet.
2. Buka **Cherry Video → Lainnya → Sumber kamera: USB / Internal**. Saat Android meminta izin USB, pilih izinkan.
3. Tunggu tulisan **Siap · Webcam USB** serta preview. Jika kamera tidak menawarkan resolusi awal 1280×720, buka tombol **Kualitas** lalu pilih ukuran yang benar-benar ditawarkan webcam.
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
Kamera USB dalam Cherry Video **bukan kamera virtual** Android: Google Meet, Zoom, dan aplikasi live lain belum otomatis bisa memilih webcam tersebut. Kualitas dan kompatibilitas webcam tertentu belum diuji karena perangkat berada di kantor. Seluruh perubahan adalah **versi uji** sampai tes lapangan.

## Pengujian perangkat
- [ ] Aplikasi versi uji dapat dibuka saat tidak ada webcam.
- [ ] Pasang USB OTG, buka sumber USB, setujui izin, lihat preview tanpa crash.
- [ ] Uji layar potret/lanskap dan putar smartphone/tablet.
- [ ] Cek tombol Kualitas (daftar resolusi UVC nyata); uji 720p kemudian 1080p jika tersedia.
- [ ] Coba LUT, Cinematic, teleprompter/teks, dan foto.
- [ ] Rekam 30 detik dengan audio; periksa MP4 di Galeri dan laporan FPS/sinkronisasi.
- [ ] Uji jeda/lanjut, rekaman 5–10 menit, suhu, kestabilan dan ruang penyimpanan.
- [ ] Cabut OTG setelah stop; kembali ke Internal dan rekam dengan kamera tablet.
- [ ] Jika USB gagal, catat layar kesalahan dan ekspor Diagnostik.

## Hasil build
Branch `feature/usb-uvc-camera`. Gradle `:core:test :app:assembleDebug` **berhasil** pada GitHub Actions 19 Nov 2026 (run 44562986286). APK dipindahkan ke percakapan; **artefak build GitHub Actions telah dihapus** dan workflow sementara dibersihkan. Build bukan jaminan kompatibilitas UVC atau stabilitas hardware.

SHA-256 APK: `057e2ffd2e5644a835d9b2f3dcd1764f7ae389bb5825ad0a241846b1948effd4`.
