package id.tege.video.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import id.tege.video.core.LensSwitchAnalysis
import id.tege.video.core.ZoomObservation
import id.tege.video.core.ZoomPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Diagnostic for the "Uji telefoto" button. It never records and never changes settings.
 * Every stage is reported separately (listed, metadata readable, opened, session configured, frames received)
 * so a failure names the stage that failed instead of a vague "does not work".
 * The caller must release the app's own camera first; the probe opens cameras itself.
 */
object TelephotoProbe {
    private const val OPEN_TIMEOUT_MS=4000L
    private const val CONFIG_TIMEOUT_MS=4000L
    private const val STEP_TIMEOUT_MS=2500L
    private const val FRAMES_PER_STEP=10
    private const val TELE_RATIO=1.35f

    private class Step { val frames=AtomicInteger(0);@Volatile var reported: Float?=null;@Volatile var physical: String?=null
        @Volatile var focal: Float?=null;@Volatile var crop: String?=null;val done=CountDownLatch(1) }
    private class SessionResult(val json: JSONObject,val observations: List<ZoomObservation>)

    /** Zoom ratios to test for one camera: its own minimum, then 1x, 1.5x, 2x ... 10x, limited to what it supports. */
    private fun sweepRatios(c: C?): List<Float> {
        val range=if(Build.VERSION.SDK_INT>=30) c?.get(C.CONTROL_ZOOM_RATIO_RANGE) else null
        val minZoom=range?.lower ?: 1f;val maxZoom=range?.upper ?: (c?.get(C.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
        return listOf(minZoom,1f,1.5f,2f,2.4f,3f,4f,5f,7f,10f).filter { it>=minZoom-.001f && it<=maxZoom+.001f }
            .map { ZoomPolicy.clamp(it,minZoom,maxZoom) }.distinct()
    }
    private fun describe(e: Throwable)="${e.javaClass.simpleName}: ${e.message}"
    private fun focalMm(c: C): Float? = c[C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.minOrNull()
    private fun equivalent(c: C): Float? {
        val focal=focalMm(c) ?: return null
        val width=c[C.SENSOR_INFO_PHYSICAL_SIZE]?.width ?: return null
        return if(width>0f) focal/width else null
    }
    private fun facing(c: C)=when(c[C.LENS_FACING]) { C.LENS_FACING_FRONT->"depan";C.LENS_FACING_BACK->"belakang";C.LENS_FACING_EXTERNAL->"eksternal";else->"tidak diketahui" }
    private fun errorName(code: Int)=when(code) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE->"kamera sedang dipakai"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE->"batas kamera terbuka tercapai"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED->"kamera dinonaktifkan kebijakan"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE->"galat perangkat kamera"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE->"galat layanan kamera"
        else->"kode tidak dikenal"
    }

    @SuppressLint("MissingPermission")
    fun run(context: Context): JSONObject {
        val report=JSONObject().put("device","${Build.MANUFACTURER} ${Build.MODEL}").put("api",Build.VERSION.SDK_INT)
        if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            return report.put("error","Izin kamera belum diberikan.").put("summary","Izin kamera belum diberikan, uji tidak dijalankan.")
        val manager=context.getSystemService(CameraManager::class.java)
        val thread=HandlerThread("Cherry-Probe").apply { start() }
        val handler=Handler(thread.looper)
        try {
            val listed=manager.cameraIdList.toList()
            val parents=LinkedHashMap<String,MutableSet<String>>()
            listed.forEach { id ->
                val physical=runCatching { manager.getCameraCharacteristics(id).physicalCameraIds }.getOrNull().orEmpty()
                physical.forEach { p -> parents.getOrPut(p) { LinkedHashSet() }.add(id) }
            }
            val candidates=(listed+parents.keys+(0..9).map { it.toString() }).distinct()
            val chars=LinkedHashMap<String,C>()
            val failures=LinkedHashMap<String,String>()
            candidates.forEach { id ->
                runCatching { manager.getCameraCharacteristics(id) }.onSuccess { chars[id]=it }.onFailure { failures[id]=describe(it) }
            }
            val mainId=listed.firstOrNull { it=="0" && chars[it]?.get(C.LENS_FACING)==C.LENS_FACING_BACK }
                ?: listed.firstOrNull { chars[it]?.get(C.LENS_FACING)==C.LENS_FACING_BACK }
            val mainEquivalent=mainId?.let { chars[it] }?.let { equivalent(it) }
            val teleLike=ArrayList<String>()
            val hiddenReadable=ArrayList<String>()
            val items=JSONArray()
            candidates.forEach { id ->
                val c=chars[id]
                val item=JSONObject().put("id",id).put("inCameraIdList",id in listed)
                    .put("physicalOf",JSONArray((parents[id] ?: emptySet<String>()).toList()))
                if(c==null) item.put("metadata",JSONObject().put("ok",false).put("error",failures[id] ?: "tidak terbaca"))
                else {
                    val ratio=if(mainEquivalent!=null && mainEquivalent>0f) equivalent(c)?.div(mainEquivalent) else null
                    val kind=when { id==mainId->"utama";ratio==null->"tidak diketahui";ratio<.85f->"ultrawide";ratio>TELE_RATIO->"telefoto";else->"setara utama" }
                    if(kind=="telefoto") teleLike.add(id)
                    if(id !in listed && id !in parents) hiddenReadable.add(id)
                    val profiles=runCatching { CameraEntry(id,"probe",c).profiles(false).map { it.label } }.getOrDefault(emptyList())
                    item.put("metadata",JSONObject().put("ok",true).put("facing",facing(c)).put("kind",kind)
                        .put("focalLengthsMm",JSONArray((c[C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS] ?: floatArrayOf()).map { it.toDouble() }))
                        .put("sensorWidthMm",(c[C.SENSOR_INFO_PHYSICAL_SIZE]?.width ?: 0f).toDouble())
                        .put("ratioToMain",ratio?.toDouble() ?: JSONObject.NULL)
                        .put("physicalIds",JSONArray(c.physicalCameraIds.toList()))
                        .put("zoomRatioRange",if(Build.VERSION.SDK_INT>=30) c[C.CONTROL_ZOOM_RATIO_RANGE]?.toString() ?: JSONObject.NULL else JSONObject.NULL)
                        .put("avcVideoProfiles",JSONArray(profiles)))
                }
                items.put(item)
            }
            report.put("cameraIdList",JSONArray(listed)).put("mainId",mainId ?: JSONObject.NULL).put("candidates",items)

            // Stage: open each interesting camera and check that frames really arrive.
            val probes=JSONArray()
            val toOpen=LinkedHashSet<Pair<String,String?>>()
            teleLike.forEach { id -> if(id in listed) toOpen.add(id to null) else parents[id]?.firstOrNull()?.let { toOpen.add(it to id) } }
            hiddenReadable.forEach { id -> toOpen.add(id to null) }
            toOpen.forEach { (openId,physicalId) ->
                val result=runSession(manager,handler,openId,physicalId,listOf(1f))
                probes.put(result.json);Thread.sleep(200)
            }
            report.put("openProbes",probes)

            // Stage: does the main (logical) camera switch lenses while zooming?
            var verdictText="Kamera utama tidak ditemukan, uji zoom dilewati."
            if(mainId!=null) {
                val ratios=sweepRatios(chars[mainId])
                val sweep=runSession(manager,handler,mainId,null,ratios)
                val verdict=LensSwitchAnalysis.analyze(sweep.observations)
                verdictText=verdict.text
                report.put("zoomSweep",sweep.json.put("verdict",JSONObject().put("switched",verdict.switched)
                    .put("firstSwitchZoom",verdict.firstSwitchZoom?.toDouble() ?: JSONObject.NULL).put("text",verdict.text)))
            }

            // Stage: hidden multi-lens cameras (for example an id that groups ultrawide, main and telephoto).
            val hiddenSweeps=JSONArray();val hiddenTexts=ArrayList<String>()
            (hiddenReadable+teleLike).distinct().filter { id -> id !in listed && (chars[id]?.physicalCameraIds?.isNotEmpty()==true) }.forEach { id ->
                val sweep=runSession(manager,handler,id,null,sweepRatios(chars[id]))
                val verdict=LensSwitchAnalysis.analyze(sweep.observations)
                val lenses=sweep.observations.mapNotNull { it.physicalId }.distinct()
                hiddenSweeps.put(sweep.json.put("verdict",JSONObject().put("switched",verdict.switched)
                    .put("firstSwitchZoom",verdict.firstSwitchZoom?.toDouble() ?: JSONObject.NULL).put("text",verdict.text)
                    .put("lensesSeen",JSONArray(lenses))))
                hiddenTexts.add("ID $id (sapuan zoom): "+if(sweep.observations.none { it.frames>0 }) "tidak ada frame." else "lensa terlihat ${lenses.joinToString().ifEmpty { "-" }}; ${verdict.text}")
                Thread.sleep(200)
            }
            report.put("hiddenSweeps",hiddenSweeps)

            val lines=ArrayList<String>()
            lines.add("ID kamera yang dilaporkan Android: ${listed.joinToString()}")
            lines.add(if(parents.isEmpty()) "Tidak ada lensa fisik yang diumumkan kamera logis." else "Lensa fisik yang diumumkan: "+parents.entries.joinToString { (p,l) -> "$p (anggota ${l.joinToString()})" })
            mainId?.let { id ->
                val focals=chars[id]?.get(C.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
                lines.add("Panjang fokus yang dilaporkan kamera $id: ${focals.joinToString { "%.1f mm".format(java.util.Locale.US,it) }.ifEmpty { "tidak ada" }}")
            }
            lines.add(if(hiddenReadable.isEmpty()) "ID tersembunyi yang bisa dibaca: tidak ada." else "ID tersembunyi yang bisa dibaca: ${hiddenReadable.joinToString()}.")
            lines.add(if(teleLike.isEmpty()) "Lensa telefoto yang terlihat oleh aplikasi: tidak ada." else "Calon telefoto: ${teleLike.joinToString()}.")
            for(i in 0 until probes.length()) {
                val p=probes.getJSONObject(i)
                lines.add("Buka ID ${p.optString("openId")}${if(p.has("physicalId") && !p.isNull("physicalId")) " (fisik ${p.optString("physicalId")})" else ""}: " +
                    if(p.optBoolean("framesReceived")) "preview jalan." else "gagal — ${p.optString("error","tanpa keterangan")}")
            }
            lines.add(verdictText)
            hiddenTexts.forEach { lines.add(it) }
            lines.add("Catatan: ini bukti perilaku kamera di sesi aplikasi ini. Rekaman nyata diuji lewat Diagnostik → Pilih lensa teknis.")
            report.put("summary",lines.joinToString("\n"))
        } catch(e: Throwable) {
            report.put("error",describe(e)).put("summary","Uji berhenti: ${describe(e)}")
        } finally { thread.quitSafely() }
        return report
    }

    private fun failed(base: JSONObject,stage: String,message: String): SessionResult {
        base.put("stage",stage).put("error",message).put("framesReceived",false)
        return SessionResult(base,emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun runSession(manager: CameraManager,handler: Handler,openId: String,physicalId: String?,ratios: List<Float>): SessionResult {
        val out=JSONObject().put("openId",openId).put("physicalId",physicalId ?: JSONObject.NULL)
        val deviceRef=AtomicReference<CameraDevice?>(null)
        val openError=AtomicReference<String?>(null)
        val opened=CountDownLatch(1)
        var session: CameraCaptureSession?=null
        var reader: ImageReader?=null
        try {
            val target=runCatching { manager.getCameraCharacteristics(physicalId ?: openId) }.getOrElse { return failed(out,"metadata",describe(it)) }
            val openChars=runCatching { manager.getCameraCharacteristics(openId) }.getOrElse { return failed(out,"metadata",describe(it)) }
            val map=target[C.SCALER_STREAM_CONFIGURATION_MAP] ?: return failed(out,"metadata","Peta konfigurasi stream tidak tersedia.")
            val sizes=map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
            val size=sizes.filter { it.width<=1280 && it.height<=960 }.maxByOrNull { it.width*it.height } ?: sizes.minByOrNull { it.width*it.height }
                ?: return failed(out,"metadata","Tidak ada ukuran YUV untuk uji preview.")
            try {
                manager.openCamera(openId,object: CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) { deviceRef.set(camera);opened.countDown() }
                    override fun onDisconnected(camera: CameraDevice) { camera.close();openError.set("Kamera terputus.");opened.countDown() }
                    override fun onError(camera: CameraDevice,error: Int) { camera.close();openError.set("Camera2 error $error (${errorName(error)})");opened.countDown() }
                },handler)
            } catch(e: Throwable) { openError.set(describe(e));opened.countDown() }
            if(!opened.await(OPEN_TIMEOUT_MS,TimeUnit.MILLISECONDS)) openError.compareAndSet(null,"Waktu habis saat membuka kamera.")
            val device=deviceRef.get()
            if(device==null) return failed(out,"open",openError.get() ?: "Kamera tidak terbuka.")
            out.put("opened",true)

            val readerLocal=ImageReader.newInstance(size.width,size.height,ImageFormat.YUV_420_888,3)
            reader=readerLocal
            readerLocal.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } },handler)
            val steps=ratios.map { Step() }
            val useRatio=Build.VERSION.SDK_INT>=30 && openChars[C.CONTROL_ZOOM_RATIO_RANGE]!=null
            val callback=object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession,request: CaptureRequest,result: TotalCaptureResult) {
                    val index=request.tag as? Int ?: return
                    val step=steps.getOrNull(index) ?: return
                    step.reported=if(Build.VERSION.SDK_INT>=30) result[CaptureResult.CONTROL_ZOOM_RATIO] else null
                    step.physical=result[CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID]
                    step.focal=result[CaptureResult.LENS_FOCAL_LENGTH]
                    step.crop=result[CaptureResult.SCALER_CROP_REGION]?.toShortString()
                    if(step.frames.incrementAndGet()>=FRAMES_PER_STEP) step.done.countDown()
                }
            }
            val configured=CountDownLatch(1)
            val configError=AtomicReference<String?>(null)
            val sessionRef=AtomicReference<CameraCaptureSession?>(null)
            val output=OutputConfiguration(readerLocal.surface)
            if(physicalId!=null) output.setPhysicalCameraId(physicalId)
            device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,listOf(output),Executor { handler.post(it) },
                object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(value: CameraCaptureSession) { sessionRef.set(value);configured.countDown() }
                    override fun onConfigureFailed(value: CameraCaptureSession) { value.close();configError.set("Kamera menolak sesi preview ${size.width}x${size.height}.");configured.countDown() }
                }))
            if(!configured.await(CONFIG_TIMEOUT_MS,TimeUnit.MILLISECONDS)) configError.compareAndSet(null,"Waktu habis saat menyiapkan sesi.")
            val live=sessionRef.get() ?: return failed(out,"session",configError.get() ?: "Sesi tidak terbentuk.")
            session=live
            out.put("sessionConfigured",true).put("previewSize","${size.width}x${size.height}")

            val active=openChars[C.SENSOR_INFO_ACTIVE_ARRAY_SIZE]
            val observations=ArrayList<ZoomObservation>()
            val stepJson=JSONArray()
            ratios.forEachIndexed { index,ratio ->
                val builder=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(readerLocal.surface);setTag(index) }
                if(useRatio && Build.VERSION.SDK_INT>=30) builder.set(CaptureRequest.CONTROL_ZOOM_RATIO,ratio)
                else if(active!=null) {
                    val crop=ZoomPolicy.crop(active.left,active.top,active.right,active.bottom,ratio.coerceAtLeast(1f))
                    builder.set(CaptureRequest.SCALER_CROP_REGION,android.graphics.Rect(crop[0],crop[1],crop[2],crop[3]))
                }
                live.setRepeatingRequest(builder.build(),callback,handler)
                steps[index].done.await(STEP_TIMEOUT_MS,TimeUnit.MILLISECONDS)
                val s=steps[index]
                observations.add(ZoomObservation(ratio,s.reported,s.physical,s.focal,s.frames.get()))
                stepJson.put(JSONObject().put("requestedZoom",ratio.toDouble()).put("reportedZoom",s.reported?.toDouble() ?: JSONObject.NULL)
                    .put("activePhysicalId",s.physical ?: JSONObject.NULL).put("focalMm",s.focal?.toDouble() ?: JSONObject.NULL)
                    .put("cropRegion",s.crop ?: JSONObject.NULL).put("frames",s.frames.get()))
            }
            out.put("steps",stepJson).put("framesReceived",observations.any { it.frames>0 })
            if(!out.getBoolean("framesReceived")) out.put("stage","frames").put("error","Sesi terbentuk tetapi tidak ada frame yang masuk.")
            return SessionResult(out,observations)
        } catch(e: Throwable) {
            return failed(out,"run",describe(e))
        } finally {
            runCatching { session?.close() }
            runCatching { deviceRef.get()?.close() }
            runCatching { reader?.close() }
        }
    }
}
