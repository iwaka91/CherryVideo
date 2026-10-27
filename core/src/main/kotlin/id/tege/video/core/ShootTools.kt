package id.tege.video.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pause / resume inside one video file. Times are encoder presentation times (microseconds) on the shared recording clock.
 * A pause opens a gap; after resume the gap is closed by the first video keyframe, so the file never continues on a
 * frame that depends on dropped frames. Audio waits (HOLD) until that keyframe is known, keeping sound and picture aligned.
 */
class PauseTimeline {
    enum class Kind { KEEP, DROP, HOLD }
    data class Decision(val kind: Kind, val adjustedUs: Long = 0L)
    private val starts = ArrayList<Long>()
    private val ends = ArrayList<Long>()
    private var resumeUs = -1L
    var paused = false; private set
    val awaitingKeyframe: Boolean get() = !paused && resumeUs >= 0L
    val gapCount: Int get() = starts.size

    fun pause(atUs: Long) {
        if (paused || awaitingKeyframe) return
        paused = true; starts.add(atUs); ends.add(OPEN)
    }
    fun resume(atUs: Long) {
        if (!paused) return
        paused = false; resumeUs = maxOf(atUs, starts.last())
    }
    /** Microseconds removed from the file before [ptsUs] (closed gaps only). */
    fun removedBefore(ptsUs: Long): Long {
        var sum = 0L
        for (i in starts.indices) if (ends[i] != OPEN && ends[i] <= ptsUs) sum += ends[i] - starts[i]
        return sum
    }
    private fun insideClosedGap(ptsUs: Long): Boolean {
        for (i in starts.indices) if (ends[i] != OPEN && ptsUs >= starts[i] && ptsUs < ends[i]) return true
        return false
    }
    fun decide(ptsUs: Long, video: Boolean, keyframe: Boolean): Decision {
        if (insideClosedGap(ptsUs)) return Decision(Kind.DROP)
        val open = starts.isNotEmpty() && ends.last() == OPEN
        if (open && ptsUs >= starts.last()) {
            if (paused || ptsUs < resumeUs) return Decision(Kind.DROP)
            if (!video) return Decision(Kind.HOLD)
            if (!keyframe) return Decision(Kind.DROP)
            ends[ends.size - 1] = ptsUs; resumeUs = -1L
        }
        return Decision(Kind.KEEP, ptsUs - removedBefore(ptsUs))
    }
    /** Where a moment of the recording clock ends up in the file, or null when it was cut out (or is still undecided). */
    fun map(ptsUs: Long): Long? {
        if (insideClosedGap(ptsUs)) return null
        if (starts.isNotEmpty() && ends.last() == OPEN && ptsUs >= starts.last()) return null
        return ptsUs - removedBefore(ptsUs)
    }
    companion object { private const val OPEN = Long.MAX_VALUE }
}

/** Second-order high-pass (about 100 Hz) on 16-bit little-endian PCM. Removes wind rumble and handling noise; voices stay. */
class WindFilter(private val channels: Int, sampleRate: Int = 48_000, cutoffHz: Double = 100.0) {
    private val b0: Double; private val b1: Double; private val b2: Double; private val a1: Double; private val a2: Double
    private val x1 = DoubleArray(channels); private val x2 = DoubleArray(channels)
    private val y1 = DoubleArray(channels); private val y2 = DoubleArray(channels)
    init {
        val w = 2 * PI * cutoffHz / sampleRate
        val alpha = sin(w) / (2 * sqrt(0.5))
        val c = cos(w)
        val a0 = 1 + alpha
        b0 = (1 + c) / 2 / a0; b1 = -(1 + c) / a0; b2 = (1 + c) / 2 / a0
        a1 = -2 * c / a0; a2 = (1 - alpha) / a0
    }
    fun process(buffer: ByteBuffer, frames: Int) {
        val order = buffer.order()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) for (ch in 0 until channels) {
            val at = (i * channels + ch) * 2
            val x = buffer.getShort(at).toDouble()
            val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
            x2[ch] = x1[ch]; x1[ch] = x; y2[ch] = y1[ch]; y1[ch] = y
            buffer.putShort(at, y.coerceIn(-32768.0, 32767.0).toInt().toShort())
        }
        buffer.order(order)
    }
}

