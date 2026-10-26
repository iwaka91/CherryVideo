package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class BokehMathTest {
    private fun rgb(p: Int)=Triple((p shr 16) and 0xff,(p shr 8) and 0xff,p and 0xff)

    @Test fun grayAndPrimaryColorsConvert() {
        // 2x2 frame, semi-planar chroma (pixel stride 2) like most Android cameras.
        val y=byteArrayOf(128.toByte(),128.toByte(),0,255.toByte())
        val u=byteArrayOf(128.toByte(),0);val v=byteArrayOf(128.toByte(),0)
        val out=IntArray(4)
        YuvToArgb.convert(y,2,u,v,2,2,2,2,out)
        assertEquals(Triple(128,128,128),rgb(out[0]))
        assertEquals(Triple(0,0,0),rgb(out[2]))
        assertEquals(Triple(255,255,255),rgb(out[3]))
        assertEquals(0xff,(out[0] ushr 24))
        // Pure red in JFIF: Y=76, Cb=85, Cr=255
        val red=IntArray(1)
        YuvToArgb.convert(byteArrayOf(76),1,byteArrayOf(85),byteArrayOf(255.toByte()),1,1,1,1,red)
        val (r,g,b)=rgb(red[0])
        assertTrue("r=$r",r>=250);assertTrue("g=$g",g<=5);assertTrue("b=$b",b<=5)
    }

    @Test fun rowStridePaddingIsSkipped() {
        val y=byteArrayOf(10,20,99,30,40,99) // width 2, row stride 3
        val u=byteArrayOf(128.toByte());val v=byteArrayOf(128.toByte())
        val out=IntArray(4)
        YuvToArgb.convert(y,3,u,v,1,1,2,2,out)
        assertEquals(listOf(10,20,30,40),out.map { it and 0xff })
    }

    @Test fun timingPercentiles() {
        val s=TimingStats.of((1..10).map { it.toDouble() })
        assertEquals(10,s.count);assertEquals(5.5,s.mean,1e-9);assertEquals(5.0,s.p50,1e-9);assertEquals(9.0,s.p90,1e-9);assertEquals(10.0,s.max,1e-9)
        assertEquals(0,TimingStats.of(emptyList()).count)
    }

    @Test fun assessmentWording() {
        assertTrue(BokehAssessment.text(29.5,30.0,20.0).startsWith("cukup cepat"))
        assertTrue(BokehAssessment.text(29.5,30.0,45.0).startsWith("belum"))
        assertTrue(BokehAssessment.text(18.0,30.0,50.0).startsWith("belum"))
        assertTrue(BokehAssessment.text(6.0,30.0,150.0).startsWith("terlalu lambat"))
        assertTrue(BokehAssessment.text(0.0,30.0,0.0).startsWith("tidak ada"))
    }
}
