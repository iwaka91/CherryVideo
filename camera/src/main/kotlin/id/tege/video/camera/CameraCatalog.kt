package id.tege.video.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Range
import android.util.Size
import id.tege.video.core.VideoPolicy
import id.tege.video.core.VideoProfile

data class CameraEntry(val id: String, val label: String, val characteristics: C) {
    val front: Boolean get() = characteristics[C.LENS_FACING] == C.LENS_FACING_FRONT
    val rotation: Int get() = characteristics[C.SENSOR_ORIENTATION] ?: 90
    val minFocus: Float get() = characteristics[C.LENS_INFO_MINIMUM_FOCUS_DISTANCE] ?: 0f
    val manual: Boolean get() = characteristics[C.REQUEST_AVAILABLE_CAPABILITIES]?.contains(C.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
    val ois: Boolean get() = characteristics[C.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION]?.contains(1) == true
    val eis: Boolean get() = characteristics[C.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES]?.contains(1) == true
    val ranges: List<Range<Int>> get() = characteristics[C.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]?.toList().orEmpty()
    fun range(fps: Int): Range<Int>? = ranges.filter { it.upper == fps }.minByOrNull { it.upper - it.lower }
    fun profiles(hevc: Boolean): List<VideoProfile> {
        val map=characteristics[C.SCALER_STREAM_CONFIGURATION_MAP] ?: return emptyList()
        val sizes=map.getOutputSizes(SurfaceTexture::class.java)?.toSet().orEmpty()
        return listOf(VideoProfile(1920,1080,30), VideoProfile(1920,1080,60), VideoProfile(3840,2160,30), VideoProfile(1280,720,30)).filter { p ->
            val size=Size(p.width,p.height)
            size in sizes && range(p.fps) != null && VideoPolicy.qualifies(p, range(p.fps)!!.upper,
                map.getOutputMinFrameDuration(SurfaceTexture::class.java,size), CodecSupport.supports(p,hevc))
        }
    }
    fun report(): String = buildString {
        append("Camera $id · $label\nSensor rotation: $rotation°\n")
        append("Manual sensor: $manual\nFocus range: 0..$minFocus diopter\nOIS: $ois · EIS: $eis\n")
        append("Normal AE ranges: ${ranges.joinToString()}\n")
        append("AVC: ${profiles(false).joinToString { it.label }}\nHEVC: ${profiles(true).joinToString { it.label }}\n")
        append("Advertised profiles are not yet physical-device verified. Constrained high-speed sessions are not implemented in this release.\n")
    }
}

object CameraCatalog {
    fun list(context: Context): List<CameraEntry> {
        val manager=context.getSystemService(CameraManager::class.java)
        return manager.cameraIdList.mapNotNull { id -> runCatching {
            val c=manager.getCameraCharacteristics(id)
            val facing=if(c[C.LENS_FACING]==C.LENS_FACING_FRONT) "Depan" else "Belakang"
            CameraEntry(id,"$facing · ID $id · ${c[C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.firstOrNull() ?: "?"} mm",c)
        }.getOrNull() }.sortedBy { if(it.id=="0") 0 else if(it.front) 2 else 1 }
    }
}

object CodecSupport {
    fun supports(p: VideoProfile, hevc: Boolean): Boolean {
        val mime=if(hevc) "video/hevc" else "video/avc"
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.contains(mime) && runCatching {
                val caps=info.getCapabilitiesForType(mime)
                caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) &&
                    caps.videoCapabilities.areSizeAndRateSupported(p.width,p.height,p.fps.toDouble())
            }.getOrDefault(false)
        }
    }
}
