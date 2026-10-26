package id.tege.video.core

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Converts one camera YUV_420_888 frame (planes already copied to arrays) to ARGB pixels, full-range BT.601 (JFIF). */
object YuvToArgb {
    fun convert(y: ByteArray,yRowStride: Int,u: ByteArray,v: ByteArray,uvRowStride: Int,uvPixelStride: Int,width: Int,height: Int,out: IntArray) {
        require(out.size>=width*height) { "Output terlalu kecil." }
        var o=0
        for(row in 0 until height) {
            val yBase=row*yRowStride
            val uvBase=(row shr 1)*uvRowStride
            for(col in 0 until width) {
                val yy=y[yBase+col].toInt() and 0xff
                val uvIndex=uvBase+(col shr 1)*uvPixelStride
                val cb=(u[uvIndex].toInt() and 0xff)-128
                val cr=(v[uvIndex].toInt() and 0xff)-128
                // Fixed point, scale 1024: 1.402, 0.344136, 0.714136, 1.772
                val r=yy+((1436*cr) shr 10)
                val g=yy-((352*cb+731*cr) shr 10)
                val b=yy+((1815*cb) shr 10)
                out[o++]=(0xff shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
    }
    /** Same colour maths as [convert], but writes the picture turned clockwise by [degrees] so a person is upright for the model. */
    fun convertUpright(y: ByteArray,yRowStride: Int,u: ByteArray,v: ByteArray,uvRowStride: Int,uvPixelStride: Int,width: Int,height: Int,degrees: Int,out: IntArray) {
        require(out.size>=width*height) { "Output terlalu kecil." }
        for(row in 0 until height) {
            val yBase=row*yRowStride
            val uvBase=(row shr 1)*uvRowStride
            for(col in 0 until width) {
                val yy=y[yBase+col].toInt() and 0xff
                val uvIndex=uvBase+(col shr 1)*uvPixelStride
                val cb=(u[uvIndex].toInt() and 0xff)-128
                val cr=(v[uvIndex].toInt() and 0xff)-128
                val r=yy+((1436*cr) shr 10)
                val g=yy-((352*cb+731*cr) shr 10)
                val b=yy+((1815*cb) shr 10)
                out[MaskMath.uprightIndex(col,row,width,height,degrees)]=(0xff shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
    }
    private fun clamp(x: Int)=if(x<0) 0 else if(x>255) 255 else x
}

/** Mean and percentiles of measured durations, in milliseconds. */
data class TimingStats(val count: Int,val mean: Double,val p50: Double,val p90: Double,val max: Double) {
    companion object {
        fun of(values: List<Double>): TimingStats {
            if(values.isEmpty()) return TimingStats(0,0.0,0.0,0.0,0.0)
            val sorted=values.sorted()
            fun pct(p: Double)=sorted[(ceil(p*sorted.size).toInt()-1).coerceIn(0,sorted.size-1)]
            return TimingStats(sorted.size,sorted.average(),pct(.5),pct(.9),sorted.last())
        }
    }
}

/** Plain-language first assessment of whether person segmentation can keep up with 30 fps video. */
object BokehAssessment {
    fun text(processedFps: Double,cameraFps: Double,p90TotalMs: Double): String {
        val target=if(cameraFps>1.0) cameraFps else 30.0
        val fps=processedFps.roundToInt()
        return when {
            processedFps>=target*.9 && p90TotalMs<=33.0 -> "cukup cepat: $fps FPS diproses, mengimbangi kamera ${target.roundToInt()} fps."
            processedFps>=15.0 -> "belum mengimbangi: $fps FPS diproses dari ${target.roundToInt()} fps kamera. Bokeh langsung perlu diproses tiap 2 gambar atau model lebih ringan."
            processedFps>0.0 -> "terlalu lambat: hanya $fps FPS diproses."
            else -> "tidak ada gambar yang berhasil diproses."
        }
    }
}
