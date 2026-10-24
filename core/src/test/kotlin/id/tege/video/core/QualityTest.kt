package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class QualityTest {
    private fun identity(n: Int = 2): ByteArray = buildString {
        append("\uFEFF# LUT warna sinematik\nTITLE \"Hangat Nusantara\"\nLUT_3D_SIZE $n\n")
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n)
            append("${r.toFloat()/(n-1)} ${g.toFloat()/(n-1)} ${b.toFloat()/(n-1)}\n")
    }.toByteArray()
    private fun rejected(bytes: ByteArray) { assertThrows(IllegalArgumentException::class.java) { CubeLut.parse(bytes) } }
    @Test fun identityInterpolatesAllChannels() {
        val lut = CubeLut.parse(identity())
        assertArrayEquals(floatArrayOf(.2f,.5f,.9f), lut.sample(.2f,.5f,.9f), 0.00001f)
    }
    @Test fun commercial33CubePreservesOrder() {
        val lut = CubeLut.parse(identity(33))
        assertEquals(33, lut.size)
        assertArrayEquals(floatArrayOf(.17f,.46f,.87f), lut.sample(.17f,.46f,.87f), .0001f)
    }
    @Test fun exportRoundTrips() {
        val lut = CubeLut.parse(identity(17))
        val copy = CubeLut.parse(lut.export())
        assertArrayEquals(lut.values, copy.values, 0f)
    }
    @Test fun domainAndInlineComments() {
        val text = identity().toString(Charsets.UTF_8).replace("LUT_3D_SIZE 2", "LUT_3D_SIZE 2 # comment\nDOMAIN_MIN -1 -1 -1\nDOMAIN_MAX 1 1 1")
        assertArrayEquals(floatArrayOf(.5f,.5f,.5f), CubeLut.parse(text.toByteArray()).sample(0f,0f,0f), .0001f)
    }
    @Test fun truncatedAndExtraTablesRejected() {
        rejected(identity().copyOf(80)); rejected(identity()+"0 0 0\n".toByteArray())
    }
    @Test fun nonFiniteAndHugeTableRejected() {
        rejected(identity().toString(Charsets.UTF_8).replace("0.0 0.0 0.0", "NaN 0 0").toByteArray())
        rejected("LUT_3D_SIZE 9999999999999".toByteArray())
        rejected("LUT_3D_SIZE 65".toByteArray())
        rejected(ByteArray(CubeLut.MAX_BYTES+1))
    }
    @Test fun shaperAndInvalidUtf8Rejected() {
        rejected("LUT_1D_SIZE 2\n0 0 0\n1 1 1".toByteArray()); rejected(byteArrayOf(-61,40))
    }
    @Test fun outputOvershootRetainedForGpu() {
        val text=identity().toString(Charsets.UTF_8).replace("1.0 1.0 1.0", "1.1 -0.1 1.0")
        assertArrayEquals(floatArrayOf(1.1f,-.1f,1f), CubeLut.parse(text.toByteArray()).sample(1f,1f,1f), .0001f)
    }
    @Test fun advertisedHighSpeedIsNotNormal60() {
        assertFalse(VideoPolicy.qualifies(VideoProfile(1920,1080,60),30,16_666_666,true))
        assertFalse(VideoPolicy.qualifies(VideoProfile(1920,1080,60),60,33_333_333,true))
        assertTrue(VideoPolicy.qualifies(VideoProfile(1920,1080,60),60,16_666_666,true))
    }
    @Test fun catchesMislabelledAndVariableRateVideo() {
        val thirty=(0L..90L).map { it*33_333 }
        assertTrue(VideoCadence.inspect(thirty,30).steady)
        assertFalse(VideoCadence.inspect(thirty,60).steady)
        val vfr=(0L..60L).map { it*16_667 + if(it>30) 66_666 else 0 }
        assertFalse(VideoCadence.inspect(vfr,60).steady)
    }
    @Test fun duplicateTimestampIsNotValidCadence() { assertFalse(VideoCadence.inspect(listOf(0L,0L,33_333),30).steady) }
    @Test fun bFrameDecodeOrderDoesNotInvalidateCadence() {
        assertTrue(VideoCadence.inspect(listOf(0L,66_666L,33_333L,99_999L,166_665L,133_332L),30).steady)
    }
    @Test fun focusPullReachesMarkAndCancelsStaleTicks() {
        val s=FocusPullSession()
        val token=s.start(FocusPullPlan(0f,4f,1000,FocusPullEasing.EASE_IN_OUT),0)
        assertEquals(2f,s.tick(token,500)!!.diopters,.0001f)
        assertEquals(4f,s.tick(token,1000)!!.diopters,.0001f)
        assertNull(s.tick(token,1100))
        val next=s.start(FocusPullPlan(4f,1f,1000,FocusPullEasing.LINEAR),2000)
        s.cancel(); assertNull(s.tick(next,2100))
    }
}
