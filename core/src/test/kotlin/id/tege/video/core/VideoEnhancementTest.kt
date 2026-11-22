package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class VideoEnhancementTest {
    @Test fun initialPresetAndRestore() {
        assertEquals(EnhancementPreset.SEIMBANG, EnhancementPreset.fromName(null))
        assertEquals(EnhancementPreset.RINGAN, EnhancementPreset.fromName("RINGAN"))
        assertEquals(EnhancementPreset.SEIMBANG, EnhancementPreset.fromName("unknown"))
    }

    @Test fun gpuProfilesAreConservative() {
        assertTrue(EnhancementPolicy.eligible(1920, 1080, 30, false))
        assertTrue(EnhancementPolicy.eligible(1280, 720, 30, false))
        assertFalse(EnhancementPolicy.eligible(3840, 2160, 30, false))
        assertFalse(EnhancementPolicy.eligible(1920, 1080, 60, false))
        assertFalse(EnhancementPolicy.eligible(1920, 1080, 30, true))
        assertFalse(EnhancementPolicy.eligible(0, 1080, 30, false))
    }

    @Test fun shaderParametersStayWithinSafeBounds() {
        EnhancementPreset.entries.forEach {
            assertTrue(it.denoise in 0f..1f)
            assertTrue(it.tone in 0f..1f)
            assertTrue(it.detail in 0f..1f)
        }
    }
}
