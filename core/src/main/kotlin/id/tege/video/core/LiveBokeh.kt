package id.tege.video.core

import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** When live Bokeh may run: people only, 1080p or 720p at 30 fps or lower, 16:9. 60 fps, high-speed and 4K never use it. */
object BokehPolicy {
    fun is16x9(width: Int,height: Int): Boolean = height>0 && abs(width.toDouble()/height-16.0/9.0)<0.02

    /** Plain-language reason Bokeh is unavailable, or null when it may run. */
    fun reason(profile: VideoProfile,physicalLens: Boolean): String? = when {
        physicalLens -> "Bokeh tidak tersedia pada lensa teknis (uji). Pakai kamera Belakang atau Depan."
        profile.highSpeed || profile.fps>30 -> "Bokeh hanya untuk 30 fps ke bawah. Pilih 1080p atau 720p 30 fps."
        profile.width>1920 || profile.height>1080 -> "Bokeh tidak tersedia di 4K. Pilih 1080p atau 720p 30 fps."
        !is16x9(profile.width,profile.height) -> "Bokeh hanya untuk video 16:9 (1080p atau 720p)."
        else -> null
    }

    fun eligible(profile: VideoProfile,physicalLens: Boolean): Boolean = reason(profile,physicalLens)==null

    /** Small YUV size for segmentation with the same shape as the video (so the mask lines up): 640x360 when offered. */
    fun smallSize(sizes: List<Pair<Int,Int>>,videoWidth: Int,videoHeight: Int): Pair<Int,Int>? {
        if(videoHeight<=0) return null
        val aspect=videoWidth.toDouble()/videoHeight
        val matching=sizes.filter { it.second>0 && abs(it.first.toDouble()/it.second-aspect)<0.02 }
        return matching.firstOrNull { it.first==640 && it.second==360 }
            ?: matching.filter { it.first in 320..800 }.minByOrNull { it.first*it.second }
            ?: matching.filter { it.first>=320 }.minByOrNull { it.first*it.second }
    }
}

/** Android thermal status numbers: 2 = moderate, 3 = severe, 4 = critical. */
object ThermalPolicy {
    const val BLOCK_START=2
    const val COUNTDOWN=3
    const val STOP_NOW=4
    const val COUNTDOWN_SECONDS=30
    fun blocksStart(status: Int): Boolean = status>=BLOCK_START
}

enum class HotAction { NONE,COUNTING,STOP }
data class HotState(val action: HotAction,val remainingSeconds: Int)

/** While recording: severe heat starts a countdown; when it runs out (or the phone gets critical) recording stops and is saved. */
class HotStopTimer(private val seconds: Int=ThermalPolicy.COUNTDOWN_SECONDS) {
    private var startedAt=-1L
    fun reset() { startedAt=-1L }
    fun update(status: Int,nowMs: Long): HotState {
        if(status>=ThermalPolicy.STOP_NOW) return HotState(HotAction.STOP,0)
        if(status<ThermalPolicy.COUNTDOWN) { startedAt=-1L;return HotState(HotAction.NONE,0) }
        if(startedAt<0L) startedAt=nowMs
        val leftMs=seconds*1000L-(nowMs-startedAt)
        if(leftMs<=0L) return HotState(HotAction.STOP,0)
        return HotState(HotAction.COUNTING,ceil(leftMs/1000.0).toInt())
    }
}

/** Cinematic Look amounts. One strength slider scales the look, film grain and the thin vignette together. */
object CinematicMath {
    const val BAR_ASPECT=2.39f
    fun grain(strength: Float): Float = .07f*strength.coerceIn(0f,1f)
    fun vignette(strength: Float): Float = .36f*strength.coerceIn(0f,1f)
    /** Width / height of the picture as the viewer sees it (the camera image turned by [rotation]). */
    fun uprightAspect(width: Int,height: Int,rotation: Int): Float =
        if(rotation%180!=0) height.toFloat()/width.coerceAtLeast(1) else width.toFloat()/height.coerceAtLeast(1)
    /** Share of the picture that stays visible once 2.39:1 bars are added. */
    fun barVisible(aspect: Float): Float = if(aspect<=0f) 1f else min(1f,aspect/BAR_ASPECT)
}

/**
 * The person mask comes back from MediaPipe in the upright orientation the model needs, but the GPU samples it in
 * camera-image orientation. These helpers turn the camera image upright and the mask back again with one shared index map.
 */
object MaskMath {
    fun uprightWidth(w: Int,h: Int,degrees: Int): Int = if(norm(degrees)%180!=0) h else w
    fun uprightHeight(w: Int,h: Int,degrees: Int): Int = if(norm(degrees)%180!=0) w else h
    private fun norm(degrees: Int): Int = ((degrees%360)+360)%360

    /** Index inside the image turned clockwise by [degrees] (0, 90, 180, 270) for the pixel at [col],[row] of a [w]x[h] image. */
    fun uprightIndex(col: Int,row: Int,w: Int,h: Int,degrees: Int): Int = when(norm(degrees)) {
        90 -> col*h+(h-1-row)
        180 -> (h-1-row)*w+(w-1-col)
        270 -> (w-1-col)*h+row
        else -> row*w+col
    }

    /**
     * Turns the upright confidence mask ([maskW]x[maskH], values 0..1) into camera-image orientation, blends it with the
     * previous frame's mask kept in [state] (weight [keep] on the old one, which calms flicker) and returns the
     * share of the frame that is clearly a person (0..1). [state] holds one byte per camera pixel and is updated in place.
     */
    fun toCameraSpace(mask: FloatBuffer,maskW: Int,maskH: Int,w: Int,h: Int,degrees: Int,state: ByteArray,keep: Float): Float {
        val uw=uprightWidth(w,h,degrees);val uh=uprightHeight(w,h,degrees)
        val same=maskW==uw && maskH==uh
        var hit=0
        for(row in 0 until h) {
            for(col in 0 until w) {
                val up=uprightIndex(col,row,w,h,degrees)
                val raw=if(same) mask.get(up) else mask.get(((up/uw)*maskH/uh)*maskW+((up%uw)*maskW/uw))
                val fresh=raw.coerceIn(0f,1f)*255f
                val at=row*w+col
                val old=state[at].toInt() and 0xff
                val blended=(old*keep+fresh*(1f-keep)+.5f).toInt().coerceIn(0,255)
                state[at]=blended.toByte()
                if(blended>127) hit++
            }
        }
        return if(w*h>0) hit.toFloat()/(w*h) else 0f
    }

    /** 0 when nobody is in frame (the picture stays sharp), 1 once a person covers more than about 1.6% of it. */
    fun presenceTarget(coverage: Float): Float {
        val t=((coverage-.004f)/(.016f-.004f)).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
}
