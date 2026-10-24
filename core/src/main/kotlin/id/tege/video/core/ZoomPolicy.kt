package id.tege.video.core

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
}