/** Finds the spoken parts of a recording from a peak-level log, for "Rapikan bagian diam". */
object SilenceCut {
    /** Peak level 0..32767 to dBFS. */
    fun db(peak: Int): Double = if (peak <= 0) -90.0 else 20 * log10(peak / 32767.0)
    /**
     * [levels] holds one peak per [binUs]. Returns pairs startUs,endUs of parts to keep (speech plus [padUs]).
     * Quiet stretches shorter than [minSilenceUs] stay. When speech and silence cannot be told apart, the whole clip is kept.
     */
    fun speech(levels: IntArray, binUs: Long, minSilenceUs: Long = 1_500_000L, padUs: Long = 300_000L): List<LongArray> {
        val total = levels.size * binUs
        if (levels.isEmpty()) return emptyList()
        val sorted = levels.map { db(it) }.sorted()
        val low = sorted[(sorted.size * 0.1).toInt().coerceIn(0, sorted.size - 1)]
        val high = sorted[(sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)]
        if (high - low < 12.0) return listOf(longArrayOf(0L, total))
        val threshold = low + (high - low) * 0.3
        val runs = ArrayList<LongArray>()
        var start = -1
        for (i in levels.indices) {
            val loud = db(levels[i]) >= threshold
            if (loud && start < 0) start = i
            if (!loud && start >= 0) { runs.add(longArrayOf(start * binUs, i * binUs)); start = -1 }
        }
        if (start >= 0) runs.add(longArrayOf(start * binUs, total))
        if (runs.isEmpty()) return listOf(longArrayOf(0L, total))
        val merged = ArrayList<LongArray>()
        for (r in runs) {
            val last = merged.lastOrNull()
            if (last != null && r[0] - last[1] < minSilenceUs) last[1] = r[1] else merged.add(longArrayOf(r[0], r[1]))
        }
        return merged.map { longArrayOf((it[0] - padUs).coerceAtLeast(0L), (it[1] + padUs).coerceAtMost(total)) }
            .fold(ArrayList<LongArray>()) { out, r ->
                val last = out.lastOrNull()
                if (last != null && r[0] <= last[1]) last[1] = maxOf(last[1], r[1]) else out.add(r)
                out
            }
    }
    /** Each part starts on a video keyframe at or before its start (so it decodes cleanly); overlapping parts are joined. */
    fun snap(parts: List<LongArray>, keyframesUs: LongArray): List<LongArray> {
        if (keyframesUs.isEmpty()) return parts.map { it.copyOf() }
        val keys = keyframesUs.sortedArray()
        val out = ArrayList<LongArray>()
        for (p in parts) {
            val s = keys.lastOrNull { it <= p[0] } ?: keys.first()
            val e = p[1]
            if (e <= s) continue
            val last = out.lastOrNull()
            if (last != null && s <= last[1]) last[1] = maxOf(last[1], e) else out.add(longArrayOf(s, e))
        }
        return out
    }
    fun length(parts: List<LongArray>): Long = parts.sumOf { it[1] - it[0] }
}

/** Brightness check for the "terlalu gelap / membelakangi cahaya" warning, from a small luminance grid (0..1). */
object LightCheck {
    enum class State { OK, DARK, BACKLIT }
    fun analyze(lum: FloatArray, w: Int, h: Int): State {
        if (w <= 0 || h <= 0 || lum.size < w * h) return State.OK
        var all = 0.0; var centre = 0.0; var centreN = 0; var edge = 0.0; var edgeN = 0
        for (y in 0 until h) for (x in 0 until w) {
            val v = lum[y * w + x].toDouble(); all += v
            val fx = (x + .5) / w; val fy = (y + .5) / h
            if (fx in 0.3..0.7 && fy in 0.25..0.75) { centre += v; centreN++ } else { edge += v; edgeN++ }
        }
        val mean = all / (w * h)
        val c = if (centreN > 0) centre / centreN else mean
        val e = if (edgeN > 0) edge / edgeN else mean
        return when {
            mean < .14 -> State.DARK
            e - c > .22 && c < .4 -> State.BACKLIT
            else -> State.OK
        }
    }
}

/** Names for activity folders and files; only letters, digits, spaces, '-' and '_' are kept. */
object ShotNames {
    fun clean(raw: String): String =
        raw.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "").replace(Regex("\\s+"), " ").take(40).trim()
    fun folder(activity: String): String = clean(activity).let { if (it.isEmpty()) "CherryVideo" else "CherryVideo/$it" }
    fun file(activity: String, stamp: String, extension: String = "mp4"): String =
        clean(activity).replace(' ', '_').let { if (it.isEmpty()) "Cherry_$stamp.$extension" else "${it}_$stamp.$extension" }
    /** Minutes of video that still fit, for the storage warning. */
    fun minutesLeft(freeBytes: Long, bitrate: Int, reserveBytes: Long = 192L * 1024 * 1024): Long {
        if (bitrate <= 0) return Long.MAX_VALUE
        val usable = (freeBytes - reserveBytes).coerceAtLeast(0L)
        return usable * 8 / (bitrate.toLong() + 192_000L) / 60
    }
}
