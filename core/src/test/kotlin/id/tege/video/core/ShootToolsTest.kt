package id.tege.video.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

class ShootToolsTest {
    @Test fun pauseCutsTheGapAndResumesOnAKeyframe() {
        val t = PauseTimeline()
        assertEquals(PauseTimeline.Kind.KEEP, t.decide(1_000_000, true, false).kind)
        t.pause(2_000_000)
        assertEquals(PauseTimeline.Kind.KEEP, t.decide(1_990_000, false, false).kind)
        assertEquals(PauseTimeline.Kind.DROP, t.decide(2_500_000, true, true).kind)
        assertEquals(PauseTimeline.Kind.DROP, t.decide(2_500_000, false, false).kind)
        t.resume(5_000_000)
        assertTrue(t.awaitingKeyframe)
        assertEquals(PauseTimeline.Kind.DROP, t.decide(4_900_000, false, false).kind)
        assertEquals(PauseTimeline.Kind.HOLD, t.decide(5_010_000, false, false).kind)
        assertEquals(PauseTimeline.Kind.DROP, t.decide(5_020_000, true, false).kind)
        val key = t.decide(5_033_000, true, true)
        assertEquals(PauseTimeline.Kind.KEEP, key.kind)
        assertEquals(2_000_000L, key.adjustedUs)
        // Held audio from before the keyframe is cut, later audio follows the picture.
        assertEquals(PauseTimeline.Kind.DROP, t.decide(5_010_000, false, false).kind)
        assertEquals(2_007_000L, t.decide(5_040_000, false, false).adjustedUs)
        assertEquals(1_000_000L, t.map(1_000_000))
        assertNull(t.map(3_000_000))
        assertEquals(2_100_000L, t.map(5_133_000))
    }

    @Test fun secondPauseAddsUp() {
        val t = PauseTimeline()
        t.pause(1_000_000); t.resume(2_000_000); t.decide(2_000_000, true, true)
        t.pause(3_000_000); t.resume(4_000_000); t.decide(4_100_000, true, true)
        assertEquals(2_100_000L, t.removedBefore(5_000_000))
        assertEquals(2_900_000L, t.decide(5_000_000, false, false).adjustedUs)
    }

    @Test fun windFilterRemovesRumbleKeepsVoice() {
        fun rms(hz: Double): Double {
            val f = WindFilter(1)
            val n = 48_000
            val b = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) b.putShort(i * 2, (sin(2 * Math.PI * hz * i / 48_000) * 10_000).toInt().toShort())
            f.process(b, n)
            var s = 0.0
            for (i in n / 2 until n) { val v = b.getShort(i * 2).toDouble(); s += v * v }
            return Math.sqrt(s / (n / 2))
        }
        assertTrue("20 Hz rumble must drop", rms(20.0) < 1_500)
        assertTrue("1 kHz voice must stay", abs(rms(1_000.0) - 7_071) < 400)
    }

    @Test fun silenceCutKeepsSpeechOnly() {
        val bin = 100_000L
        val levels = IntArray(100) { i -> if (i in 10..29 || i in 60..79) 12_000 else 120 }
        val parts = SilenceCut.speech(levels, bin, minSilenceUs = 1_500_000L, padUs = 200_000L)
        assertEquals(2, parts.size)
        assertEquals(800_000L, parts[0][0]); assertEquals(3_200_000L, parts[0][1])
        assertEquals(5_800_000L, parts[1][0]); assertEquals(8_200_000L, parts[1][1])
        val snapped = SilenceCut.snap(parts, longArrayOf(0, 1_000_000, 2_000_000, 3_000_000, 4_000_000, 5_000_000, 6_000_000, 7_000_000, 8_000_000, 9_000_000))
        assertEquals(0L, snapped[0][0]); assertEquals(5_000_000L, snapped[1][0])
        // Flat sound (no clear speech/silence) keeps everything.
        assertEquals(1, SilenceCut.speech(IntArray(50) { 3_000 }, bin).size)
    }

    @Test fun lightCheckFindsDarkAndBacklit() {
        assertEquals(LightCheck.State.DARK, LightCheck.analyze(FloatArray(16 * 9) { .05f }, 16, 9))
        assertEquals(LightCheck.State.OK, LightCheck.analyze(FloatArray(16 * 9) { .5f }, 16, 9))
        val backlit = FloatArray(16 * 9) { i ->
            val x = (i % 16 + .5f) / 16; val y = (i / 16 + .5f) / 9
            if (x in 0.3f..0.7f && y in 0.25f..0.75f) .2f else .85f
        }
        assertEquals(LightCheck.State.BACKLIT, LightCheck.analyze(backlit, 16, 9))
    }

    @Test fun namesAreSafe() {
        assertEquals("CherryVideo/Upacara 17 Agustus", ShotNames.folder("  Upacara 17  Agustus!! "))
        assertEquals("CherryVideo", ShotNames.folder("///"))
        assertEquals("Upacara_17_Agustus_20261027_100000.mp4", ShotNames.file("Upacara 17 Agustus", "20261027_100000"))
        assertEquals("Cherry_20261027_100000.mp4", ShotNames.file("", "20261027_100000"))
        assertTrue(ShotNames.minutesLeft(10L * 1024 * 1024 * 1024, 18_000_000) in 70..80)
    }
}
