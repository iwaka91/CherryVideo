package id.tege.video.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import id.tege.video.core.MaskMath
import id.tege.video.core.YuvToArgb
import org.json.JSONObject
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Second, small camera stream (YUV) that feeds MediaPipe person segmentation on the CPU (as fast as the GPU on the Xiaomi 13T
 * in the measured test, and it leaves the GPU to the video). Each finished mask is turned back into camera-image orientation,
 * smoothed over time and handed to [onMask]. The stream keeps draining camera frames even after segmentation stops,
 * so the camera session never stalls.
 */
class PersonMaskStream(
    private val context: Context,
    val size: Size,
    private val onMask: (ByteArray,Int,Int,Float)->Unit,
    private val onProblem: (String)->Unit,
) : AutoCloseable {
    private val thread=HandlerThread("Cherry-PersonMask").apply { start() }
    private val handler=Handler(thread.looper)
    private val width=size.width
    private val height=size.height
    private val reader=ImageReader.newInstance(width,height,ImageFormat.YUV_420_888,3)
    val surface: Surface get() = reader.surface
    /** Clockwise turn that makes the camera image upright on screen; the model needs people upright. */
    @Volatile var degrees=90
    @Volatile private var segmenter: ImageSegmenter?=null
    @Volatile private var working=true
    @Volatile private var closed=false
    private var y=ByteArray(0)
    private var u=ByteArray(0)
    private var v=ByteArray(0)
    private val argb=IntArray(width*height)
    private var bitmap: Bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
    private val smoothed=ByteArray(width*height)
    private var presence=0f
    private var lastTs=-1L
    @Volatile var processedFrames=0L; private set
    @Volatile var meanMs=0.0; private set
    @Volatile var maskCount=0; private set
    @Volatile var lastError: String?=null; private set

    init {
        handler.post {
            if(closed) return@post
            try {
                val base=BaseOptions.builder().setModelAssetPath(BokehProbe.MODEL_ASSET).setDelegate(Delegate.CPU).build()
                val options=ImageSegmenter.ImageSegmenterOptions.builder().setBaseOptions(base).setRunningMode(RunningMode.VIDEO)
                    .setOutputConfidenceMasks(true).setOutputCategoryMask(false).build()
                segmenter=ImageSegmenter.createFromOptions(context,options)
            } catch(e: Throwable) { fail("Bokeh tidak bisa dimuat: ${e.javaClass.simpleName}") }
        }
        reader.setOnImageAvailableListener({ r ->
            val image=runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try { if(working && !closed) process(image) } catch(e: Throwable) { fail("Bokeh berhenti: ${e.javaClass.simpleName}: ${e.message}") }
            finally { image.close() }
        },handler)
    }

    private fun fail(message: String) {
        lastError=message
        if(!working) return
        working=false
        onProblem(message)
    }

    private fun copyPlane(plane: Image.Plane,current: ByteArray): ByteArray {
        val buffer=plane.buffer.duplicate().apply { rewind() }
        val target=if(current.size==buffer.remaining()) current else ByteArray(buffer.remaining())
        buffer.get(target);return target
    }

    private fun process(image: Image) {
        val model=segmenter ?: return
        val started=System.nanoTime()
        val turn=degrees
        val planes=image.planes
        y=copyPlane(planes[0],y);u=copyPlane(planes[1],u);v=copyPlane(planes[2],v)
        YuvToArgb.convertUpright(y,planes[0].rowStride,u,v,planes[1].rowStride,planes[1].pixelStride,width,height,turn,argb)
        val uw=MaskMath.uprightWidth(width,height,turn)
        val uh=MaskMath.uprightHeight(width,height,turn)
        if(bitmap.width!=uw || bitmap.height!=uh) bitmap=Bitmap.createBitmap(uw,uh,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(argb,0,uw,0,0,uw,uh)
        val ts=maxOf(image.timestamp/1_000_000,lastTs+1);lastTs=ts
        val result=model.segmentForVideo(BitmapImageBuilder(bitmap).build(),ts)
        val masks=result.confidenceMasks().orElse(null)
        try {
            maskCount=masks?.size ?: 0
            val mask=masks?.firstOrNull() ?: return
            val floats=ByteBufferExtractor.extract(mask).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val mw=mask.width
            val mh=mask.height
            if(mw<=0 || mh<=0 || floats.limit()<mw*mh) return
            val coverage=MaskMath.toCameraSpace(floats,mw,mh,width,height,turn,smoothed,.4f)
            val target=MaskMath.presenceTarget(coverage)
            presence+=(target-presence)*(if(target>presence) .5f else .15f)
            onMask(smoothed,width,height,presence)
        } finally { masks?.forEach { runCatching { it.close() } } }
        val ms=(System.nanoTime()-started)/1_000_000.0
        processedFrames++
        meanMs=if(processedFrames==1L) ms else meanMs*.9+ms*.1
    }

    fun info(): JSONObject = JSONObject().put("delegate","CPU").put("stream","${width}x$height").put("framesProcessed",processedFrames)
        .put("meanMs",meanMs).put("maskCount",maskCount).put("working",working).put("error",lastError ?: JSONObject.NULL)

    override fun close() {
        if(closed) return
        closed=true
        runCatching { reader.setOnImageAvailableListener(null,null) }
        val done=CountDownLatch(1)
        if(handler.post { runCatching { segmenter?.close() };segmenter=null;done.countDown() }) done.await(3,TimeUnit.SECONDS)
        runCatching { reader.close() }
        thread.quitSafely()
    }
}
