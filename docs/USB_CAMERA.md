# Cherry Video — Kamera USB (UVC) [eksperimen 0.5.2-beta]

## Tujuan
Menambahkan mode webcam USB OTG di APK Cherry Video **yang sama**, terutama untuk Redmi Pad 2. Tidak membuat aplikasi kedua. Mode kamera ponsel yang sudah ada tetap menggunakan Camera2/Cherry Video CameraEngine. Mode USB menggunakan **AUSBC 3.6.0** karena beberapa tablet tidak mengekspos webcam UVC melalui Camera2 API.

## Penggunaan
1. Sambungkan webcam UVC ke tablet menggunakan adaptor USB-C OTG (jika daya USB kurang, coba hub USB bertenaga).
2. Di Cherry Video, buka **Lainnya → Kamera USB · Webcam OTG**.
3. Setujui permintaan akses USB dari Android. Tunggu pesan webcam terhubung.
4. Pilih **Resolusi** dari daftar yang dilaporkan webcam, misalnya 1280×720 atau 1920×1080 jika benar-benar tersedia.
5. Tekan **Foto JPG** untuk foto atau **Rekam MP4** untuk mulai; tekan **Stop** untuk menyimpan.
6. Hasil dipublikasikan ke **Pictures/CherryVideo** atau **Movies/CherryVideo** dan dapat dibuka lewat Galeri. Berkas sementara tidak dihapus jika publikasi gagal.

## Batasan teknis
- Menggunakan jalur UVC langsung, bukan kamera virtual Android. Mode ini **tidak** membuat webcam dapat dipilih di Google Meet, Zoom, atau aplikasi sosial lain.
- Ini adalah mode terpisah **di dalam APK yang sama**. Pengolahan Cherry Video Camera2/GL (LUT, Bokeh, Cinematic, EIS/OIS, teleprompter, cap lokasi, pause/mark) belum dihubungkan ke jalur UVC ini. Jangan klaim efek tersebut ikut masuk rekaman USB.
- Audio menggunakan sumber **AUTO** dari AUSBC: mikrofon webcam bila tersedia, jika tidak mikrofon tablet; cek apakah mikrofon dan suara benar-benar terekam.
- Resolusi awal 1280×720 dengan permintaan MJPEG. Pilihan lain bergantung pada daftar webcam. **Tidak mengklaim 2K/60fps sebelum diuji.**
- Perangkat USB yang terdeteksi di laptop belum tentu berfungsi pada tablet. Perlu uji OTG, daya, izin USB, codec, audio, rotasi video, dan perekaman panjang.
- Memerlukan dependency dari JitPack; ukuran APK meningkat akibat pustaka native. Periksa hasil Gradle dan ukuran APK sebelum menggabungkan perubahan.

## Uji pada Redmi Pad 2
- [ ] Aplikasi bisa dibuka tanpa webcam dan tidak crash.
- [ ] Webcam langsung terdeteksi setelah memasang OTG dan memberi izin.
- [ ] Preview muncul tanpa warna rusak pada posisi tablet potret dan lanskap.
- [ ] Daftar resolusi muncul dan 720p/1080p dapat dipilih jika tersedia.
- [ ] Foto JPG tersimpan dan dapat dibuka Galeri.
- [ ] Video MP4 berdurasi 30 detik tersimpan, audio sinkron dan frame rate stabil.
- [ ] Rekaman 10 menit selesai tanpa file kosong, panas berlebihan atau kegagalan USB.
- [ ] Melepas USB menampilkan peringatan dan masuk lagi tidak crash.
- [ ] Mode kamera internal Cherry Video tetap normal setelah kembali.

### Status
Implementasi awal ditulis pada branch `feature/usb-uvc-camera`; **belum terkompilasi/teruji pada perangkat** di sesi ini. Tidak menjalankan GitHub Actions berbayar/berat secara otomatis. Gabungkan hanya setelah pemeriksaan build dan uji Redmi Pad 2.
