package id.tege.video.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualCameraCheckTest {
    private fun back(id: String = "0", w: Int = 1920, h: Int = 1080) = ConcurrentCam(id, CamFacing.BACK, w, h)
    private fun front(id: String = "1", w: Int = 1920, h: Int = 1080) = ConcurrentCam(id, CamFacing.FRONT, w, h)

    @Test fun oldAndroidIsNotSupported() {
        assertEquals(DualState.NOT_SUPPORTED, DualCameraCheck.evaluate(false, true, listOf(listOf(back(), front()))).state)
    }

    @Test fun noGroupsAndNoFlagIsNotSupported() {
        assertEquals(DualState.NOT_SUPPORTED, DualCameraCheck.evaluate(true, false, emptyList()).state)
    }

    @Test fun groupsWithoutFrontAndBackTogetherAreNotSupported() {
        val r = DualCameraCheck.evaluate(true, true, listOf(listOf(back("0"), back("2"))))
        assertEquals(DualState.NOT_SUPPORTED, r.state)
        assertTrue(r.lines.first().contains("1 kelompok"))
    }

    @Test fun frontPlusBackAtFullHdIsSupported() {
        val r = DualCameraCheck.evaluate(true, true, listOf(listOf(back(), front())))
        assertEquals(DualState.SUPPORTED, r.state)
        assertTrue(DualCameraCheck.text(r).contains("depan 1920x1080, belakang 1920x1080"))
        assertTrue(r.lines.any { it.contains("Jumlah pasangan depan + belakang: 1") })
    }

    @Test fun smallSharedSizeIsLimited() {
        val r = DualCameraCheck.evaluate(true, true, listOf(listOf(back(w = 1280, h = 720).copy(maxH = 480, maxW = 640), front())))
        assertEquals(DualState.LIMITED, r.state)
    }

    @Test fun unknownSizeIsLimitedNotSupportedBlindly() {
        val r = DualCameraCheck.evaluate(true, true, listOf(listOf(back(w = 0, h = 0), front(w = 0, h = 0))))
        assertEquals(DualState.LIMITED, r.state)
        assertTrue(DualCameraCheck.text(r).contains("tidak dilaporkan"))
    }

    @Test fun pairsAreCountedAndIdsListedOnce() {
        val r = DualCameraCheck.evaluate(true, true, listOf(listOf(back("0"), front("1")), listOf(back("2"), front("1"))))
        assertTrue(r.lines.any { it.contains("belakang yang bisa dipasangkan: 0, 2") })
        assertTrue(r.lines.any { it.contains("depan yang bisa dipasangkan: 1") && !it.contains("1, 1") })
        assertTrue(r.lines.any { it.contains("pasangan depan + belakang: 2") })
    }

    @Test fun missingFlagWithPairsIsNotedButNotRejected() {
        val r = DualCameraCheck.evaluate(true, false, listOf(listOf(back(), front())))
        assertEquals(DualState.SUPPORTED, r.state)
        assertTrue(r.lines.any { it.contains("Perlu dicoba langsung") })
    }
}
