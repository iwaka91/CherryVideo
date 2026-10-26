package id.tege.video.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Size
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import id.tege.video.core.BokehAssessment
import id.tege.video.core.TimingStats
import id.tege.video.core.YuvToArgb
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Diagnostic for the "Uji Bokeh" button. It never records and never changes settings.
 * It opens the back camera itself with a 1080p30 stream (stand-in for the recording stream, frames are discarded)
 * plus a small extra YUV stream that feeds MediaPipe person segmentation on the device, first on GPU, then on CPU.
 * Measures time per image, processed FPS and temperature. The caller must release the app's own camera first.
 */
object BokehProbe {
    const val MODEL_ASSET="selfie_segmenter.tflite"
    private const val WARMUP_FRAMES=5
    private const val OPEN_TIMEOUT_MS=4000L
    private const val CONFIG_TIMEOUT_MS=4000L
    private const val FIRST_FRAME_TIMEOUT_MS=3000L

    private class Phase(val label: String,val delegate: Delegate) {
        var segmenter: ImageSegmenter?=null
        var initMs=0.0;var error: String?=null;var firstMs: Double?=null
        val convertMs=ArrayList<Double>();val segmentMs=ArrayList<Double>();val totalMs=ArrayList<Double>()
        var seen=0;var processed=0;var startNs=0L;var endNs=0L;var lastTs=-1L
        var coverageSum=0.0;var coverageCount=0
        var cameraStart=0;var cameraEnd=0
    }
    private class Buffers(val width: Int,val height: Int) {
        val argb=IntArray(width*height)
        val bitmap: Bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        var y=ByteArray(0);var u=ByteArray(0);var v=ByteArray(0)
    }

    private fun describe(e: Throwable)="${e.javaClass.simpleName}: ${e.message}"
    private fun ms(start: Long,end: Long)=(end-start)/1_000_000.0
    private fun f1(x: Double)=String.format(Locale.US,"%.1f",x)
    private fun thermalName(status: Int)=when(status) {
        0->"normal";1->"ringan";2->"sedang";3->"berat";4->"kritis";5->"darurat";6->"mati";else->"tidak diketahui"
    }

    /** Battery temperature (°C), thermal status and thermal headroom, sampled once. */
    private fun thermal(context: Context,elapsedS: Double): JSONObject {
        val out=JSONObject().put("t",elapsedS)
        val filter=IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery=runCatching { if(Build.VERSION.SDK_INT>=33) context.registerReceiver(null,filter,Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(null,filter) }.getOrNull()
        val tenth=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE) ?: Int.MIN_VALUE
        out.put("batteryC",if(tenth==Int.MIN_VALUE) JSONObject.NULL else tenth/10.0)
        val power=context.getSystemService(PowerManager::class.java)
        val status=runCatching { power.currentThermalStatus }.getOrDefault(-1)
        out.put("thermalStatus",status).put("thermalLabel",thermalName(status))
        val headroom=if(Build.VERSION.SDK_INT>=30) runCatching { power.getThermalHeadroom(10) }.getOrDefault(Float.NaN) else Float.NaN
        out.put("headroom10s",if(headroom.isNaN()) JSONObject.NULL else headroom.toDouble())
        return out
    }

