package id.tege.video.core

/**
 * Lightweight real-time SDR enhancement for the existing OpenGL ES camera path.
 *
 * Values control an original single-pass shader. No RAW, upscaling, multi-frame
 * temporal denoise, or third-party image engine is bundled.
 */
enum class EnhancementPreset(
    val label: String,
    val denoise: Float,
    val tone: Float,
    val detail: Float,
) {
    RINGAN("Ringan", 0.30f, 0.25f, 0.18f),
    SEIMBANG("Seimbang", 0.55f, 0.45f, 0.30f),
    KUAT("Kuat", 0.80f, 0.60f, 0.40f);

    companion object {
        fun fromName(value: String?): EnhancementPreset =
            entries.firstOrNull { it.name == value } ?: SEIMBANG
    }
}

/** Conservative until actual Xiaomi 13T and USB UVC throughput is measured. */
object EnhancementPolicy {
    fun reason(width: Int, height: Int, fps: Int, highSpeed: Boolean): String? = when {
        highSpeed || fps > 30 -> "Enhancement belum tersedia untuk 60 fps / high-speed. Pilih 30 fps."
        width <= 0 || height <= 0 || fps <= 0 -> "Profil video belum valid."
        width.toLong() * height > 1920L * 1080L ->
            "Enhancement sementara tersedia hingga 1080p. Pilih 1080p/30 atau 720p/30."
        else -> null
    }

    fun eligible(width: Int, height: Int, fps: Int, highSpeed: Boolean): Boolean =
        reason(width, height, fps, highSpeed) == null
}
