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
}
