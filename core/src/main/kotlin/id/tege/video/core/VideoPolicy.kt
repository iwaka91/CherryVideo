package id.tege.video.core

import kotlin.math.abs

data class VideoProfile(val width: Int, val height: Int, val fps: Int, val captureFps: Int = fps) {
    val highSpeed: Boolean get() = captureFps > fps
    val label: String get() = "${if (width >= 3840) "4K" else "${height}p"} · $fps fps"
    val bitrate: Int get() = when { width >= 3840 -> 48_000_000; fps >= 60 -> 28_000_000; height >= 1080 -> 18_000_000; else -> 10_000_000 }
}

object VideoPolicy {
    /** Zero duration means unknown, not unsupported. Only regular <=30 fps may use that fallback. */
    fun qualifies(profile: VideoProfile, rangeUpper: Int, minFrameDurationNs: Long, encoderSupported: Boolean): Boolean =
        encoderSupported && rangeUpper >= profile.fps && minFrameDurationNs >= 0 &&
            ((minFrameDurationNs == 0L && profile.fps <= 30) ||
                (minFrameDurationNs > 0 && minFrameDurationNs <= 1_000_000_000L / profile.fps + 1_000L))
}

/** Maps REALTIME camera stamps to the shared MONOTONIC audio/codec clock.
 * Never subtract only the first video frame: the recorder anchors both tracks to one epoch. */
class FrameClock(private val offsetNs: Long = 0L) {
    fun monotonic(sensorNs: Long): Long = sensorNs - offsetNs
}

/** Output rate limiting retains real elapsed time; it never converts 120 fps into slow motion. */
class FrameGate(private val fps: Int) {
    private var nextNs = Long.MIN_VALUE
    fun accept(stampNs: Long): Boolean {
        val period = 1_000_000_000L / fps
        if (nextNs != Long.MIN_VALUE && stampNs + period / 10 < nextNs) return false
        nextNs = if (nextNs == Long.MIN_VALUE || stampNs > nextNs + period) stampNs + period else nextNs + period
        return true
    }
}

data class Cadence(val frames: Int, val fps: Double, val maxGapMs: Double, val steady: Boolean)

object VideoCadence {
    /** Uses encoded sample timestamps, independent of nominal container frame-rate flags. */
    fun inspect(timesUs: List<Long>, targetFps: Int): Cadence {
        require(targetFps > 0)
        if (timesUs.size < 2) return Cadence(timesUs.size, 0.0, 0.0, false)
        // B-frame streams may expose samples in decode order; cadence uses presentation order.
        val presentation = timesUs.sorted()
        val gaps = presentation.zipWithNext { a, b -> b - a }
        if (gaps.any { it <= 0L }) return Cadence(timesUs.size, 0.0, 0.0, false)
        val fps = (timesUs.size - 1) * 1_000_000.0 / (presentation.last() - presentation.first())
        val expected = 1_000_000.0 / targetFps
        return Cadence(timesUs.size, fps, gaps.max() / 1000.0,
            abs(fps - targetFps) / targetFps <= 0.05 && gaps.max() <= expected * 1.55)
    }
}
