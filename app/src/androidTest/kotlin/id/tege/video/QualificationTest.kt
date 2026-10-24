package id.tege.video

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import id.tege.video.camera.VideoFiles
import id.tege.video.camera.VideoRenderer
import id.tege.video.core.CubeLut
import id.tege.video.core.VideoProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QualificationTest {
    @get:Rule val permissions: GrantPermissionRule=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val evidence get()=File(context.getExternalFilesDir(null),"qualification").apply { mkdirs() }
    @Before fun resetPreferences() { context.getSharedPreferences("tege",0).edit().clear().commit() }
    private fun await(message: String,timeout: Long=25000,predicate: ()->Boolean) {
        val deadline=System.currentTimeMillis()+timeout
        while(System.currentTimeMillis()<deadline) { if(predicate()) return;Thread.sleep(100) }
        screenshot("test-failure.png");fail(message)
    }
    private fun tap(activity: MainActivity,tag: String) {
        instrumentation.runOnMainSync { assertTrue("Button $tag",activity.window.decorView.findViewWithTag<Button>(tag).performClick()) }
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.apply {
            File(evidence,name).outputStream().use { compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };recycle()
        }
    }
    @Test fun cameraRecordingAndSimpleProNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity=it }
            await("Camera not ready: ${activity.engine?.lastError}") { activity.engine?.isReady==true }
            screenshot("simple-portrait.png")
            tap(activity,"mode")
            scenario.onActivity { assertEquals(View.VISIBLE,it.window.decorView.findViewWithTag<Button>("focus").parent.let { parent -> (parent as View).visibility }) }
            screenshot("pro-portrait.png")
            tap(activity,"record")
            await("Recorder did not start") { activity.engine?.isRecording==true }
            Thread.sleep(3500)
            screenshot("recording.png")
            tap(activity,"record")
            await("No finalized camera video: ${activity.engine?.lastError}") { activity.lastSaved!=null }
            val saved=activity.lastSaved!!
            assertTrue(saved.report.getInt("frames")>=12)
            assertTrue(saved.report.getBoolean("audio"))
            context.contentResolver.openInputStream(saved.uri)!!.use { input -> File(evidence,"camera-emulator.mp4").outputStream().use { input.copyTo(it) } }
            File(evidence,"camera-emulator.json").writeText(saved.report.toString(2))
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(1500)
            await("Landscape camera did not reopen") { activity.engine?.isReady==true }
            screenshot("pro-landscape.png")
            tap(activity,"record")
            await("Landscape recorder did not start") { activity.engine?.isRecording==true }
            Thread.sleep(2200)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            await("Backgrounding did not save the current take") { activity.lastSaved?.name!=saved.name }
            val background=activity.lastSaved!!
            context.contentResolver.openInputStream(background.uri)!!.use { input -> File(evidence,"camera-background.mp4").outputStream().use { input.copyTo(it) } }
            File(evidence,"camera-background.json").writeText(background.report.toString(2))
        }
    }
    @Test fun cubeImporterUsesPickerAndRejectsMalformedFile() {
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME,"TeGe-test.cube");put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");put(MediaStore.Downloads.IS_PENDING,1)
        })!!
        val bytes=cubeSwap()
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null)
        val filter=IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);addDataType("*/*") }
        val monitor=instrumentation.addMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri)),true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity;scenario.onActivity { activity=it }
                await("Camera not ready") { activity.engine?.isReady==true }
                tap(activity,"colors")
                UiDevice.getInstance(instrumentation).findObject(UiSelector().text("＋ Impor LUT (.cube)")).click()
                await("Imported LUT was not persisted") { context.getSharedPreferences("tege",0).getString("lut",null)==CubeLut.parse(bytes).sha256 }
                assertTrue(monitor.hits>=1)
                screenshot("lut-import.png")
                val old=context.getSharedPreferences("tege",0).getString("lut",null)
                resolver.openOutputStream(uri,"w")!!.use { it.write("LUT_3D_SIZE 33\n0 0 0".toByteArray()) }
                UiDevice.getInstance(instrumentation).findObject(UiSelector().text("＋ Impor LUT (.cube)")).click()
                await("Malformed LUT did not show an import error") {
                    activity.window.decorView.findViewWithTag<android.widget.TextView>("status").text.toString().startsWith("Impor LUT ditolak")
                }
                assertEquals(old,context.getSharedPreferences("tege",0).getString("lut",null))
            }
        } finally { instrumentation.removeMonitor(monitor);resolver.delete(uri,null,null) }
    }
    private fun cubeSwap()="""TITLE "Red to green test"
        |LUT_3D_SIZE 2
        |0 0 0
        |0 1 0
        |1 0 0
        |1 1 0
        |0 0 1
        |0 1 1
        |1 0 1
        |1 1 1
        |""".trimMargin().toByteArray()
    @Test fun gpuLutBakesIntoEncodedMp4() { verifyGpu(baked=true) }
    @Test fun monitorOnlyLutKeepsOriginalEncodedColor() { verifyGpu(baked=false) }
    @Suppress("DEPRECATION")
    private fun verifyGpu(baked: Boolean) {
        val reader=ImageReader.newInstance(320,240,PixelFormat.RGBA_8888,4)
        reader.setOnImageAvailableListener({ imageReader -> imageReader.acquireLatestImage()?.close() },Handler(Looper.getMainLooper()))
        var failure: String?=null
        val renderer=VideoRenderer(reader.surface,320,240) { failure=it }
        val recorder=MediaRecorder()
        val file=File(evidence,if(baked) "lut-baked.mp4" else "lut-monitor-only.mp4")
        try {
            renderer.resize(320,240,0,false)
            renderer.grade(CubeLut.parse(cubeSwap()),1f,baked,0)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(320,240);recorder.setVideoFrameRate(30);recorder.setVideoEncodingBitRate(2_000_000)
            recorder.setOutputFile(file.absolutePath);recorder.prepare();recorder.start()
            val surface=recorder.surface
            renderer.attachEncoder(surface)
            repeat(55) {
                val canvas=renderer.input.lockCanvas(null);canvas.drawColor(Color.RED);renderer.input.unlockCanvasAndPost(canvas)
                Thread.sleep(34)
            }
            val pixel=renderer.centerPixel()
            assertNull("GL renderer failure",failure)
            assertTrue("Preview LUT must turn red into green: ${pixel.toList()}",pixel[1]>180 && pixel[0]<60)
            renderer.detachEncoder();recorder.stop();surface.release()
            val report=VideoFiles.inspect(file,VideoProfile(320,240,30),false)
            assertTrue(report.getInt("frames")>=20)
            File(evidence,file.nameWithoutExtension+".json").writeText(report.toString(2))
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val bitmap=retriever.getFrameAtTime(500000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                val center=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
                if(baked) assertTrue("Encoded LUT is missing",Color.green(center)>150 && Color.red(center)<70)
                else assertTrue("Monitor-only altered encoded source",Color.red(center)>150 && Color.green(center)<70)
                bitmap.recycle()
            } finally { retriever.release() }
        } finally { runCatching { recorder.release() };renderer.close();reader.close() }
    }
}
