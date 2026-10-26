package id.tege.video

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import id.tege.video.camera.BokehProbe
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Emulator check only: the model loads offline, frames reach the segmenter, nothing is recorded. Speed is proven on the phone. */
@RunWith(AndroidJUnit4::class)
class BokehProbeTest {
    @get:Rule val permissions: GrantPermissionRule=GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun segmentationProbeRunsOfflineWithoutRecording() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val report=BokehProbe.run(context,gpuMs=2500,cpuMs=2500)
        File(context.getExternalFilesDir(null),"qualification").apply { mkdirs() }.resolve("bokeh-probe.json").writeText(report.toString(2))
        assertFalse(report.toString(2),report.has("error"))
        assertFalse(report.getBoolean("recording"))
        assertEquals(64,report.getString("modelSha256").length)
        val phases=report.getJSONArray("phases")
        val processed=(0 until phases.length()).sumOf { phases.getJSONObject(it).getInt("framesProcessed") }
        assertTrue("Tidak ada gambar diproses: ${report.toString(2)}",processed>0)
        assertTrue(report.getString("summary").startsWith("Uji Bokeh selesai"))
        assertTrue(report.getJSONArray("thermal").length()>0)
    }
}
