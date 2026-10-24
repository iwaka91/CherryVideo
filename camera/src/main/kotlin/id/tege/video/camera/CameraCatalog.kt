package id.tege.video.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Range
import android.util.Size
import id.tege.video.core.VideoPolicy
import id.tege.video.core.VideoProfile
import org.json.JSONArray
import org.json.JSONObject

data class CameraEntry(val id: String, val label: String, val characteristics: C,
    val logicalId: String?=null, val logicalCharacteristics: C?=null) {
    val openId: String get()=logicalId ?: id
    val selectionKey: String get()="$openId/$id"
    val physical: Boolean get()=logicalId!=null
    val physicalCrop: Boolean get()=physical && logicalCharacteristics?.availablePhysicalCameraRequestKeys?.contains(CaptureRequest.SCALER_CROP_REGION)==true
    val ratioZoom: Boolean get()= !physical && Build.VERSION.SDK_INT>=30 && characteristics[C.CONTROL_ZOOM_RATIO_RANGE]!=null
    val zoomMin: Float get()=if(ratioZoom) characteristics[C.CONTROL_ZOOM_RATIO_RANGE]!!.lower else 1f
    val zoomMax: Float get()=if(physical && !physicalCrop) 1f else if(ratioZoom) characteristics[C.CONTROL_ZOOM_RATIO_RANGE]!!.upper
        else (characteristics[C.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM] ?: 1f).coerceAtLeast(1f)
    val equivalentFocal: Float get()=(characteristics[C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.minOrNull() ?: 0f) /
        (characteristics[C.SENSOR_INFO_PHYSICAL_SIZE]?.width ?: 1f).coerceAtLeast(.001f)

    val front: Boolean get() = characteristics[C.LENS_FACING] == C.LENS_FACING_FRONT
    val rotation: Int get() = characteristics[C.SENSOR_ORIENTATION] ?: 90
    val minFocus: Float get() = characteristics[C.LENS_INFO_MINIMUM_FOCUS_DISTANCE] ?: 0f
    val manual: Boolean get() = characteristics[C.REQUEST_AVAILABLE_CAPABILITIES]?.contains(C.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
    val ois: Boolean get() = characteristics[C.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION]?.contains(1) == true
    val eis: Boolean get() = characteristics[C.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES]?.contains(1) == true
    val ranges: List<Range<Int>> get() = characteristics[C.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]?.toList().orEmpty()
    fun range(fps: Int): Range<Int>? = ranges.filter { it.upper == fps }.minByOrNull { it.upper - it.lower }
    fun highSpeedRates(width: Int, height: Int): List<Int> = runCatching {
        if(physical) return@runCatching emptyList()
        val map=characteristics[C.SCALER_STREAM_CONFIGURATION_MAP] ?: return@runCatching emptyList()
        if(characteristics[C.REQUEST_AVAILABLE_CAPABILITIES]?.contains(C.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)!=true) return@runCatching emptyList()
        map.getHighSpeedVideoFpsRangesFor(Size(width,height)).filter { it.lower==it.upper && it.upper>=120 && it.upper%60==0 }.map { it.upper }.sorted()
    }.getOrDefault(emptyList())
    fun profiles(hevc: Boolean): List<VideoProfile> {
        val map=characteristics[C.SCALER_STREAM_CONFIGURATION_MAP] ?: return emptyList()
        val sizes=map.getOutputSizes(SurfaceTexture::class.java)?.toSet().orEmpty()
        val normal=listOf(VideoProfile(1920,1080,30), VideoProfile(1920,1080,60), VideoProfile(3840,2160,30), VideoProfile(1280,720,30)).filter { p ->
            val size=Size(p.width,p.height)
            size in sizes && range(p.fps) != null && VideoPolicy.qualifies(p, range(p.fps)!!.upper,
                map.getOutputMinFrameDuration(SurfaceTexture::class.java,size), CodecSupport.supports(p,hevc))
        }
        val highSpeed=highSpeedRates(1920,1080).firstOrNull()?.let { VideoProfile(1920,1080,60,it) }
            ?.takeIf { Size(it.width,it.height) in sizes && CodecSupport.supports(it,hevc) }
        return (normal + listOfNotNull(highSpeed?.takeIf { normal.none { p -> p.fps==60 } }))
            .sortedWith(compareBy<VideoProfile> { if(it.height==1080) 0 else if(it.width>=3840) 1 else 2 }.thenBy { it.fps })
    }
    fun report(): JSONObject = JSONObject().put("id",id).put("label",label).put("front",front)
        .put("logicalId",logicalId ?: JSONObject.NULL).put("openId",openId).put("physicalSelection",physical)
        .put("zoomMin",zoomMin.toDouble()).put("zoomMax",zoomMax.toDouble()).put("zoomMethod",if(ratioZoom) "ratio" else if(physical && !physicalCrop) "fixed lens" else "crop")
        .put("sensorRotation",rotation).put("minFocusDiopter",minFocus.toDouble()).put("manualSensor",manual)
        .put("ois",ois).put("eis",eis).put("timestampSource",characteristics[C.SENSOR_INFO_TIMESTAMP_SOURCE])
        .put("normalAeRanges",JSONArray(ranges.map { listOf(it.lower,it.upper) }))
        .put("highSpeed1080CaptureRates",JSONArray(highSpeedRates(1920,1080)))
        .put("physicalIds",JSONArray(characteristics.physicalCameraIds.toList()))
        .put("profiles",JSONArray(listOf(false,true).flatMap { hevc -> profiles(hevc).map { p ->
            JSONObject().put("label",p.label).put("codec",if(hevc) "HEVC" else "AVC").put("captureFps",p.captureFps).put("highSpeed",p.highSpeed)
        } }))
        .put("regularCandidates",JSONArray(listOf(VideoProfile(1920,1080,30),VideoProfile(1920,1080,60),VideoProfile(3840,2160,30),VideoProfile(1280,720,30)).map { p ->
            val map=characteristics[C.SCALER_STREAM_CONFIGURATION_MAP]
            val size=Size(p.width,p.height)
            val sizeAvailable=map?.getOutputSizes(SurfaceTexture::class.java)?.contains(size)==true
            JSONObject().put("label",p.label).put("sizeAvailable",sizeAvailable)
                .put("minFrameDurationNs",if(sizeAvailable) map!!.getOutputMinFrameDuration(SurfaceTexture::class.java,size) else -1)
                .put("aeRange",range(p.fps)?.toString() ?: "unavailable")
                .put("avcEncoder",CodecSupport.supports(p,false)).put("hevcEncoder",CodecSupport.supports(p,true))
        }))
}

object CameraCatalog {
    fun physicalReport(context: Context,entries: List<CameraEntry>): JSONArray {
        val manager=context.getSystemService(CameraManager::class.java)
        return JSONArray(entries.flatMap { it.characteristics.physicalCameraIds }.distinct().map { id ->
            runCatching { CameraEntry(id,"Physical $id",manager.getCameraCharacteristics(id)).report() }
                .getOrElse { JSONObject().put("id",id).put("error",it.message) }
        })
    }
    fun list(context: Context): List<CameraEntry> {
        val manager=context.getSystemService(CameraManager::class.java)
        val public=manager.cameraIdList.mapNotNull { id -> runCatching {
            CameraEntry(id,"Kamera $id",manager.getCameraCharacteristics(id))
        }.getOrNull() }
        val physical=public.filter { !it.front }.flatMap { parent ->
            parent.characteristics.physicalCameraIds.filter { id -> public.none { it.id==id } }.mapNotNull { id ->
                runCatching { CameraEntry(id,"Lensa $id",manager.getCameraCharacteristics(id),parent.id,parent.characteristics) }
                    .getOrNull()?.takeIf { it.profiles(false).isNotEmpty() || it.profiles(true).isNotEmpty() }
            }
        }.distinctBy { it.id }
        val main=public.firstOrNull { it.id=="0" && !it.front } ?: public.firstOrNull { !it.front }
        val reference=main?.equivalentFocal?.takeIf { it>0 } ?: 1f
        return (public+physical).map { camera ->
            val ratio=camera.equivalentFocal/reference
            val kind=if(camera.front) "Depan" else if(camera.id==main?.id) "Utama" else when {
                camera.equivalentFocal<=0f->"Belakang";ratio<.85f->"Ultrawide";ratio>1.35f->"Telefoto";else->"Belakang"
            }
            camera.copy(label="$kind · ID ${camera.id}${if(camera.physical) " · fisik" else ""}")
        }.sortedWith(compareBy<CameraEntry> { if(it.id==main?.id) 0 else if(it.front) 2 else 1 }.thenBy { it.equivalentFocal })
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
