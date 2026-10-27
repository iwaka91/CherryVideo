package id.tege.video.core

import java.util.Locale
import kotlin.math.pow

/**
 * Live Bokeh settings chosen by the user. [strength] is the blur (0..1, shown as an F value); [sensitivity] how readily a
 * pixel counts as a person (0..1, 0.5 = model default); [smooth] trades quick following for calmer edges; [feather] softens
 * the person edge; [grade] lets the blur grow gradually away from the person; [showMask] tints the person area in the preview only.
 */
data class BokehTuning(
    val strength: Float=BokehLook.DEFAULT_STRENGTH,
    val sensitivity: Float=.5f,
    val smooth: Boolean=true,
    val feather: Float=.55f,
    val grade: Float=.55f,
    val showMask: Boolean=false,
)

enum class BokehPreset(val label: String,val strength: Float,val feather: Float,val grade: Float,val smooth: Boolean) {
    POTRET("Potret",.06f,.55f,.55f,true),
    SINEMATIK("Sinematik",.12f,.75f,.85f,true),
    LEMBUT("Lembut",.03f,.9f,.35f,true);
    fun apply(t: BokehTuning): BokehTuning = t.copy(strength=strength,feather=feather,grade=grade,smooth=smooth)
    companion object { fun of(name: String?): BokehPreset? = entries.firstOrNull { it.name==name } }
}

object BokehLook {
    const val DEFAULT_STRENGTH=.06f
    /** Simulated aperture: blur 0% = F16, 100% = F1.0 (the lens itself has a fixed aperture). */
    fun fNumber(strength: Float): Float = (16.0*(1.0/16.0).pow(strength.coerceIn(0f,1f).toDouble())).toFloat()
    fun fLabel(strength: Float): String {
        val f=fNumber(strength)
        return if(f>=10f) String.format(Locale.US,"F%.0f",f) else String.format(Locale.US,"F%.1f",f)
    }
    /** Mask level that counts as "person": higher sensitivity lowers it (more is kept sharp), lower sensitivity raises it. */
    fun threshold(sensitivity: Float): Float = (.75f-.5f*sensitivity.coerceIn(0f,1f)).coerceIn(.25f,.75f)
    /** Weight of the previous mask when blending in a new one: Halus keeps more (calmer), Cepat follows movement sooner. */
    fun keep(smooth: Boolean): Float = if(smooth) .55f else .2f
    /** Half width of the soft edge around [threshold], in mask units. */
    fun featherWidth(feather: Float): Float = .06f+.3f*feather.coerceIn(0f,1f)
}
