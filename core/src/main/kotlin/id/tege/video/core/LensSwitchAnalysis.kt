package id.tege.video.core

import kotlin.math.abs

/** One observation from a zoom sweep: what the camera reported after a zoom ratio was requested. */
data class ZoomObservation(val requested: Float,val reported: Float?,val physicalId: String?,val focalMm: Float?,val frames: Int)

/**
 * Decides, from measured results only, whether the camera switched lenses while zooming.
 * A switch is a different active physical id, or a focal length that moves by more than 15 % from the first
 * observation. Observations without frames are ignored because nothing was measured.
 */
object LensSwitchAnalysis {
    data class Verdict(val switched: Boolean,val firstSwitchZoom: Float?,val text: String)

    fun analyze(observations: List<ZoomObservation>): Verdict {
        val measured=observations.filter { it.frames>0 }
        if(measured.size<2) return Verdict(false,null,"Data zoom tidak cukup untuk menyimpulkan perpindahan lensa.")
        val base=measured.first()
        val change=measured.drop(1).firstOrNull { o ->
            val idChanged=base.physicalId!=null && o.physicalId!=null && o.physicalId!=base.physicalId
            val focalChanged=base.focalMm!=null && o.focalMm!=null && base.focalMm>0f && abs(o.focalMm-base.focalMm)/base.focalMm>.15f
            idChanged || focalChanged
        }
        return if(change==null) Verdict(false,null,
            "Selama uji ini kamera tidak berpindah lensa: lensa aktif dan panjang fokus tetap pada semua rasio zoom. Zoom hanya memotong gambar lensa yang sama.")
        else Verdict(true,change.requested,
            "Kamera berpindah lensa mulai zoom ${ZoomPolicy.label(change.requested)} (lensa ${change.physicalId ?: "?"}, fokus ${change.focalMm ?: "?"} mm).")
    }
}
