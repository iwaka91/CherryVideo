package id.tege.video.core

/** AudioRecord timestamps identify a captured frame, not the time startRecording returned.
 * Anchor once, then count samples so a slow read/codec thread cannot stretch the audio. */
class AudioFrameClock(private val sampleRate: Int = 48_000) {
    private var firstFrameNs: Long? = null
    fun anchor(framePosition: Long, captureTimeNs: Long) {
        require(framePosition >= 0 && captureTimeNs > 0)
        if (firstFrameNs == null) firstFrameNs = captureTimeNs - framePosition * 1_000_000_000L / sampleRate
    }
    fun timeUs(framePosition: Long, epochNs: Long): Long =
        (checkNotNull(firstFrameNs) + framePosition * 1_000_000_000L / sampleRate - epochNs) / 1000
}

/** Keep only overlapping startup media on ONE timeline. Never zero the tracks separately.
 * Start on a decodable video keyframe; trim audio pre-roll rather than retaining a long
 * audio-only lead-in. If the microphone starts late, wait for the next video keyframe.
 * This changes only the leading boundary, never inter-track offsets or playback speed. */
class RecordingStart(private val expectAudio: Boolean) {
    var originUs: Long? = null; private set
    var firstVideoUs: Long? = null; private set
    var firstAudioUs: Long? = null; private set
    private val keyframes = ArrayList<Long>()

    fun observe(video: Boolean, ptsUs: Long, keyframe: Boolean = false) {
        if (originUs != null) return
        check(ptsUs in -100_000L..10_000_000L) { "Clock rekaman tidak valid saat mulai: $ptsUs us." }
        if (video) {
            if (firstVideoUs == null) firstVideoUs = ptsUs
            if (keyframe && ptsUs >= 0) keyframes.add(ptsUs)
        } else if (firstAudioUs == null) firstAudioUs = ptsUs
        if (expectAudio && firstAudioUs == null) return
        val minimum = if (expectAudio) firstAudioUs!! - 100_000L else 0L
        originUs = keyframes.firstOrNull { it >= minimum }
    }

    fun outputTimeUs(ptsUs: Long): Long? = (ptsUs - checkNotNull(originUs)).takeIf { it >= 0 }
}
