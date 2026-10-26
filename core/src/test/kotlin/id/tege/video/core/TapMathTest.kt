package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class TapMathTest {
    @Test fun uprightImageKeepsPoint() {
        val p=TapMath.toSensor(.25f,.75f,0,false)
        assertEquals(.25f,p.first,1e-6f);assertEquals(.75f,p.second,1e-6f)
    }
    @Test fun portraitRotationMapsCornersBackToSensor() {
        // Sensor image turned 90 degrees clockwise: its top-left corner shows at the top-right of the screen.
        val topRight=TapMath.toSensor(1f,0f,90,false)
        assertEquals(0f,topRight.first,1e-6f);assertEquals(0f,topRight.second,1e-6f)
        val bottomLeft=TapMath.toSensor(0f,1f,90,false)
        assertEquals(1f,bottomLeft.first,1e-6f);assertEquals(1f,bottomLeft.second,1e-6f)
    }
    @Test fun rotation180AndMirrorAreUndone() {
        val p=TapMath.toSensor(.2f,.3f,180,false)
        assertEquals(.8f,p.first,1e-6f);assertEquals(.7f,p.second,1e-6f)
        val m=TapMath.toSensor(.2f,.3f,0,true)
        assertEquals(.8f,m.first,1e-6f);assertEquals(.3f,m.second,1e-6f)
    }
    @Test fun zoomedPointMapsInsideCrop() {
        val crop=ZoomPolicy.crop(0,0,4000,3000,2f)
        val center=TapMath.toActive(.5f,.5f,crop,0,0,4000,3000)
        assertEquals(.5f,center.first,1e-3f);assertEquals(.5f,center.second,1e-3f)
        val corner=TapMath.toActive(0f,0f,crop,0,0,4000,3000)
        assertEquals(.25f,corner.first,1e-3f);assertEquals(.25f,corner.second,1e-3f)
    }
    @Test fun regionStaysInsideArray() {
        val r=TapMath.region(0f,1f,100,50,4100,3050)
        assertTrue(r[0]>=100 && r[1]>=50 && r[2]<=4100 && r[3]<=3050)
        assertEquals(r[2]-r[0],r[3]-r[1])
    }
    @Test fun dragUpBrightensAndClamps() {
        assertTrue(TapMath.evFromDrag(0,300f,1000f,-12,12)>0)
        assertTrue(TapMath.evFromDrag(0,-300f,1000f,-12,12)<0)
        assertEquals(12,TapMath.evFromDrag(0,100000f,1000f,-12,12))
        assertEquals(-12,TapMath.evFromDrag(0,-100000f,1000f,-12,12))
        assertEquals(0,TapMath.evFromDrag(0,0f,1000f,-12,12))
    }
}
