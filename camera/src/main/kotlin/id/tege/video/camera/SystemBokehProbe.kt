package id.tege.video.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads (without opening any camera or recording) whether the phone offers its own bokeh to other apps:
 * the Android "extended scene mode" (continuous bokeh for preview/video, still-capture bokeh for photos) and the
 * camera "Bokeh" extension (normally photos only). The result is evidence, not a promise that it works in a recording.
 */
object SystemBokehProbe {
    fun run(context: Context): JSONObject {
        val manager=context.getSystemService(CameraManager::class.java)
        val list=JSONArray()
        var continuous=0;var still=0;var extension=0
        val ids=runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        val lines=ArrayList<String>()
        for(id in ids) {
            val item=JSONObject().put("id",id)
            val c=runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
            if(c==null) { list.put(item.put("error","metadata tidak terbaca"));continue }
            val facing=if(c[CameraCharacteristics.LENS_FACING]==CameraMetadata.LENS_FACING_FRONT) "Depan" else "Belakang"
            item.put("facing",facing)
            val modes=JSONArray()
            var hasContinuous=false;var hasStill=false
            if(Build.VERSION.SDK_INT>=30) {
                c[CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES]?.forEach { cap ->
                    val name=when(cap.mode) {
                        CameraMetadata.CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS -> "BOKEH_CONTINUOUS".also { hasContinuous=true }
                        CameraMetadata.CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE -> "BOKEH_STILL_CAPTURE".also { hasStill=true }
                        CameraMetadata.CONTROL_EXTENDED_SCENE_MODE_DISABLED -> "DISABLED"
                        else -> "MODE_${cap.mode}"
                    }
                    modes.put(JSONObject().put("mode",name).put("maxStreamingSize",cap.maxStreamingSize.toString())
                        .put("zoomRange",cap.zoomRatioRange.toString()))
                }
            }
            item.put("extendedSceneModes",modes)
            var hasExtension=false
            if(Build.VERSION.SDK_INT>=31) {
                runCatching {
                    val ext=manager.getCameraExtensionCharacteristics(id)
                    hasExtension=CameraExtensionCharacteristics.EXTENSION_BOKEH in ext.supportedExtensions
                    item.put("bokehExtension",hasExtension)
                }.onFailure { item.put("bokehExtensionError",it.javaClass.simpleName) }
            }
            if(hasContinuous) continuous++
            if(hasStill) still++
            if(hasExtension) extension++
            list.put(item)
            lines.add("Kamera $id ($facing): bokeh video ${if(hasContinuous) "ADA" else "tidak"} · bokeh foto ${if(hasStill) "ada" else "tidak"} · ekstensi Bokeh ${if(hasExtension) "ada" else "tidak"}")
        }
        val conclusion=when {
            continuous>0 -> "HP menawarkan bokeh video sistem ke aplikasi. Layak dicoba sebagai pilihan tambahan (perlu uji rekam terpisah)."
            still>0 || extension>0 -> "HP hanya menawarkan bokeh sistem untuk foto, bukan video. Cherry Video tetap memakai Bokeh buatan sendiri."
            else -> "HP tidak menawarkan bokeh sistem ke aplikasi lain. Nilai F di kamera bawaan hanya milik aplikasi Xiaomi; Cherry Video tetap memakai Bokeh buatan sendiri."
        }
        val summary="Uji mode bokeh sistem selesai (hanya membaca data kamera, tanpa merekam).\n\n${lines.joinToString("\n")}\n\nKesimpulan: $conclusion"
        return JSONObject().put("summary",summary).put("device","${Build.MANUFACTURER} ${Build.MODEL}").put("api",Build.VERSION.SDK_INT)
            .put("cameras",list).put("continuousCount",continuous).put("stillCount",still).put("extensionCount",extension)
    }
}
