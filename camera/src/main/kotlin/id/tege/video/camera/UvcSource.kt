package id.tege.video.camera

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.view.Surface
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.UVCCamera

/**
 * USB connection ONLY. Frames go straight from libuvc into the input Surface
 * owned by Cherry Video's VideoRenderer, never through AUSBC's recorder.
 * This adapter lives on CameraEngine's serial handler.
 */
internal class UvcSource(
    context: Context,
    private val handler: Handler,
    private val input: Surface,
    private val preferredWidth: Int,
    private val preferredHeight: Int,
    private val onModes: (List<Pair<Int,Int>>) -> Unit,
    private val onConnected: (String) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val context = context.applicationContext
    private var monitor: MultiCameraClient? = null
    private var camera: UVCCamera? = null
    private var targetId: Int? = null
    private var closed = false
    private var wasConnected = false
    private var isConnecting = false

    private fun UsbDevice.isVideoCamera(): Boolean =
        (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }

    fun start() {
        if (closed || monitor != null) return
        try {
            monitor = MultiCameraClient(context, object : IDeviceConnectCallBack {
                override fun onAttachDev(device: UsbDevice?) {
                    if (device != null && device.isVideoCamera() && targetId == null) {
                        targetId = device.deviceId
                        monitor?.requestPermission(device)
                    }
                }
                override fun onDetachDec(device: UsbDevice?) {
                    if (device?.deviceId == targetId) handler.post {
                        if (!closed) disconnected()
                    }
                }
                override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                    if (device == null || ctrlBlock == null || device.deviceId != targetId) return
                    handler.post { if (!closed) open(device, ctrlBlock) }
                }
                override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                    if (device?.deviceId == targetId) handler.post {
                        if (!closed) disconnected()
                    }
                }
                override fun onCancelDev(device: UsbDevice?) {
                    if (device?.deviceId == targetId) handler.post {
                        if (!closed) onFailure("Akses kamera USB tidak diberikan. Cabut-pasang OTG lalu izinkan akses.")
                    }
                }
            }).also { it.register() }
            val devices = monitor?.getDeviceList().orEmpty().filter { it.isVideoCamera() }
            val first = devices.firstOrNull()
            if (first == null) onFailure("Webcam UVC tidak ditemukan. Pasang USB OTG dan pastikan perangkat mendapat daya.")
            else {
                targetId = first.deviceId
                if (monitor?.requestPermission(first) != true)
                    onFailure("Tidak dapat meminta izin perangkat USB.")
            }
        } catch (e: Exception) { onFailure("Inisialisasi kamera USB gagal: ${e.message}") }
    }

    private fun open(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        if (closed || isConnecting || camera != null) return
        isConnecting = true
        try {
            val instance = UVCCamera()
            camera = instance
            instance.open(ctrlBlock)
            val mjpeg = instance.getSupportedSizeList(UVCCamera.FRAME_FORMAT_MJPEG)
                .map { it.width to it.height }.distinct()
            val yuyv = instance.getSupportedSizeList(UVCCamera.FRAME_FORMAT_YUYV)
                .map { it.width to it.height }.distinct()
            val available = (mjpeg + yuyv).distinct().sortedByDescending { it.first.toLong() * it.second }
            onModes(available)
            val preferred = preferredWidth to preferredHeight
            check(preferred in available) {
                "Resolusi ${preferredWidth}×${preferredHeight} tidak tersedia pada webcam. Pilih kualitas lain di Cherry Video."
            }
            val formats = if(preferred in mjpeg) listOf(UVCCamera.FRAME_FORMAT_MJPEG, UVCCamera.FRAME_FORMAT_YUYV)
                else listOf(UVCCamera.FRAME_FORMAT_YUYV, UVCCamera.FRAME_FORMAT_MJPEG)
            var chosen = false
            for(format in formats) {
                if ((format == UVCCamera.FRAME_FORMAT_MJPEG && preferred !in mjpeg) ||
                    (format == UVCCamera.FRAME_FORMAT_YUYV && preferred !in yuyv)) continue
                if(runCatching {
                    instance.setPreviewSize(preferredWidth, preferredHeight, 1, 31, format, UVCCamera.DEFAULT_BANDWIDTH)
                }.isSuccess) { chosen = true;break }
            }
            check(chosen) { "Format MJPEG/YUYV ${preferredWidth}×${preferredHeight} ditolak webcam." }
            instance.setPreviewDisplay(input)
            instance.startPreview()
            wasConnected = true
            onConnected("USB ${device.vendorId.toString(16)}:${device.productId.toString(16)}")
        } catch (e: Throwable) {
            runCatching { camera?.destroy() };camera = null
            onFailure("Webcam USB: ${e.message ?: e.javaClass.simpleName}")
        } finally { isConnecting = false }
    }

    private fun disconnected() {
        val hadCamera = wasConnected
        wasConnected = false
        closeCamera()
        if (hadCamera) onDisconnected()
        else onFailure("Koneksi USB terputus sebelum webcam siap.")
    }

    private fun closeCamera() {
        val old = camera
        camera = null
        runCatching { old?.stopPreview() }
        runCatching { old?.destroy() }
    }

    fun close() {
        if (closed) return
        closed = true
        closeCamera()
        runCatching { monitor?.unRegister() }
        runCatching { monitor?.destroy() }
        monitor = null
    }
}
