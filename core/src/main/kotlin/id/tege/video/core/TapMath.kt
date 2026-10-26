package id.tege.video.core

import kotlin.math.roundToInt

/** Pure maths for tap-to-focus and drag-to-brighten. Kept free of Android types so it can be tested on the JVM. */
object TapMath {
    /**
     * Display point (0..1 inside the picture, as seen on screen) to the matching point (0..1) in the un-rotated,
     * un-mirrored camera image. [rotation] is the clockwise angle that turns the camera image upright on screen.
     * The front preview is mirrored, so that mirror is undone first.
     */
    fun toSensor(u: Float,v: Float,rotation: Int,mirrored: Boolean): Pair<Float,Float> {
        val x=(if(mirrored) 1f-u else u).coerceIn(0f,1f)
        val y=v.coerceIn(0f,1f)
        return when(((rotation%360)+360)%360) {
            90 -> y to 1f-x
            180 -> 1f-x to 1f-y
            270 -> 1f-y to x
            else -> x to y
        }
    }
    /** A point inside the visible (zoomed) area to a point (0..1) of the whole active array. [crop] is left,top,right,bottom. */
    fun toActive(su: Float,sv: Float,crop: IntArray,activeLeft: Int,activeTop: Int,activeRight: Int,activeBottom: Int): Pair<Float,Float> {
        val w=(activeRight-activeLeft).coerceAtLeast(1).toFloat();val h=(activeBottom-activeTop).coerceAtLeast(1).toFloat()
        val x=crop[0]+su*(crop[2]-crop[0]);val y=crop[1]+sv*(crop[3]-crop[1])
        return ((x-activeLeft)/w).coerceIn(0f,1f) to ((y-activeTop)/h).coerceIn(0f,1f)
    }
    /** Square metering region (left,top,right,bottom) around a point (0..1) of the active array, kept inside the array. */
    fun region(px: Float,py: Float,left: Int,top: Int,right: Int,bottom: Int,fraction: Float=.14f): IntArray {
        val w=right-left;val h=bottom-top
        val size=(minOf(w,h)*fraction).roundToInt().coerceAtLeast(8)
        val cx=left+(px*w).roundToInt();val cy=top+(py*h).roundToInt()
        val l=(cx-size/2).coerceIn(left,right-size);val t=(cy-size/2).coerceIn(top,bottom-size)
        return intArrayOf(l,t,l+size,t+size)
    }
    /** Dragging up by [dragPx] raises the exposure compensation; a drag of the full picture height spans the whole range. */
    fun evFromDrag(startEv: Int,dragPx: Float,heightPx: Float,lower: Int,upper: Int): Int {
        if(upper<=lower || heightPx<=0f) return startEv.coerceIn(lower,maxOf(lower,upper))
        val steps=(dragPx/heightPx*(upper-lower)*1.4f).roundToInt()
        return (startEv+steps).coerceIn(lower,upper)
    }
}
