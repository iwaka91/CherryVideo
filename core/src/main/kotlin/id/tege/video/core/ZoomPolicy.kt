package id.tege.video.core

import kotlin.math.abs
import kotlin.math.roundToInt

object ZoomPolicy {
    fun clamp(value: Float,min: Float,max: Float): Float =
        (if(value.isFinite()) value else 1f).coerceIn(min,max)
    /** Camera2 crop coordinates include the active array's nonzero origin. */
    fun crop(left: Int,top: Int,right: Int,bottom: Int,ratio: Float): IntArray {
        require(right>left && bottom>top && ratio.isFinite() && ratio>=1f)
        val w=((right-left)/ratio).roundToInt().coerceIn(1,right-left)
        val h=((bottom-top)/ratio).roundToInt().coerceIn(1,bottom-top)
        val x=left+(right-left-w)/2;val y=top+(bottom-top-h)/2
        return intArrayOf(x,y,x+w,y+h)
    }
    /**
     * Shortcut ratios for the rear camera: the widest ratio when it is below 1x (for example 0.6x),
     * 1x, and 2x. Only ratios the camera really supports are returned; the front camera gets none.
     */
    fun presets(min: Float,max: Float,front: Boolean): List<Float> {
        if(front || !min.isFinite() || !max.isFinite() || max<=min+.01f) return emptyList()
        val list=ArrayList<Float>()
        if(min<.95f) list.add(min)
        if(min<=1f && max>=1f) list.add(1f)
        if(max>=2f) list.add(2f)
        return list.distinct()
    }
    /** "0.6×", "1×", "2.5×". */
    fun label(value: Float): String {
        val rounded=(value*10f).roundToInt()/10f
        return if(abs(rounded-rounded.roundToInt())<.001f) "${rounded.roundToInt()}×" else "$rounded×"
    }
    /** True when [value] sits on one of the shortcut ratios (within [tolerance]). */
    fun matchesPreset(value: Float,presets: List<Float>,tolerance: Float=.05f): Float? =
        presets.firstOrNull { abs(it-value)<=tolerance }
}