    private fun modelSha256(context: Context): String? = runCatching {
        val digest=MessageDigest.getInstance("SHA-256")
        context.assets.open(MODEL_ASSET).use { input -> val buf=ByteArray(16384);while(true) { val n=input.read(buf);if(n<0) break;digest.update(buf,0,n) } }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Small YUV size for segmentation: 640x360 if offered, otherwise the smallest 16:9 (or any) size at least 320 wide. */
    private fun smallSize(sizes: List<Size>): Size? =
        sizes.firstOrNull { it.width==640 && it.height==360 }
            ?: sizes.filter { it.width in 320..800 && abs(it.width*9-it.height*16)<=it.width/10 }.minByOrNull { it.width*it.height }
            ?: sizes.filter { it.width>=320 }.minByOrNull { it.width*it.height }
            ?: sizes.minByOrNull { it.width*it.height }

    private fun copyPlane(plane: Image.Plane,current: ByteArray): ByteArray {
        val buffer=plane.buffer.duplicate().apply { rewind() }
        val target=if(current.size==buffer.remaining()) current else ByteArray(buffer.remaining())
        buffer.get(target);return target
    }

    private fun createSegmenter(context: Context,delegate: Delegate): ImageSegmenter {
        val base=BaseOptions.builder().setModelAssetPath(MODEL_ASSET).setDelegate(delegate).build()
        val options=ImageSegmenter.ImageSegmenterOptions.builder().setBaseOptions(base).setRunningMode(RunningMode.VIDEO)
            .setOutputConfidenceMasks(true).setOutputCategoryMask(false).build()
        return ImageSegmenter.createFromOptions(context,options)
    }

    /** Runs on the frame thread: convert, segment, measure. Frames that arrive while busy are dropped by acquireLatestImage. */
    private fun process(phase: Phase,image: Image,buffers: Buffers,rotation: ImageProcessingOptions) {
        val segmenter=phase.segmenter ?: return
        phase.seen++
        val t0=System.nanoTime()
        val planes=image.planes
        buffers.y=copyPlane(planes[0],buffers.y);buffers.u=copyPlane(planes[1],buffers.u);buffers.v=copyPlane(planes[2],buffers.v)
        YuvToArgb.convert(buffers.y,planes[0].rowStride,buffers.u,buffers.v,planes[1].rowStride,planes[1].pixelStride,buffers.width,buffers.height,buffers.argb)
        buffers.bitmap.setPixels(buffers.argb,0,buffers.width,0,0,buffers.width,buffers.height)
        val t1=System.nanoTime()
        val ts=maxOf(image.timestamp/1_000_000,phase.lastTs+1);phase.lastTs=ts
        val result=segmenter.segmentForVideo(BitmapImageBuilder(buffers.bitmap).build(),rotation,ts)
        val t2=System.nanoTime()
        val masks=result.confidenceMasks().orElse(null)
        masks?.firstOrNull()?.let { mask ->
            runCatching {
                val floats=ByteBufferExtractor.extract(mask).order(ByteOrder.nativeOrder()).asFloatBuffer()
                var hit=0;var total=0;var i=0
                while(i<floats.limit()) { if(floats.get(i)>.5f) hit++;total++;i+=7 }
                if(total>0) { phase.coverageSum+=hit.toDouble()/total;phase.coverageCount++ }
            }
        }
        masks?.forEach { runCatching { it.close() } }
        val total=ms(t0,t2)
        if(phase.firstMs==null) phase.firstMs=total
        phase.processed++
        if(phase.processed>WARMUP_FRAMES) { phase.convertMs.add(ms(t0,t1));phase.segmentMs.add(ms(t1,t2));phase.totalMs.add(total) }
    }

    private fun phaseJson(phase: Phase,cameraFps: Double): JSONObject {
        val seconds=if(phase.endNs>phase.startNs) ms(phase.startNs,phase.endNs)/1000.0 else 0.0
        val processedFps=if(seconds>0) phase.processed/seconds else 0.0
        val camFps=if(seconds>0) (phase.cameraEnd-phase.cameraStart)/seconds else cameraFps
        val total=TimingStats.of(phase.totalMs);val seg=TimingStats.of(phase.segmentMs);val conv=TimingStats.of(phase.convertMs)
        fun stats(s: TimingStats)=JSONObject().put("count",s.count).put("mean",s.mean).put("p50",s.p50).put("p90",s.p90).put("max",s.max)
        return JSONObject().put("delegate",phase.label).put("error",phase.error ?: JSONObject.NULL)
            .put("initMs",phase.initMs).put("firstFrameMs",phase.firstMs ?: JSONObject.NULL)
            .put("seconds",seconds).put("framesSeen",phase.seen).put("framesProcessed",phase.processed)
            .put("processedFps",processedFps).put("cameraFps",camFps).put("warmupFramesExcluded",WARMUP_FRAMES)
            .put("totalMs",stats(total)).put("segmentMs",stats(seg)).put("convertMs",stats(conv))
            .put("personCoverage",if(phase.coverageCount>0) phase.coverageSum/phase.coverageCount else JSONObject.NULL)
            .put("assessment",BokehAssessment.text(processedFps,camFps,total.p90))
    }

    @SuppressLint("MissingPermission")
    fun run(context: Context,gpuMs: Long=10_000,cpuMs: Long=5_000): JSONObject {
        val report=JSONObject().put("device","${Build.MANUFACTURER} ${Build.MODEL}").put("api",Build.VERSION.SDK_INT)
            .put("model",MODEL_ASSET).put("recording",false)
        if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            return report.put("error","Izin kamera belum diberikan.").put("summary","Izin kamera belum diberikan, uji tidak dijalankan.")
        val sha=modelSha256(context)
            ?: return report.put("error","Model $MODEL_ASSET tidak ada di APK.").put("summary","Model segmentasi tidak ditemukan di aplikasi, uji tidak dijalankan.")
        report.put("modelSha256",sha)
        val manager=context.getSystemService(CameraManager::class.java)
        val cameraThread=HandlerThread("Cherry-BokehCam").apply { start() }
        val frameThread=HandlerThread("Cherry-BokehFrame").apply { start() }
        val cameraHandler=Handler(cameraThread.looper);val frameHandler=Handler(frameThread.looper)
        val deviceRef=AtomicReference<CameraDevice?>(null)
        var session: CameraCaptureSession?=null
        var small: ImageReader?=null;var big: ImageReader?=null
        val phases=listOf(Phase("GPU",Delegate.GPU),Phase("CPU",Delegate.CPU))
        val thermalSamples=JSONArray()
        val started=System.nanoTime()
        fun runOnFrame(block: ()->Unit) { val done=CountDownLatch(1);frameHandler.post { try { block() } finally { done.countDown() } };done.await(20,TimeUnit.SECONDS) }
        try {
            val listed=manager.cameraIdList.toList()
            val id=listed.firstOrNull { it=="0" && manager.getCameraCharacteristics(it)[C.LENS_FACING]==C.LENS_FACING_BACK }
                ?: listed.firstOrNull { manager.getCameraCharacteristics(it)[C.LENS_FACING]==C.LENS_FACING_BACK }
                ?: return report.put("error","Kamera belakang tidak ditemukan.").put("summary","Kamera belakang tidak ditemukan.")
            val chars=manager.getCameraCharacteristics(id)
            val map=chars[C.SCALER_STREAM_CONFIGURATION_MAP] ?: return report.put("error","Peta stream tidak tersedia.").put("summary","Peta stream kamera tidak tersedia.")
            val smallSize=smallSize(map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty())
                ?: return report.put("error","Tidak ada ukuran YUV.").put("summary","Kamera tidak menawarkan aliran gambar kecil.")
            val bigSize=map.getOutputSizes(ImageFormat.PRIVATE)?.firstOrNull { it.width==1920 && it.height==1080 }
            val sensor=chars[C.SENSOR_ORIENTATION] ?: 90
            val rotation=ImageProcessingOptions.builder().setRotationDegrees(sensor).build()
            report.put("cameraId",id).put("smallStream","${smallSize.width}x${smallSize.height}").put("sensorOrientation",sensor)
            thermalSamples.put(thermal(context,0.0))

            // Open the camera.
            val opened=CountDownLatch(1);val openError=AtomicReference<String?>(null)
            try {
                manager.openCamera(id,object: CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) { deviceRef.set(camera);opened.countDown() }
                    override fun onDisconnected(camera: CameraDevice) { camera.close();openError.set("Kamera terputus.");opened.countDown() }
                    override fun onError(camera: CameraDevice,error: Int) { camera.close();openError.set("Camera2 error $error");opened.countDown() }
                },cameraHandler)
            } catch(e: Throwable) { openError.set(describe(e));opened.countDown() }
            if(!opened.await(OPEN_TIMEOUT_MS,TimeUnit.MILLISECONDS)) openError.compareAndSet(null,"Waktu habis saat membuka kamera.")
            val device=deviceRef.get() ?: return report.put("error",openError.get() ?: "Kamera tidak terbuka.").put("summary","Kamera tidak bisa dibuka: ${openError.get()}")

            val buffers=Buffers(smallSize.width,smallSize.height)
            val active=AtomicReference<Phase?>(null)
            val smallReader=ImageReader.newInstance(smallSize.width,smallSize.height,ImageFormat.YUV_420_888,3).also { small=it }
            smallReader.setOnImageAvailableListener({ r ->
                val image=runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try { active.get()?.let { phase -> runCatching { process(phase,image,buffers,rotation) }.onFailure { e -> if(phase.error==null) phase.error=describe(e) } } }
                finally { image.close() }
            },frameHandler)
            val bigFrames=AtomicInteger(0)
            val bigReader=bigSize?.let { s -> ImageReader.newInstance(s.width,s.height,ImageFormat.PRIVATE,3).also { big=it } }
            bigReader?.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() };bigFrames.incrementAndGet() },cameraHandler)

            // Configure: 1080p stand-in stream + small stream; if refused, small stream only.
            fun configure(surfaces: List<android.view.Surface>): CameraCaptureSession? {
                val done=CountDownLatch(1);val ref=AtomicReference<CameraCaptureSession?>(null)
                device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,surfaces.map { OutputConfiguration(it) },Executor { cameraHandler.post(it) },
                    object: CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) { ref.set(s);done.countDown() }
                        override fun onConfigureFailed(s: CameraCaptureSession) { s.close();done.countDown() }
                    }))
                done.await(CONFIG_TIMEOUT_MS,TimeUnit.MILLISECONDS);return ref.get()
            }
            var surfaces=listOfNotNull(bigReader?.surface,smallReader.surface)
            var live=configure(surfaces)
            if(live==null && bigReader!=null) { surfaces=listOf(smallReader.surface);live=configure(surfaces) }
            if(live==null) return report.put("error","Kamera menolak sesi uji.").put("summary","Kamera menolak sesi uji ${smallSize.width}x${smallSize.height}.")
            session=live
            val mainStream=bigReader!=null && surfaces.size==2
            report.put("mainStream",if(mainStream) "${bigSize!!.width}x${bigSize.height} (dibuang, pengganti aliran rekaman)" else "tidak dipakai (kamera menolak gabungan)")

            val cameraFrames=AtomicInteger(0)
            val firstFrame=CountDownLatch(1)
            val ranges=chars[C.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES].orEmpty()
            val fpsRange=ranges.firstOrNull { it.lower==30 && it.upper==30 } ?: ranges.filter { it.upper<=30 }.maxByOrNull { it.upper*100+it.lower }
            val request=device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                surfaces.forEach { addTarget(it) }
                fpsRange?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,it) }
            }.build()
            report.put("fpsRange",fpsRange?.toString() ?: JSONObject.NULL)
            live.setRepeatingRequest(request,object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession,r: CaptureRequest,result: TotalCaptureResult) { cameraFrames.incrementAndGet();firstFrame.countDown() }
            },cameraHandler)
            if(!firstFrame.await(FIRST_FRAME_TIMEOUT_MS,TimeUnit.MILLISECONDS))
                return report.put("error","Tidak ada frame kamera.").put("summary","Sesi kamera terbentuk tetapi tidak ada gambar yang masuk.")

            // Measure each delegate in turn.
            phases.forEach { phase ->
                val duration=if(phase.delegate==Delegate.GPU) gpuMs else cpuMs
                runOnFrame {
                    val t=System.nanoTime()
                    runCatching { createSegmenter(context,phase.delegate) }
                        .onSuccess { phase.segmenter=it;phase.initMs=ms(t,System.nanoTime()) }
                        .onFailure { e -> phase.error="Gagal memuat: ${describe(e)}" }
                    phase.startNs=System.nanoTime();phase.cameraStart=cameraFrames.get()
                    if(phase.segmenter!=null) active.set(phase)
                }
                if(phase.segmenter!=null) {
                    val end=System.currentTimeMillis()+duration
                    while(System.currentTimeMillis()<end) {
                        Thread.sleep(minOf(1000L,maxOf(1L,end-System.currentTimeMillis())))
                        thermalSamples.put(thermal(context,ms(started,System.nanoTime())/1000.0))
                    }
                }
                runOnFrame {
                    active.set(null);phase.endNs=System.nanoTime();phase.cameraEnd=cameraFrames.get()
                    runCatching { phase.segmenter?.close() };phase.segmenter=null
                }
            }
            val seconds=ms(started,System.nanoTime())/1000.0
            report.put("cameraFramesTotal",cameraFrames.get()).put("mainStreamFrames",bigFrames.get())

            val phaseJson=JSONArray();val lines=ArrayList<String>()
            lines.add("Uji Bokeh selesai (${f1(seconds)} detik, tanpa merekam, pengaturan tidak diubah).")
            lines.add("Kamera ID $id · aliran kecil ${smallSize.width}x${smallSize.height}"+if(mainStream) " + aliran 1080p." else " (tanpa aliran 1080p).")
            phases.forEach { phase ->
                val json=phaseJson(phase,30.0);phaseJson.put(json)
                val total=json.getJSONObject("totalMs");val seg=json.getJSONObject("segmentMs")
                lines.add(if(phase.processed==0) "${phase.label}: ${phase.error ?: "tidak ada gambar diproses."}"
                    else "${phase.label}: rata-rata ${f1(total.getDouble("mean"))} ms per gambar (segmentasi ${f1(seg.getDouble("mean"))} ms, terlambat 10%: ${f1(total.getDouble("p90"))} ms), "+
                        "${f1(json.getDouble("processedFps"))} FPS diproses dari ${f1(json.getDouble("cameraFps"))} fps kamera; ${json.getString("assessment")}"+
                        (phase.error?.let { " Galat: $it" } ?: ""))
            }
            report.put("phases",phaseJson)
            val coverage=phases.filter { it.coverageCount>0 }.let { list -> if(list.isEmpty()) null else list.sumOf { it.coverageSum }/list.sumOf { it.coverageCount } }
            lines.add(if(coverage==null) "Area orang: tidak terukur." else "Area orang terdeteksi rata-rata ${(coverage*100).toInt()}% gambar (0% berarti tidak ada orang di depan kamera).")
            val first=thermalSamples.optJSONObject(0);val last=thermalSamples.optJSONObject(thermalSamples.length()-1)
            if(first!=null && last!=null) {
                val b0=first.opt("batteryC");val b1=last.opt("batteryC")
                lines.add("Suhu baterai: ${if(b0 is Number) f1(b0.toDouble())+" °C" else "-"} → ${if(b1 is Number) f1(b1.toDouble())+" °C" else "-"}; status panas: ${first.optString("thermalLabel")} → ${last.optString("thermalLabel")}.")
            }
            lines.add("Catatan: angka ini dari HP ini saja. Bokeh langsung (langkah 2) juga harus menggambar blur, jadi bebannya lebih berat dari uji ini.")
            report.put("seconds",seconds).put("summary",lines.joinToString("\n"))
        } catch(e: Throwable) {
            report.put("error",describe(e)).put("summary","Uji berhenti: ${describe(e)}")
        } finally {
            report.put("thermal",thermalSamples)
            runCatching { session?.stopRepeating() }
            runCatching { session?.close() }
            runCatching { deviceRef.get()?.close() }
            runOnFrame { phases.forEach { runCatching { it.segmenter?.close() };it.segmenter=null } }
            runCatching { small?.close() };runCatching { big?.close() }
            cameraThread.quitSafely();frameThread.quitSafely()
        }
        return report
    }
}
