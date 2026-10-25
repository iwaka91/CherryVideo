package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class LensSwitchAnalysisTest {
    private fun obs(zoom: Float,id: String?,focal: Float?,frames: Int=6) = ZoomObservation(zoom,zoom,id,focal,frames)
    @Test fun sameLensEverywhereMeansCropOnly() {
        val v=LensSwitchAnalysis.analyze(listOf(obs(1f,"7",6.9f),obs(2f,"7",6.9f),obs(5f,"7",6.9f),obs(10f,"7",6.9f)))
        assertFalse(v.switched);assertNull(v.firstSwitchZoom)
    }
    @Test fun activePhysicalIdChangeIsASwitch() {
        val v=LensSwitchAnalysis.analyze(listOf(obs(1f,"7",6.9f),obs(2f,"7",6.9f),obs(3f,"2",14.2f)))
        assertTrue(v.switched);assertEquals(3f,v.firstSwitchZoom)
    }
    @Test fun focalLengthJumpWithoutIdIsASwitch() {
        val v=LensSwitchAnalysis.analyze(listOf(obs(1f,null,6.9f),obs(2f,null,6.9f),obs(2.5f,null,13.8f)))
        assertTrue(v.switched);assertEquals(2.5f,v.firstSwitchZoom)
    }
    @Test fun stepsWithoutFramesAreNotEvidence() {
        val v=LensSwitchAnalysis.analyze(listOf(obs(1f,"7",6.9f),obs(3f,"2",14.2f,frames=0)))
        assertFalse(v.switched)
    }
}
