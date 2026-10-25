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
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaCodec
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import id.tege.video.camera.VideoFiles
import id.tege.video.camera.VideoRenderer
import id.tege.video.camera.TextureGeometry
import id.tege.video.camera.SurfaceRecorder
import id.tege.video.core.AudioMode
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
        context.filesDir.listFiles()?.filter { it.name.startsWith("pending-") && it.extension=="mp4" }?.forEach { it.copyTo(File(evidence,it.name),overwrite=true) }
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
            scenario.onActivity {
                assertFalse("Simpel must start active",it.window.decorView.findViewWithTag<android.widget.Switch>("mode-pro").isChecked)
                assertEquals("PRO badge must be hidden in Simpel",View.GONE,it.window.decorView.findViewWithTag<View>("pro-badge").visibility)
            }
            tap(activity,"more")
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<android.widget.Switch>("mode-pro").performClick() }
            scenario.onActivity {
                val proRow=it.window.decorView.findViewWithTag<Button>("focus").parent.parent as View
                assertEquals(View.VISIBLE,proRow.visibility)
                assertTrue("Pro switch must be on",it.window.decorView.findViewWithTag<android.widget.Switch>("mode-pro").isChecked)
                assertEquals("PRO badge must show in Pro",View.VISIBLE,it.window.decorView.findViewWithTag<View>("pro-badge").visibility)
                assertEquals(View.VISIBLE,it.window.decorView.findViewWithTag<View>("control-host").let { host -> (host as android.view.ViewGroup).getChildAt(0).visibility })
            }
            assertTrue("Pro mode must persist",context.getSharedPreferences("tege",0).getBoolean("pro",false))
            screenshot("pro-portrait.png")
            tap(activity,"more")
            tap(activity,"record")
            await("Recorder did not start") { activity.engine?.isRecording==true }
            screenshot("recording.png")
            // Screenshot capture can stall a software-rendered emulator. Measure a
            // normal take after that diagnostic work, with a recent submitted frame.
            Thread.sleep(3500)
            assertTrue("AudioRecord must deliver PCM frames",(activity.engine?.capturedAudioFrames ?: 0L)>24_000L)
            await("Camera timestamp did not catch up after screenshot",10000) {
                val stamp=activity.engine?.lastEncodedTimestampNs ?: 0L
                stamp>0 && System.nanoTime()-stamp in 0L..250_000_000L
            }
            tap(activity,"record")
            await("No finalized camera video: ${activity.engine?.lastError}") { activity.lastSaved!=null }
            val saved=activity.lastSaved!!
            assertTrue(saved.report.getInt("frames")>=12)
            assertTrue(saved.report.getBoolean("audio"))
            assertTrue(saved.report.getLong("videoStartUs")<500_000L)
            assertTrue(saved.report.getLong("containerDurationMs")<10_000L)
            assertEquals(90,saved.report.getInt("rotation"))
            context.contentResolver.openInputStream(saved.uri)!!.use { input -> File(evidence,"camera-emulator.mp4").outputStream().use { input.copyTo(it) } }
            File(evidence,"camera-emulator.json").writeText(saved.report.toString(2))
            verifyBadTimelineIsRejected(File(evidence,"camera-emulator.mp4"))
            scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(1500)
            await("Landscape camera did not reopen") { activity.engine?.isReady==true }
            scenario.onActivity {
                val button=it.window.decorView.findViewWithTag<Button>("record")
                val bounds=android.graphics.Rect()
                assertTrue("Landscape record button must remain fully visible",button.getGlobalVisibleRect(bounds) && bounds.height()==button.height)
            }
            screenshot("pro-landscape.png")
            tap(activity,"audio")
            val preset=UiDevice.getInstance(instrumentation).findObject(UiSelector().text("Reduksi Kuat"))
            if(!preset.exists() || preset.visibleBounds.isEmpty) {
                assertTrue(UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Reduksi Kuat")))
            }
            assertTrue(preset.click())
            UiDevice.getInstance(instrumentation).findObject(UiSelector().resourceId("android:id/button2")).click()
            tap(activity,"record")
            await("Landscape recorder did not start") { activity.engine?.isRecording==true }
            Thread.sleep(2200)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            await("Backgrounding did not save the current take") { activity.lastSaved?.name!=saved.name }
            val background=activity.lastSaved!!
            assertEquals("STRONG",background.report.getJSONObject("recorderTiming").getString("audioMode"))
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
                scenario.onActivity {
                    val preview=it.window.decorView.findViewWithTag<View>("viewfinder")
                    val dock=it.window.decorView.findViewWithTag<View>("control-host")
                    val a=android.graphics.Rect();val b=android.graphics.Rect()
                    assertTrue(preview.getGlobalVisibleRect(a));assertTrue(dock.getGlobalVisibleRect(b))
                    assertTrue("LUT controls must sit below the visible preview",a.bottom<=b.top && a.height()>150)
                }
                tap(activity,"import-lut")
                await("Imported LUT was not persisted") { context.getSharedPreferences("tege",0).getString("lut",null)==CubeLut.parse(bytes).sha256 }
                assertTrue(monitor.hits>=1)
                screenshot("lut-import.png")
                val old=context.getSharedPreferences("tege",0).getString("lut",null)
                resolver.openOutputStream(uri,"w")!!.use { it.write("LUT_3D_SIZE 33\n0 0 0".toByteArray()) }
                tap(activity,"import-lut")
                await("Malformed LUT did not show an import error") {
                    var shown=false
                    instrumentation.runOnMainSync { shown=activity.window.decorView.findViewWithTag<android.widget.TextView>("status").text.toString().startsWith("Impor LUT ditolak") }
                    shown
                }
                assertEquals(old,context.getSharedPreferences("tege",0).getString("lut",null))
            }
        } finally { instrumentation.removeMonitor(monitor);resolver.delete(uri,null,null) }
    }
    private fun cubeSwap(size: Int=2)=buildString {
        append("TITLE \"Red to green test\"\nLUT_3D_SIZE $size\n")
        for(b in 0 until size) for(g in 0 until size) for(r in 0 until size)
            append("${g.toFloat()/(size-1)} ${r.toFloat()/(size-1)} ${b.toFloat()/(size-1)}\n")
    }.toByteArray()
    @Test fun gpuLutBakesIntoEncodedMp4() { verifyGpu(baked=true) }
    @Test fun monitorOnlyLutKeepsOriginalEncodedColor() { verifyGpu(baked=false) }
    @Test fun delayedVideoStartupStillProducesPlayableSynchronizedFiles() {
        val available=android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
        for(hevc in listOf(false,true)) {
            val mime=if(hevc) "video/hevc" else "video/avc"
            if(available.none { it.isEncoder && it.supportedTypes.contains(mime) }) continue
            for(audio in listOf(0,1)) {
                val reader=ImageReader.newInstance(320,240,PixelFormat.RGBA_8888,4)
                reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() },Handler(Looper.getMainLooper()))
                var failure: String?=null
                val renderer=VideoRenderer(reader.surface,320,240) { failure=it }
                val file=File(evidence,"startup-${if(hevc) "hevc" else "avc"}-${if(audio==0) "silent" else "audio"}.mp4")
                val recorder=SurfaceRecorder(context,file,VideoProfile(320,240,30),hevc,audio,0,100L*1024*1024,
                    onLimit={ failure="Unexpected test file limit" },onFailure={ failure=it },
                    audioMode=if(hevc) AudioMode.STRONG else AudioMode.CLEAR)
                try {
                    renderer.resize(320,240,0,false)
                    recorder.prepare();recorder.start()
                    // Reproduce >500 ms audio/video startup skew, not a relaxed validator.
                    Thread.sleep(900)
                    renderer.attachEncoder(recorder.surface,recorder.epochNs)
                    repeat(55) { index ->
                        val canvas=renderer.input.lockCanvas(null)
                        canvas.drawColor(if(index<27) Color.RED else Color.BLUE)
                        renderer.input.unlockCanvasAndPost(canvas)
                        Thread.sleep(34)
                    }
                    renderer.pauseEncoder();recorder.stop();renderer.detachEncoder()
                    assertNull(failure)
                    val report=VideoFiles.inspect(file,VideoProfile(320,240,30),audio!=0,0)
                        .put("recorderTiming",recorder.timingReport)
                    assertTrue("Startup must be trimmed, not published as a leading gap",report.getLong("videoStartUs")<100_000L)
                    assertTrue(report.getInt("frames")>=12)
                    assertTrue(recorder.timingReport.getLong("startupTrimUs")>=500_000L)
                    if(audio!=0) assertTrue(kotlin.math.abs(report.getLong("audioStartUs")-report.getLong("videoStartUs"))<150_000L)
                    File(evidence,file.nameWithoutExtension+".json").writeText(report.toString(2))
                    val metadata=MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(file.absolutePath)
                        val bitmap=metadata.getFrameAtTime(300_000,MediaMetadataRetriever.OPTION_CLOSEST)
                        assertNotNull("Finalized MP4 must decode",bitmap);bitmap?.recycle()
                    } finally { metadata.release() }
                } finally { recorder.release();renderer.close();reader.close() }
            }
        }
    }
    @Test fun gpuLutCornersAndHighlightsMatchCpuReference() {
        val reader=ImageReader.newInstance(320,240,PixelFormat.RGBA_8888,4)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() },Handler(Looper.getMainLooper()))
        var failure: String?=null
        val renderer=VideoRenderer(reader.surface,320,240) { failure=it }
        try {
            renderer.resize(320,240,0,false)
            val results=org.json.JSONArray();val errors=mutableListOf<String>()
            for(size in listOf(2,17,33)) {
              val lut=CubeLut.parse(cubeSwap(size));renderer.grade(lut,1f,true,0)
              for(color in listOf(Color.RED,Color.GREEN,Color.BLUE,Color.CYAN,Color.MAGENTA,Color.YELLOW,Color.WHITE,Color.BLACK,Color.GRAY,Color.rgb(36,73,166))) {
                val canvas=renderer.input.lockCanvas(null);canvas.drawColor(color);renderer.input.unlockCanvasAndPost(canvas)
                Thread.sleep(200)
                val expected=lut.sample(Color.red(color)/255f,Color.green(color)/255f,Color.blue(color)/255f)
                val actual=renderer.centerPixel()
                results.put(org.json.JSONObject().put("size",size).put("input",Integer.toHexString(color)).put("actual",org.json.JSONArray(actual.toList())).put("expected",org.json.JSONArray(expected.map { it*255 })))
                for(c in 0..2) if(kotlin.math.abs(expected[c]*255-actual[c])>20f) errors.add("Size $size input ${Integer.toHexString(color)} channel $c GPU ${actual.toList()}")
              }
            }
            File(evidence,"lut-gpu-colors.json").writeText(results.toString(2))
            assertTrue(errors.joinToString("; "),errors.isEmpty())
            assertNull(failure)
        } finally { renderer.close();reader.close() }
    }
    @Test fun cameraTextureRotationAndMirroringAreAppliedOnce() {
        for(sensor in listOf(90,270)) for(producerMirror in listOf(false,true)) for(rotation in listOf(0,90,180,270)) for(front in listOf(false,true)) {
            val producer=TextureGeometry.cameraTransform(sensor,producerMirror)
            val actual=TextureGeometry.sampling(producer,sensor,producerMirror,rotation,front)
            assertArrayEquals("sensor=$sensor rotation=$rotation front=$front",TextureGeometry.cameraTransform(rotation,front),actual,.0001f)
            val encoded=TextureGeometry.sampling(producer,sensor,producerMirror,0,false)
            assertArrayEquals(TextureGeometry.cameraTransform(0,false),encoded,.0001f)
        }
        assertArrayEquals(intArrayOf(30,0,180,320),TextureGeometry.viewport(240,320,1920,1080,90))
        assertArrayEquals(intArrayOf(0,30,320,180),TextureGeometry.viewport(320,240,1920,1080,0))
    }
    @Test fun gpuPreviewQuadrantsStayUprightAcrossDeviceRotations() {
        val reader=ImageReader.newInstance(400,400,PixelFormat.RGBA_8888,4)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() },Handler(Looper.getMainLooper()))
        var failure: String?=null
        val renderer=VideoRenderer(reader.surface,320,240) { failure=it }
        try {
            val canvas=renderer.input.lockCanvas(null)
            val paint=android.graphics.Paint()
            listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW).forEachIndexed { i,c ->
                paint.color=c;val x=(i%2)*160f;val y=(i/2)*120f;canvas.drawRect(x,y,x+160f,y+120f,paint)
            }
            renderer.input.unlockCanvasAndPost(canvas);Thread.sleep(700)
            val expected=listOf(
                listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW),
                listOf(Color.BLUE,Color.RED,Color.YELLOW,Color.GREEN),
                listOf(Color.YELLOW,Color.BLUE,Color.GREEN,Color.RED),
                listOf(Color.GREEN,Color.YELLOW,Color.RED,Color.BLUE))
            for(turn in 0..3) for(front in listOf(false,true)) {
                renderer.resize(400,400,turn*90,front)
                val colors=expected[turn].let { if(front) listOf(it[1],it[0],it[3],it[2]) else it }
                listOf(.3f to .25f,.7f to .25f,.3f to .75f,.7f to .75f).forEachIndexed { i,(x,y) ->
                    val pixel=renderer.previewPixel(x,y);val c=colors[i]
                    assertTrue("rotation=${turn*90} front=$front quadrant=$i pixel=${pixel.toList()}",
                        kotlin.math.abs(pixel[0]-Color.red(c))<50 && kotlin.math.abs(pixel[1]-Color.green(c))<50 && kotlin.math.abs(pixel[2]-Color.blue(c))<50)
                }
            }
            assertNull(failure)
        } finally { renderer.close();reader.close() }
    }
    @Test fun selectedValuesAndReadableAbout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity;scenario.onActivity { activity=it }
            await("Camera not ready") { activity.engine?.isReady==true }
            tap(activity,"colors");tap(activity,"look-2")
            scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<android.widget.TextView>("lut-active").text.contains("Teal")) }
            screenshot("lut-dock.png");tap(activity,"close-lut")
            scenario.onActivity {
                assertTrue(it.window.decorView.findViewWithTag<Button>("colors").text.contains("Teal"))
                assertTrue(it.window.decorView.findViewWithTag<Button>("audio").text.contains("Kamera"))
            }
            tap(activity,"stabilization")
            UiDevice.getInstance(instrumentation).findObject(UiSelector().text("OFF")).click()
            scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<Button>("stabilization").text.contains("OFF")) }
            tap(activity,"settings")
            UiDevice.getInstance(instrumentation).findObject(UiSelector().text("Tentang Cherry Video")).click()
            assertTrue(UiDevice.getInstance(instrumentation).findObject(UiSelector().text("Cherry Video")).exists())
            assertFalse(UiDevice.getInstance(instrumentation).findObject(UiSelector().textContains("TERMS AND CONDITIONS")).exists())
            screenshot("about.png")
        }
    }
    private fun pinch(scenario: ActivityScenario<MainActivity>,spread: Boolean) {
        var cx=0f;var cy=0f
        lateinit var decor: View
        scenario.onActivity {
            decor=it.window.decorView
            val preview=decor.findViewWithTag<View>("viewfinder");val at=IntArray(2);preview.getLocationInWindow(at)
            cx=at[0]+preview.width/2f;cy=at[1]+preview.height/2f
        }
        val from=if(spread) 40f else 220f;val to=if(spread) 220f else 40f
        val downTime=SystemClock.uptimeMillis()
        fun send(action: Int,gap: Float,pointers: Int) {
            val properties=Array(pointers) { i -> MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }
            val coords=Array(pointers) { i -> MotionEvent.PointerCoords().apply { x=cx+if(i==0) -gap else gap;y=cy;pressure=1f;size=1f } }
            val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,pointers,properties,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            // Dispatch from the window root so the real parent/child touch-target logic is exercised.
            instrumentation.runOnMainSync { decor.dispatchTouchEvent(event);event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN,from,1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),from,2)
        for(step in 1..14) { send(MotionEvent.ACTION_MOVE,from+(to-from)*step/14f,2);Thread.sleep(16) }
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),to,2)
        send(MotionEvent.ACTION_UP,to,1)
    }
    @Test fun twoFingerPinchZoomsThroughRealTouchDispatchAndKeepsRecording() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity;scenario.onActivity { activity=it }
            await("Camera not ready: ${activity.engine?.lastError}") { activity.engine?.isReady==true }
            org.junit.Assume.assumeTrue("Emulated camera exposes no usable zoom range",activity.engine!!.entry.zoomMax>1.4f && !activity.engine!!.profile.highSpeed)
            val start=activity.engine?.reportedZoom ?: 1f
            pinch(scenario,true)
            await("Spreading two fingers must zoom in",15000) { (activity.engine?.reportedZoom ?: 1f)>start+.15f }
            val zoomedIn=activity.engine?.reportedZoom ?: 1f
            pinch(scenario,false)
            await("Pinching two fingers together must zoom out",15000) { (activity.engine?.reportedZoom ?: 99f)<zoomedIn-.15f }
            tap(activity,"record")
            await("Recorder did not start") { activity.engine?.isRecording==true }
            val before=activity.engine?.reportedZoom ?: 1f
            pinch(scenario,true)
            await("Zoom while recording must still change",15000) { (activity.engine?.reportedZoom ?: 1f)>before+.1f }
            assertTrue("Zoom must not stop the recording",activity.engine?.isRecording==true)
            tap(activity,"record")
            await("No finalized video after zooming while recording") { activity.lastSaved!=null }
        }
    }
    @Test fun flipButtonSwitchesBetweenBackAndFrontCamera() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity;scenario.onActivity { activity=it }
            await("Camera not ready: ${activity.engine?.lastError}") { activity.engine?.isReady==true }
            var flippable=false
            scenario.onActivity { flippable=it.window.decorView.findViewWithTag<Button>("lens").isEnabled }
            org.junit.Assume.assumeTrue("Needs a front and a back camera",flippable)
            assertFalse("Starts on the back camera",activity.engine!!.entry.front)
            tap(activity,"lens")
            await("Front camera did not open: ${activity.engine?.lastError}") { activity.engine?.isReady==true && activity.engine?.entry?.front==true }
            assertEquals("front",context.getSharedPreferences("tege",0).getString("facing",null))
            scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<Button>("lens").text.startsWith("Depan")) }
            tap(activity,"lens")
            await("Back camera did not reopen: ${activity.engine?.lastError}") { activity.engine?.isReady==true && activity.engine?.entry?.front==false }
            assertEquals("back",context.getSharedPreferences("tege",0).getString("facing",null))
        }
    }
    @Test fun cinematicLooksCompileOnGpuAndChangeColors() {
        val reader=ImageReader.newInstance(320,240,PixelFormat.RGBA_8888,4)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() },Handler(Looper.getMainLooper()))
        var failure: String?=null
        val renderer=VideoRenderer(reader.surface,320,240) { failure=it }
        try {
            renderer.resize(320,240,0,false)
            val pixels=HashMap<Int,IntArray>()
            for(look in listOf(0,4,5)) {
                renderer.grade(null,1f,false,look)
                val canvas=renderer.input.lockCanvas(null);canvas.drawColor(Color.rgb(200,120,60));renderer.input.unlockCanvasAndPost(canvas)
                Thread.sleep(350)
                pixels[look]=renderer.centerPixel()
            }
            assertNull("GL renderer failure",failure)
            val natural=pixels.getValue(0)
            for(look in listOf(4,5)) {
                val graded=pixels.getValue(look)
                val difference=(0..2).sumOf { kotlin.math.abs(graded[it]-natural[it]) }
                assertTrue("Cinematic look $look must change the colors: ${graded.toList()} vs ${natural.toList()}",difference>12)
            }
        } finally { renderer.close();reader.close() }
    }
    private fun verifyBadTimelineIsRejected(original: File) {
        val bad=File(context.cacheDir,"bad-clock.mp4")
        val extractor=MediaExtractor();val muxer=MediaMuxer(bad.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(original.absolutePath)
            val videoIndices=HashSet<Int>();val tracks=HashMap<Int,Int>()
            for(i in 0 until extractor.trackCount) {
                val f=extractor.getTrackFormat(i);tracks[i]=muxer.addTrack(f);extractor.selectTrack(i)
                if(f.getString("mime")!!.startsWith("video/")) videoIndices.add(i)
            }
            muxer.start()
            val buffer=java.nio.ByteBuffer.allocateDirect(4*1024*1024);val info=MediaCodec.BufferInfo()
            while(extractor.sampleTrackIndex>=0) {
                val index=extractor.sampleTrackIndex;buffer.clear()
                val size=extractor.readSampleData(buffer,0);if(size<0) break
                info.set(0,size,extractor.sampleTime+if(index in videoIndices) 15_070_000_000L else 0L,extractor.sampleFlags)
                muxer.writeSampleData(tracks.getValue(index),buffer,info);if(!extractor.advance()) break
            }
            muxer.stop()
        } finally { extractor.release();muxer.release() }
        assertThrows(IllegalStateException::class.java) { VideoFiles.inspect(bad,VideoProfile(1280,720,30),true) }
        bad.delete()
    }
    @Suppress("DEPRECATION")
    private fun verifyGpu(baked: Boolean) {
        val reader=ImageReader.newInstance(320,240,PixelFormat.RGBA_8888,4)
        reader.setOnImageAvailableListener({ imageReader -> imageReader.acquireLatestImage()?.close() },Handler(Looper.getMainLooper()))
        var failure: String?=null
        val renderer=VideoRenderer(reader.surface,320,240) { failure=it }
        val recorder=MediaRecorder()
        val file=File(evidence,if(baked) "lut-baked.mp4" else "lut-monitor-only.mp4")
        val direct=if(baked) SurfaceRecorder(context,file,VideoProfile(320,240,30),false,0,0,100L*1024*1024,
            onLimit={ failure="Unexpected test file limit" },onFailure={ failure=it }) else null
        try {
            renderer.resize(320,240,0,false)
            renderer.grade(CubeLut.parse(cubeSwap()),1f,baked,0)
            val surface=if(direct!=null) {
                direct.prepare();direct.start();direct.surface
            } else {
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                recorder.setVideoSize(320,240);recorder.setVideoFrameRate(30);recorder.setVideoEncodingBitRate(2_000_000)
                recorder.setOutputFile(file.absolutePath);recorder.prepare();recorder.start();recorder.surface
            }
            renderer.attachEncoder(surface,direct?.epochNs ?: 0L)
            repeat(70) { index ->
                val canvas=renderer.input.lockCanvas(null);canvas.drawColor(Color.RED)
                val paint=android.graphics.Paint().apply { color=Color.WHITE }
                val x=if(index<35) 20f else 250f
                canvas.drawRect(x,180f,x+35f,230f,paint)
                renderer.input.unlockCanvasAndPost(canvas)
                Thread.sleep(34)
            }
            val pixel=renderer.centerPixel()
            assertNull("GL renderer failure",failure)
            assertTrue("Preview LUT must turn red into green: ${pixel.toList()}",pixel[1]>180 && pixel[0]<60)
            val highlight=renderer.previewPixel(.84f,.86f)
            assertTrue("White highlight must remain white: ${highlight.toList()}",highlight.take(3).all { it>180 })
            renderer.pauseEncoder();if(direct!=null) direct.stop() else recorder.stop();renderer.detachEncoder();surface.release()
            val report=VideoFiles.inspect(file,VideoProfile(320,240,30),false)
            assertTrue(report.getInt("frames")>=20)
            File(evidence,file.nameWithoutExtension+".json").writeText(report.toString(2))
            val retriever=MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val bitmap=retriever.getFrameAtTime(500000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                val later=retriever.getFrameAtTime((report.getLong("containerDurationMs")-250)*1000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                var changed=0
                for(y in 3*bitmap.height/4 until bitmap.height step 4) for(x in 0 until bitmap.width step 4) {
                    val a=bitmap.getPixel(x,y);val b=later.getPixel(x*later.width/bitmap.width,y*later.height/bitmap.height)
                    if(kotlin.math.abs(Color.red(a)-Color.red(b))+kotlin.math.abs(Color.green(a)-Color.green(b))+kotlin.math.abs(Color.blue(a)-Color.blue(b))>120) changed++
                }
                assertTrue("Playback must show motion at distinct presentation times",changed>15)
                later.recycle()
                val center=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
                if(baked) assertTrue("Encoded LUT is missing",Color.green(center)>150 && Color.red(center)<70)
                else assertTrue("Monitor-only altered encoded source",Color.red(center)>150 && Color.green(center)<70)
                bitmap.recycle()
            } finally { retriever.release() }
        } finally { runCatching { direct?.release() };runCatching { recorder.release() };renderer.close();reader.close() }
    }
}
