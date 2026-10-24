package id.tege.video.core

import kotlin.math.abs

data class VideoProfile(val width: Int, val height: Int, val fps: Int) {
    val label: String get() = "${if (width >= 3840) "4K" else "${height}p"} · $fps fps"
    val bitrate: Int get() = when { width >= 3840 -> 48_000_000; fps >= 60 -> 28_000_000; height >= 1080 -> 18_000_000; else -> 10_000_000 }
}

object VideoPolicy {
    /** Standard Camera2 sessions only. A high-speed capability alone never qualifies 60 fps. */
    fun qualifies(profile: VideoProfile, rangeUpper: Int, minFrameDurationNs: Long, encoderSupported: Boolean): Boolean =
        encoderSupported && rangeUpper >= profile.fps && minFrameDurationNs > 0 &&
            minFrameDurationNs <= 1_000_000_000L / profile.fps + 1_000L
}

data class Cadence(val frames: Int, val fps: Double, val maxGapMs: Double, val steady: Boolean)

object VideoCadence {
    /** Uses encoded sample timestamps, independent of nominal container frame-rate flags. */
    fun inspect(timesUs: List<Long>, targetFps: Int): Cadence {
        require(targetFps > 0)
        if (timesUs.size < 2) return Cadence(timesUs.size, 0.0, 0.0, false)
        val gaps = timesUs.zipWithNext { a, b -> b - a }
        if (gaps.any { it <= 0L }) return Cadence(timesUs.size, 0.0, 0.0, false)
        val fps = (timesUs.size - 1) * 1_000_000.0 / (timesUs.last() - timesUs.first())
        val expected = 1_000_000.0 / targetFps
        return Cadence(timesUs.size, fps, gaps.max() / 1000.0,
            abs(fps - targetFps) / targetFps <= 0.05 && gaps.max() <= expected * 1.55)
    }
}
