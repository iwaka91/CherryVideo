package id.tege.video.core

import org.junit.Assert.*
import org.junit.Test

class RecordingClockTest {
    @Test fun microphoneStartupLatencyIsNotMistakenForSampleCaptureTime() {
        val clock = AudioFrameClock()
        val epoch = 100_000_000_000L
        // startRecording may return late. The HAL timestamp maps frame 4800 to 100.7s.
        clock.anchor(4800, epoch + 700_000_000L)
        assertEquals(600_000L, clock.timeUs(0, epoch))
        assertEquals(700_000L, clock.timeUs(4800, epoch))
        // Later thread scheduling cannot change already chosen sample timing.
        clock.anchor(9600, epoch + 2_000_000_000L)
        assertEquals(800_000L, clock.timeUs(9600, epoch))
    }
    @Test fun readCompletionFallbackAccountsForAllPreviouslyCapturedFrames() {
        val clock = AudioFrameClock()
        clock.anchor(4096, 20_100_000_000L)
        assertEquals(14_666L, clock.timeUs(0, 20_000_000_000L))
        assertEquals(100_000L, clock.timeUs(4096, 20_000_000_000L))
    }
    @Test fun slowVideoStartupTrimsAudioWithoutMovingItRelativeToVideo() {
        val start = RecordingStart(true)
        start.observe(false, 20_000)
        start.observe(true, 900_000, true)
        assertEquals(900_000L, start.originUs)
        assertNull(start.outputTimeUs(20_000))
        assertEquals(0L, start.outputTimeUs(900_000))
        assertEquals(16_000L, start.outputTimeUs(916_000))
        assertEquals(50_000L, start.outputTimeUs(950_000))
    }
    @Test fun slowMicrophoneWaitsForDecodableKeyframeRatherThanZeroingEachTrack() {
        val start = RecordingStart(true)
        start.observe(true, 10_000, true)
        start.observe(true, 43_333)
        start.observe(false, 800_000)
        assertNull(start.originUs)
        start.observe(true, 1_010_000, true)
        assertEquals(1_010_000L, start.originUs)
        assertNull(start.outputTimeUs(800_000))
        assertEquals(11_000L, start.outputTimeUs(1_021_000))
    }
    @Test fun smallAudioLagAndAacPrimingKeepTheFirstKeyframe() {
        val start = RecordingStart(true)
        start.observe(true, 40_000, true)
        start.observe(false, 65_000)
        assertEquals(40_000L, start.originUs)
        assertEquals(25_000L, start.outputTimeUs(65_000))
        val priming = RecordingStart(true)
        priming.observe(false, -21_333)
        priming.observe(true, 10_000, true)
        assertEquals(10_000L, priming.originUs)
        assertNull(priming.outputTimeUs(-21_333))
    }
    @Test fun delayedCodecOutputDoesNotChangeChosenTimeline() {
        val start = RecordingStart(true)
        start.observe(true, 20_000, true)
        start.observe(true, 1_020_000, true)
        start.observe(false, 30_000)
        assertEquals(20_000L, start.originUs)
    }
    @Test fun videoOnlyStillRequiresAKeyframe() {
        val start = RecordingStart(false)
        start.observe(true, 100_000)
        assertNull(start.originUs)
        start.observe(true, 1_100_000, true)
        assertEquals(0L, start.outputTimeUs(1_100_000))
    }
    @Test fun bootTimeRegressionIsRejectedRatherThanRebasedToLookValid() {
        assertThrows(IllegalStateException::class.java) {
            RecordingStart(true).observe(true, 15_070_000_000L, true)
        }
    }
}
