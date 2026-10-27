package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class BokehTuningTest {
    @Test fun defaultIsSixPercent() {
        assertEquals(.06f,BokehTuning().strength,0f)
        assertEquals(.06f,BokehPreset.POTRET.strength,0f)
    }
    @Test fun fValueRunsFromF16ToF1() {
        assertEquals(16f,BokehLook.fNumber(0f),1e-4f)
        assertEquals(1f,BokehLook.fNumber(1f),1e-4f)
        assertEquals(4f,BokehLook.fNumber(.5f),1e-3f)
        assertEquals("F16",BokehLook.fLabel(0f))
        assertEquals("F1.0",BokehLook.fLabel(1f))
        assertTrue(BokehLook.fNumber(.2f)>BokehLook.fNumber(.3f))
    }
    @Test fun sensitivityMovesThreshold() {
        assertEquals(.5f,BokehLook.threshold(.5f),1e-6f)
        assertTrue(BokehLook.threshold(1f)<BokehLook.threshold(0f))
        assertEquals(.75f,BokehLook.threshold(0f),1e-6f)
        assertEquals(.25f,BokehLook.threshold(1f),1e-6f)
    }
    @Test fun presetsChangeLookButKeepSensitivityAndMaskView() {
        val base=BokehTuning(sensitivity=.3f,showMask=true)
        val p=BokehPreset.SINEMATIK.apply(base)
        assertEquals(.3f,p.sensitivity,0f);assertTrue(p.showMask)
        assertEquals(BokehPreset.SINEMATIK.strength,p.strength,0f)
        assertEquals(BokehPreset.LEMBUT,BokehPreset.of("LEMBUT"));assertNull(BokehPreset.of(""))
    }
    @Test fun smoothKeepsMoreOfOldMask() { assertTrue(BokehLook.keep(true)>BokehLook.keep(false)) }
}
