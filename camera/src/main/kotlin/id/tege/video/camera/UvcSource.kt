package id.tege.video.camera

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.util.Log
import android.view.Surface
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.UVCCamera
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Connection/permission/format negotiation ONLY. Native USB frames are written
 * straight to the input Surface of Cherry VideoRenderer, not AUSBC's recorder.
 * All open/close operations run on the same CameraEngine handler.
 */
internal class UvcSource(
    context: Context,
    private val handler: Handler,
    private val preferredWidth: Int,
    private val preferredHeight: Int,
    private val acceptsMode: (Int, Int) -> Boolean,
    private val inputForMode: (Int, Int) -> Surface,
    private val onModes: (List<Pair<Int, Int>>) -> Unit,
    private val onConnected: (String) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val context = context.applicationContext
    private var monitor: MultiCameraClient? = null
    private var camera: UVCCamera? = null
    private var targetId: Int? = null
    private var closed = false
    private var active = false
    private var connecting = false
    private var failed = false

    private fun UsbDevice.isVideoCamera() =
        (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }

    private fun stage(id: String, detail: String) {
        val message = "USB-$id: $detail"
        Log.i("CherryUVC", message)
        runCatching {
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            File(context.filesDir, "usb-last-diagnostic.txt").appendText("$stamp $message\n")
        }
    }

    private fun problem(id: String, detail: String) {
        if (closed || failed) return
        failed = true
        stage(id, detail)
        onFailure("USB-$id: $detail")
    }

    fun start() {
        if (closed || monitor != null) return
        runCatching { File(context.filesDir, "usb-last-diagnostic.txt").writeText("Cherry Video UVC diagnostic\n") }
        stage("01", "Memulai pemeriksaan perangkat USB.")
        try {
            val client = MultiCameraClient(context, object : IDeviceConnectCallBack {
                override fun onAttachDev(device: UsbDevice?) {
                    if (device == null) return
                    handler.post {
                        if (!closed && targetId == null && device.isVideoCamera()) {
                            targetId = device.deviceId
                            stage("02", "Webcam terpasang: VID=${device.vendorId}, PID=${device.productId}.")
                            monitor?.requestPermission(device)
                        }
                    }
                }
                override fun onDetachDec(device: UsbDevice?) {
                    if (device != null) handler.post {
                        if (!closed && device.deviceId == targetId) disconnected()
                    }
                }
                override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                    if (device == null || ctrlBlock == null) return
                    handler.post {
                        if (!closed && device.deviceId == targetId) open(device, ctrlBlock)
                    }
                }
                override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                    if (device != null) handler.post {
                        if (!closed && device.deviceId == targetId) disconnected()
                    }
                }
                override fun onCancelDev(device: UsbDevice?) {
                    handler.post {
                        if (!closed && (device == null || device.deviceId == targetId))
                            problem("03", "Izin USB ditolak atau sistem tidak mengirim izin perangkat.")
                    }
                }
            })
            monitor = client
            client.register()
            val first = client.getDeviceList().orEmpty().firstOrNull { it.isVideoCamera() }
            if (first == null) problem("02", "Webcam UVC tidak ditemukan. Periksa kabel dan daya OTG.")
            else {
                targetId = first.deviceId
                stage("02", "Meminta izin webcam VID=${first.vendorId}, PID=${first.productId}.")
                // Boolean result of AUSBC's wrapper is not a success indicator.
                client.requestPermission(first)
            }
        } catch (e: Exception) {
            problem("03", "Izin atau inisialisasi USB gagal: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun open(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        if (closed || connecting || camera != null) return
        connecting = true
        failed = false
        stage("04", "Izin diterima; membuka perangkat UVC.")
        var instance: UVCCamera? = null
        try {
            instance = UVCCamera()
            camera = instance
            instance.open(ctrlBlock)
            val mjpeg = instance.getSupportedSizeList(UVCCamera.FRAME_FORMAT_MJPEG)
                .map { it.width to it.height }.filter { it.first > 0 && it.second > 0 }.distinct()
            val yuyv = instance.getSupportedSizeList(UVCCamera.FRAME_FORMAT_YUYV)
                .map { it.width to it.height }.filter { it.first > 0 && it.second > 0 }.distinct()
            val available = (mjpeg + yuyv).distinct()
            onModes(available.sortedByDescending { it.first.toLong() * it.second })
            stage("05", "Mode webcam: ${available.joinToString { "${it.first}x${it.second}" }.take(300)}")
            val candidates = available.filter { acceptsMode(it.first, it.second) }
                .sortedWith(compareBy<Pair<Int, Int>> {
                    if (it.first == preferredWidth && it.second == preferredHeight) 0 else 1
                }.thenBy {
                    abs(it.first.toDouble()/it.second - preferredWidth.toDouble()/preferredHeight)
                }.thenBy {
                    abs(it.first.toLong()*it.second - preferredWidth.toLong()*preferredHeight)
                })
            check(candidates.isNotEmpty()) {
                "Tidak ada resolusi webcam yang cocok dengan encoder Cherry Video."
            }
            var chosen: Pair<Int, Int>? = null
            for ((w, h) in candidates) {
                val formats = listOf(UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.FRAME_FORMAT_YUYV)
                for (format in formats) {
                    if ((format == UVCCamera.FRAME_FORMAT_MJPEG && (w to h) !in mjpeg) ||
                        (format == UVCCamera.FRAME_FORMAT_YUYV && (w to h) !in yuyv)) continue
                    if (runCatching {
                        instance.setPreviewSize(w, h, 1, 31, format, UVCCamera.DEFAULT_BANDWIDTH)
                    }.isSuccess) {
                        chosen = w to h
                        stage("06", "Format UVC diset ke ${w}x${h}, format ${if(format==1) "MJPEG" else "YUYV"}.")
                        break
                    }
                }
                if (chosen != null) break
            }
            check(chosen != null) { "Webcam menolak semua format MJPEG/YUYV yang dicoba." }
            val (w, h) = chosen
            val surface = inputForMode(w, h)
            stage("07", "Surface Cherry Video siap; memulai preview.")
            instance.setPreviewDisplay(surface)
            instance.startPreview()
            active = true
            onConnected("VID ${device.vendorId} · PID ${device.productId} · ${w}x${h}")
            stage("08", "Perintah startPreview berhasil; menunggu frame pertama.")
        } catch (e: Throwable) {
            // UVCCamera.destroy() already invokes close() -> stopPreview(). Never call
            // stopPreview() and destroy() separately: native double-stop can crash.
            stage("ERR", "Gagal membuka UVC: ${e.javaClass.simpleName}: ${e.message}")
            camera = null
            runCatching { instance?.destroy() }
            problem("OPEN", e.message ?: e.javaClass.simpleName)
        } finally {
            connecting = false
        }
    }

    private fun disconnected() {
        if (closed) return
        val wasActive = active
        active = false
        val alreadyFailed = failed
        stage("09", "Perangkat USB terputus.")
        closeCamera()
        if (wasActive) onDisconnected()
        else if (!alreadyFailed) problem("DISC", "Perangkat USB terlepas sebelum preview siap.")
    }

    private fun closeCamera() {
        val instance = camera
        camera = null
        active = false
        // IMPORTANT: no separate stopPreview(). destroy() itself calls close().
        runCatching { instance?.destroy() }.onFailure {
            stage("ERR", "Penutupan native UVC: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    fun close() {
        if (closed) return
        closed = true
        stage("10", "Menutup sesi UVC.")
        closeCamera()
        runCatching { monitor?.unRegister() }
        runCatching { monitor?.destroy() }
        monitor = null
        targetId = null
    }
}
