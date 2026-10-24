package id.tege.video

import android.Manifest
import android.widget.Button
import android.widget.RadioButton
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import id.tege.video.camera.*
import id.tege.video.core.AudioMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AudioFeaturesTest {
    @get:Rule val permissions=GrantPermissionRule.grant(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun nativePresetsReduceStationaryNoiseAndDoNotCrossStereoChannels() {
        assertTrue("Native processor must load on the test ABI",AudioProcessor.available)
        val levels=ArrayList<Double>()
        for(mode in AudioMode.entries) {
            val random=Random(2026)
            val buffer=ByteBuffer.allocateDirect(480*2*2).order(ByteOrder.LITTLE_ENDIAN)
            var squares=0.0;var frames=0
            AudioProcessor(mode,2).use { processor ->
                repeat(250) { block ->
                    val original=ShortArray(480) { (random.nextGaussian()*350).toInt().toShort() }
                    repeat(480) { i -> buffer.putShort(i*4,original[i]);buffer.putShort(i*4+2,0) }
                    processor.process(buffer,480)
                    repeat(480) { i ->
                        val left=buffer.getShort(i*4);val right=buffer.getShort(i*4+2)
                        assertEquals("Stereo crosstalk",0,right.toInt())
                        if(mode==AudioMode.NATURAL) assertEquals(original[i],left)
                        if(block>=100) { squares+=left.toDouble()*left;frames++ }
                    }
                }
            }
            levels.add(10*log10((squares/frames).coerceAtLeast(1e-12)))
        }
        assertTrue("Clear must reduce steady noise: $levels",levels[0]-levels[1]>4)
        assertTrue("Strong must differ from Clear: $levels",levels[1]-levels[2]>4)
        File(context.getExternalFilesDir(null),"qualification").apply { mkdirs() }.let {
            File(it,"audio-presets.json").writeText(JSONObject().put("rmsDb",org.json.JSONArray(levels))
                .put("scope","Synthetic noise; physical speech/microphone quality not certified").toString(2))
        }
    }
    @Test fun missingExternalMicrophoneIsRejectedWithoutBuiltinFallback() {
        AudioRoute(context,true,Int.MAX_VALUE).use { route ->
            assertThrows(IllegalStateException::class.java) { route.prepare() }
        }
    }
    @Test fun audioPresetUiPersists() {
        context.getSharedPreferences("tege",0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity;scenario.onActivity { activity=it }
            fun await(message: String,predicate: ()->Boolean) {
                val end=System.currentTimeMillis()+25000
                while(System.currentTimeMillis()<end) { if(predicate()) return;Thread.sleep(100) }
                fail(message)
            }
            await("Camera unavailable") { activity.engine?.isReady==true }
            scenario.onActivity { it.window.decorView.findViewWithTag<Button>("audio").performClick() }
            val preset=UiDevice.getInstance(instrumentation).findObject(UiSelector().text("Suara Jernih"))
            if(!preset.exists() || preset.visibleBounds.isEmpty) {
                assertTrue(UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("Suara Jernih")))
            }
            assertTrue(preset.click())
            UiDevice.getInstance(instrumentation).findObject(UiSelector().resourceId("android:id/button2")).click()
            scenario.onActivity { assertTrue(it.window.decorView.findViewWithTag<Button>("audio").text.contains("Suara Jernih")) }
            assertEquals(1,context.getSharedPreferences("tege",0).getInt("audioMode",-1))
            val bmp=instrumentation.uiAutomation.takeScreenshot()
            bmp?.let { image ->
                val directory=File(context.getExternalFilesDir(null),"qualification").apply { mkdirs() }
                File(directory,"updated-controls.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                image.recycle()
            }
        }
    }
    @Test fun cameraReportsAppliedZoom() {
        context.getSharedPreferences("tege",0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity;scenario.onActivity { activity=it }
            fun await(message: String,predicate: ()->Boolean) {
                val end=System.currentTimeMillis()+25000
                while(System.currentTimeMillis()<end) { if(predicate()) return;Thread.sleep(100) }
                fail(message)
            }
            await("Camera unavailable") { activity.engine?.isReady==true }
            val entry=activity.engine!!.entry
            if(entry.zoomMax>1.1f) {
                val target=min(2f,entry.zoomMax)
                activity.engine!!.update(Controls(zoom=target))
                await("Camera must report a zoomed capture result") { activity.engine?.reportedZoom?.let { abs(it-target)<.1f }==true }
                activity.engine!!.update(Controls(zoom=1f))
            } else scenario.onActivity { assertFalse(it.window.decorView.findViewWithTag<Button>("zoom").isEnabled) }
        }
    }

}
