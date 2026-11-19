package id.tege.video

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraActivity
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * USB-host UVC mode inside Cherry Video.
 *
 * This path deliberately uses AUSBC instead of Camera2 so a tablet does not need
 * Android's external-camera HAL. The existing Camera2/GL engine remains unchanged.
 * Advanced Camera2 controls, LUT, Bokeh and teleprompter are not yet available here.
 */
class UsbCameraActivity : CameraActivity() {
    private lateinit var cameraView: AspectRatioTextureView
    private lateinit var cameraContainer: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var recordButton: Button
    private lateinit var photoButton: Button
    private lateinit var resolutionButton: Button
    private var isRecording = false
    private var starting = false
    private var saving = false
    private var lastSavedUri: Uri? = null
    private val background = Color.rgb(16,19,21)
    private val gold = Color.rgb(230,190,127)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 14f): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(9),dp(12),dp(9))
        }

    private fun button(value: String, action: () -> Unit): Button =
        Button(this).apply {
            text = value
            textSize = 13f
            isAllCaps = false
            setOnClickListener { action() }
        }

    override fun getRootView(layoutInflater: LayoutInflater): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("Kembali") { if (isRecording || starting || saving) {
            note("Hentikan dan simpan rekaman sebelum keluar.")
        } else finish() }, LinearLayout.LayoutParams(dp(104),dp(54)))
        header.addView(label("Cherry Video  ·  Kamera USB",17f).apply {
            setTypeface(null,Typeface.BOLD);setTextColor(gold)
        },LinearLayout.LayoutParams(0,dp(54),1f))
        root.addView(header)

        statusView = label("Sambungkan webcam UVC melalui USB-C OTG, lalu izinkan akses USB.")
        root.addView(statusView)

        cameraContainer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        cameraView = AspectRatioTextureView(this)
        root.addView(cameraContainer,LinearLayout.LayoutParams(-1,0,1f))

        val settings = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        resolutionButton = button("Resolusi") { chooseResolution() }
        settings.addView(resolutionButton,LinearLayout.LayoutParams(0,dp(52),1f))
        settings.addView(button("Putar hasil") { openLastSaved() },LinearLayout.LayoutParams(0,dp(52),1f))
        root.addView(settings)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        photoButton = button("Foto JPG") { takePhoto() }
        recordButton = button("● Rekam MP4") { toggleRecord() }
        actions.addView(photoButton,LinearLayout.LayoutParams(0,dp(68),1f))
        actions.addView(recordButton,LinearLayout.LayoutParams(0,dp(68),1f))
        root.addView(actions)
        root.addView(label("Audio: otomatis (mikrofon webcam jika tersedia, atau mikrofon tablet). " +
            "USB Camera merekam secara mandiri; efek dan kontrol kamera internal belum berlaku di mode ini.",11f))
        refreshControls()
        return root
    }

    override fun getCameraView(): IAspectRatio = cameraView
    override fun getCameraViewContainer(): ViewGroup = cameraContainer
    override fun getGravity(): Int = Gravity.CENTER

    override fun getCameraRequest(): CameraRequest = CameraRequest.Builder()
        .setPreviewWidth(1280)
        .setPreviewHeight(720)
        .setRenderMode(CameraRequest.RenderMode.OPENGL)
        .setDefaultRotateType(RotateType.ANGLE_0)
        .setAudioSource(CameraRequest.AudioSource.SOURCE_AUTO)
        .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
        .setAspectRatioShow(true)
        .create()

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        runOnUiThread {
            when (code) {
                ICameraStateCallBack.State.OPENED -> {
                    val size = getCurrentPreviewSize()
                    note("Webcam USB terhubung · ${size?.width ?: "?"} × ${size?.height ?: "?"}. Siap merekam.")
                }
                ICameraStateCallBack.State.CLOSED -> {
                    if (isRecording || starting) note("Koneksi webcam putus. Periksa hasil rekaman sementara.")
                    else note("Webcam USB terputus. Sambungkan kembali melalui OTG.")
                    isRecording = false
                    starting = false
                }
                ICameraStateCallBack.State.ERROR -> {
                    note("Gagal membuka webcam USB: ${msg ?: "periksa izin, daya OTG, dan format UVC."}")
                    isRecording = false
                    starting = false
                }
            }
            refreshControls()
        }
    }

    private fun note(message: String) {
        if (::statusView.isInitialized) statusView.text = message
    }

    private fun refreshControls() {
        if (!::recordButton.isInitialized) return
        recordButton.text = when {
            saving -> "Menyimpan…"
            isRecording -> "■ Stop"
            starting -> "Menyiapkan…"
            else -> "● Rekam MP4"
        }
        recordButton.isEnabled = !saving
        photoButton.isEnabled = !isRecording && !starting && !saving
        resolutionButton.isEnabled = !isRecording && !starting && !saving
    }

    private fun chooseResolution() {
        if (!isCameraOpened() || isRecording || starting || saving) {
            note("Hubungkan webcam dan hentikan rekaman sebelum mengubah resolusi.")
            return
        }
        val sizes = getAllPreviewSizes().orEmpty()
            .filter { it.width > 0 && it.height > 0 }
            .distinctBy { it.width to it.height }
            .sortedWith(compareByDescending<com.jiangdg.ausbc.camera.bean.PreviewSize> { it.width * it.height })
        if (sizes.isEmpty()) { note("Webcam tidak melaporkan daftar resolusi.");return }
        val labels = sizes.map { "${it.width} × ${it.height}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Resolusi webcam USB")
            .setItems(labels) { _, which ->
                val size = sizes[which]
                runCatching { updateResolution(size.width,size.height) }
                    .onSuccess { note("Mengubah resolusi ke ${labels[which]}…") }
                    .onFailure { note("Gagal mengubah resolusi: ${it.localizedMessage}") }
            }
            .setNegativeButton("Batal",null).show()
    }

    private fun temporary(name: String, video: Boolean): File {
        val folder = getExternalFilesDir(if(video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
            ?: cacheDir
        folder.mkdirs()
        return File(folder,name)
    }

    private fun fileName(video: Boolean): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
        return "Cherry_USB_${date}.${if(video) "mp4" else "jpg"}"
    }

    private fun takePhoto() {
        if (!isCameraOpened() || isRecording || starting || saving) {
            note("Hubungkan webcam USB sebelum mengambil foto.")
            return
        }
        val tmp = temporary(fileName(false),false)
        saving = true;refreshControls()
        captureImage(object : ICaptureCallBack {
            override fun onBegin() { runOnUiThread { note("Mengambil foto dari webcam…") } }
            override fun onError(error: String?) { runOnUiThread {
                saving = false;refreshControls();note("Foto gagal: ${error ?: "kesalahan kamera USB"}")
            } }
            override fun onComplete(path: String?) { publishMedia(File(path?.takeIf { it.isNotBlank() } ?: tmp.path),false) }
        },tmp.absolutePath)
    }

    private fun toggleRecord() {
        if (isRecording) {
            note("Menghentikan dan menyimpan rekaman USB…")
            captureVideoStop()
            return
        }
        if (starting || saving) return
        if (!isCameraOpened()) { note("Hubungkan webcam USB dan izinkan akses sebelum merekam.");return }
        val tmp = temporary(fileName(true),true)
        starting = true;refreshControls();note("Menyiapkan perekam USB…")
        captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() { runOnUiThread {
                starting = false;isRecording = true;refreshControls()
                note("REC · Webcam USB · audio otomatis")
            } }
            override fun onError(error: String?) { runOnUiThread {
                starting = false;isRecording = false;refreshControls()
                note("Perekaman gagal: ${error ?: "kesalahan kamera USB"}")
            } }
            override fun onComplete(path: String?) {
                runOnUiThread { isRecording = false;starting = false;saving = true;refreshControls();note("Menyimpan video ke galeri…") }
                publishMedia(File(path?.takeIf { it.isNotBlank() } ?: tmp.path),true)
            }
        },tmp.absolutePath,0L)
    }

    /**
     * UVC writes into an app-owned temporary file; we then publish an ordinary
     * media item so the result is visible in Gallery, Files and other apps.
     * The original is retained if the MediaStore copy fails.
     */
    private fun publishMedia(source: File, video: Boolean) {
        Thread {
            var uri: Uri? = null
            var error: String? = null
            if (!source.isFile || source.length() == 0L) {
                error = "Berkas hasil webcam kosong atau tidak ditemukan."
            } else {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME,source.name)
                        put(MediaStore.MediaColumns.MIME_TYPE,if(video) "video/mp4" else "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH,
                            (if(video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/CherryVideo")
                        put(MediaStore.MediaColumns.IS_PENDING,1)
                    }
                    val collection = if(video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    uri = contentResolver.insert(collection,values)
                        ?: throw IllegalStateException("Galeri tidak mengizinkan penulisan.")
                    contentResolver.openOutputStream(uri!!)?.use { target ->
                        source.inputStream().use { input -> input.copyTo(target) }
                    } ?: throw IllegalStateException("Berkas keluaran tidak dapat dibuka.")
                    contentResolver.update(uri!!, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING,0)
                    },null,null)
                    source.delete()
                } catch (e: Exception) {
                    error = e.localizedMessage ?: "Gagal memindahkan ke galeri."
                    uri?.let { runCatching { contentResolver.delete(it,null,null) } }
                    uri = null
                }
            }
            val saved = uri
            val issue = error
            runOnUiThread {
                saving = false
                refreshControls()
                if(saved != null) {
                    lastSavedUri = saved
                    note("${if(video) "Video MP4" else "Foto JPG"} tersimpan di galeri /CherryVideo/${source.name}")
                } else {
                    note("Penyimpanan gagal: " + (issue ?: "tidak diketahui") + ". Berkas sementara tidak dihapus.")
                }
            }
        }.start()
    }

    private fun openLastSaved() {
        val uri = lastSavedUri
        if (uri == null) { note("Belum ada hasil kamera USB yang tersimpan pada sesi ini.");return }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri,contentResolver.getType(uri) ?: "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { note("Tidak ada aplikasi pembuka media: ${it.localizedMessage}") }
    }

    override fun onPause() {
        if (isRecording) captureVideoStop()
        super.onPause()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (isRecording || starting || saving) note("Stop rekaman dan selesaikan penyimpanan sebelum keluar.")
        else super.onBackPressed()
    }
}
