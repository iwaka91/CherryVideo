package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer

class LiveBokehTest {
    @Test fun bokehOnlyForSlowEnoughSixteenByNine() {
        assertTrue(BokehPolicy.eligible(VideoProfile(1920,1080,30),false))
        assertTrue(BokehPolicy.eligible(VideoProfile(1280,720,30),false))
        assertFalse(BokehPolicy.eligible(VideoProfile(1920,1080,60),false))
        assertFalse(BokehPolicy.eligible(VideoProfile(1920,1080,60,120),false))
        assertFalse(BokehPolicy.eligible(VideoProfile(3840,2160,30),false))
        assertFalse(BokehPolicy.eligible(VideoProfile(1920,1080,30),true))
        assertFalse(BokehPolicy.eligible(VideoProfile(1440,1080,30),false))
        assertNull(BokehPolicy.reason(VideoProfile(1920,1080,30),false))
        assertTrue(BokehPolicy.reason(VideoProfile(3840,2160,30),false)!!.contains("4K"))
        assertTrue(BokehPolicy.reason(VideoProfile(1920,1080,60),false)!!.contains("30 fps"))
    }

    @Test fun smallStreamKeepsTheVideoShape() {
        val sizes=listOf(1920 to 1080,640 to 480,640 to 360,320 to 240,1280 to 720)
        assertEquals(640 to 360,BokehPolicy.smallSize(sizes,1920,1080))
        assertEquals(640 to 360,BokehPolicy.smallSize(sizes,1280,720))
        assertEquals(480 to 270,BokehPolicy.smallSize(listOf(1920 to 1080,480 to 270,960 to 540),1920,1080))
        assertNull(BokehPolicy.smallSize(listOf(640 to 480,320 to 240),1920,1080))
        assertNull(BokehPolicy.smallSize(emptyList(),1920,1080))
    }

    @Test fun heatBlocksStartFromModerate() {
        assertFalse(ThermalPolicy.blocksStart(0));assertFalse(ThermalPolicy.blocksStart(1))
        assertTrue(ThermalPolicy.blocksStart(2));assertTrue(ThermalPolicy.blocksStart(3));assertTrue(ThermalPolicy.blocksStart(5))
    }

    @Test fun hotCountdownRunsThirtySecondsThenStops() {
        val timer=HotStopTimer()
        assertEquals(HotAction.NONE,timer.update(2,0).action)
        val first=timer.update(3,1_000)
        assertEquals(HotAction.COUNTING,first.action);assertEquals(30,first.remainingSeconds)
        assertEquals(20,timer.update(3,11_000).remainingSeconds)
        assertEquals(1,timer.update(3,30_500).remainingSeconds)
        assertEquals(HotAction.STOP,timer.update(3,31_000).action)
    }

    @Test fun cooledPhoneCancelsTheCountdownAndCriticalStopsAtOnce() {
        val timer=HotStopTimer()
        timer.update(3,0);timer.update(3,20_000)
        assertEquals(HotAction.NONE,timer.update(2,21_000).action)
        val again=timer.update(3,22_000)
        assertEquals(30,again.remainingSeconds)
        assertEquals(HotAction.STOP,timer.update(4,22_500).action)
    }

    @Test fun turningTheImageUprightAndBackIsLossless() {
        val w=5;val h=3
        for(degrees in listOf(0,90,180,270)) {
            val seen=HashSet<Int>()
            for(row in 0 until h) for(col in 0 until w) {
                val at=MaskMath.uprightIndex(col,row,w,h,degrees)
                assertTrue(at in 0 until w*h);assertTrue("duplicate at $degrees",seen.add(at))
            }
        }
        assertEquals(3,MaskMath.uprightWidth(5,3,90));assertEquals(5,MaskMath.uprightHeight(5,3,90))
        assertEquals(5,MaskMath.uprightWidth(5,3,180));assertEquals(5,MaskMath.uprightHeight(5,3,270))
        // Clockwise 90: the bottom-left pixel of the camera image becomes the top-left pixel.
        assertEquals(0,MaskMath.uprightIndex(0,h-1,w,h,90))
        // Clockwise 270: the top-right pixel becomes the top-left pixel.
        assertEquals(0,MaskMath.uprightIndex(w-1,0,w,h,270))
    }

