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
        val phasesBrief=report.optJSONArray("phases")?.let { a -> (0 until a.length()).joinToString(" ; ") { i -> val p=a.getJSONObject(i)
            "${p.optString("delegate")}: seen=${p.optInt("framesSeen")} processed=${p.optInt("framesProcessed")} init=${p.optDouble("initMs")} err=${p.opt("error")}" } } ?: "-"
        val brief="error=${report.opt("error")} | abi=${android.os.Build.SUPPORTED_ABIS.joinToString()} | small=${report.opt("smallStream")} main=${report.opt("mainStream")} cam=${report.opt("cameraFramesTotal")} | $phasesBrief"
        assertFalse(brief,report.has("error"))
        assertFalse(report.getBoolean("recording"))
        assertEquals(64,report.getString("modelSha256").length)
        val phases=report.getJSONArray("phases")
        val processed=(0 until phases.length()).sumOf { phases.getJSONObject(it).getInt("framesProcessed") }
        assertTrue("Tidak ada gambar diproses: $brief",processed>0)
        assertTrue(report.getString("summary").startsWith("Uji Bokeh selesai"))
        assertTrue(report.getJSONArray("thermal").length()>0)
    }
}
