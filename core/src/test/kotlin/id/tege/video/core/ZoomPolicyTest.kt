package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class ZoomPolicyTest {
    @Test fun cameraLimitsClampPinchAndInvalidInput() {
        assertEquals(.6f,ZoomPolicy.clamp(.2f,.6f,4f),0f)
        assertEquals(4f,ZoomPolicy.clamp(10f,.6f,4f),0f)
        assertEquals(1f,ZoomPolicy.clamp(Float.NaN,.6f,4f),0f)
    }
    @Test fun cropRetainsNonzeroSensorOriginAndCenter() {
        assertArrayEquals(intArrayOf(1100,800,3100,2300),ZoomPolicy.crop(100,50,4100,3050,2f))
        assertArrayEquals(intArrayOf(100,50,4100,3050),ZoomPolicy.crop(100,50,4100,3050,1f))
    }
    @Test fun extremeZoomCannotProduceEmptyOrOffSensorCrop() {
        val a=ZoomPolicy.crop(12,20,101,103,1000f)
        assertTrue(a[0]>=12 && a[1]>=20 && a[2]<=101 && a[3]<=103)
        assertTrue(a[2]>a[0] && a[3]>a[1])
    }
    @Test fun presetsOnlyListRatiosTheRearCameraSupports() {
        assertEquals(listOf(.6f,1f,2f),ZoomPolicy.presets(.6f,10f,false))
        assertEquals(listOf(1f,2f),ZoomPolicy.presets(1f,10f,false))
        assertEquals(listOf(.6f,1f),ZoomPolicy.presets(.6f,1.5f,false))
        assertTrue(ZoomPolicy.presets(1f,1f,false).isEmpty())
        assertTrue(ZoomPolicy.presets(1f,8f,true).isEmpty())
        assertTrue(ZoomPolicy.presets(Float.NaN,8f,false).isEmpty())
    }
    @Test fun labelsAreShortAndMatchBadge() {
        assertEquals("0.6×",ZoomPolicy.label(.6f))
        assertEquals("1×",ZoomPolicy.label(1f))
        assertEquals("2×",ZoomPolicy.label(2f))
        assertEquals("2.4×",ZoomPolicy.label(2.4048f))
    }
    @Test fun presetMatchUsesSmallTolerance() {
        val presets=listOf(.6f,1f,2f)
        assertEquals(1f,ZoomPolicy.matchesPreset(1.03f,presets))
        assertNull(ZoomPolicy.matchesPreset(1.4f,presets))
    }
}