    @Test fun rotatedPictureKeepsItsColours() {
        val w=3;val h=2
        val y=ByteArray(w*h) { (it*40).toByte() }
        val chroma=ByteArray(w*h) { 128.toByte() }
        for(degrees in listOf(0,90,180,270)) {
            val out=IntArray(w*h)
            YuvToArgb.convertUpright(y,w,chroma,chroma,w,1,w,h,degrees,out)
            for(row in 0 until h) for(col in 0 until w)
                assertEquals("degrees $degrees col $col row $row",(row*w+col)*40,out[MaskMath.uprightIndex(col,row,w,h,degrees)] and 0xff)
        }
    }

    @Test fun maskComesBackInCameraOrientation() {
        val w=4;val h=3
        for(degrees in listOf(0,90,180,270)) {
            // A single marked camera pixel is turned upright like the picture, then the mask is turned back.
            val uw=MaskMath.uprightWidth(w,h,degrees);val uh=MaskMath.uprightHeight(w,h,degrees)
            val camera=IntArray(w*h)
            val marker=5
            camera[marker]=1
            val upright=FloatArray(uw*uh)
            for(row in 0 until h) for(col in 0 until w) if(camera[row*w+col]==1) upright[MaskMath.uprightIndex(col,row,w,h,degrees)]=1f
            val state=ByteArray(w*h)
            MaskMath.toCameraSpace(FloatBuffer.wrap(upright),uw,uh,w,h,degrees,state,0f)
            for(i in 0 until w*h) assertEquals("degrees $degrees pixel $i",if(i==marker) 255 else 0,state[i].toInt() and 0xff)
        }
    }

    @Test fun maskIsSmoothedOverTimeAndMeasuresCoverage() {
        val w=4;val h=4
        val full=FloatBuffer.wrap(FloatArray(w*h) { 1f })
        val state=ByteArray(w*h)
        val first=MaskMath.toCameraSpace(full,w,h,w,h,0,state,.4f)
        assertEquals(153,state[0].toInt() and 0xff)
        assertEquals(1f,first,1e-6f)
        MaskMath.toCameraSpace(full,w,h,w,h,0,state,.4f)
        assertEquals(214,state[0].toInt() and 0xff)
        val empty=FloatBuffer.wrap(FloatArray(w*h))
        val none=MaskMath.toCameraSpace(empty,w,h,w,h,0,ByteArray(w*h),0f)
        assertEquals(0f,none,1e-6f)
    }

    @Test fun maskOfAnotherSizeIsScaled() {
        val w=4;val h=4
        val half=FloatArray(2*2).also { it[0]=1f }
        val state=ByteArray(w*h)
        MaskMath.toCameraSpace(FloatBuffer.wrap(half),2,2,w,h,0,state,0f)
        assertEquals(255,state[0].toInt() and 0xff);assertEquals(255,state[1*w+1].toInt() and 0xff)
        assertEquals(0,state[2].toInt() and 0xff);assertEquals(0,state[2*w].toInt() and 0xff)
    }

    @Test fun presenceNeedsRealCoverage() {
        assertEquals(0f,MaskMath.presenceTarget(0f),1e-6f)
        assertEquals(0f,MaskMath.presenceTarget(.003f),1e-6f)
        assertEquals(1f,MaskMath.presenceTarget(.2f),1e-6f)
        assertTrue(MaskMath.presenceTarget(.01f) in 0.01f..0.99f)
    }

    @Test fun cinematicAmountsFollowTheSlider() {
        assertEquals(0f,CinematicMath.grain(0f),1e-6f);assertEquals(.07f,CinematicMath.grain(1f),1e-6f)
        assertEquals(.36f,CinematicMath.vignette(2f),1e-6f)
        assertEquals(16f/9f,CinematicMath.uprightAspect(1920,1080,0),1e-4f)
        assertEquals(9f/16f,CinematicMath.uprightAspect(1920,1080,90),1e-4f)
        assertEquals(1920f/1080f/2.39f,CinematicMath.barVisible(16f/9f),1e-3f)
        assertEquals(1f,CinematicMath.barVisible(3f),1e-6f)
    }
}
