package id.tege.video.core

/** Which way a camera faces. */
enum class CamFacing { BACK, FRONT, OTHER }

/** One camera the phone says can run together with others. [maxW] x [maxH] is the biggest picture it is promised at the same time, or 0 when unknown. */
data class ConcurrentCam(val id: String, val facing: CamFacing, val maxW: Int, val maxH: Int)

enum class DualState { SUPPORTED, LIMITED, NOT_SUPPORTED, UNKNOWN }

data class DualCameraResult(val state: DualState, val title: String, val lines: List<String>)

/**
 * Judges whether front and back camera can run at the same time.
 * [apiOk] is false below Android 11, [featureFlag] is the phone's own "concurrent camera" flag,
 * [groups] are the sets of cameras the phone allows together (each inner list is one allowed set).
 */
object DualCameraCheck {
    /** Below this height (px) the shared picture is called small. */
    const val SMALL_H = 720

    fun evaluate(apiOk: Boolean, featureFlag: Boolean, groups: List<List<ConcurrentCam>>): DualCameraResult {
        if (!apiOk) return DualCameraResult(DualState.NOT_SUPPORTED, "Tidak didukung",
            listOf("Android di HP ini terlalu lama untuk dua kamera sekaligus (perlu Android 11 ke atas)."))
        val pairs = groups.filter { g -> g.any { it.facing == CamFacing.FRONT } && g.any { it.facing == CamFacing.BACK } }
        if (groups.isEmpty() && !featureFlag) return DualCameraResult(DualState.NOT_SUPPORTED, "Tidak didukung",
            listOf("HP ini tidak menyatakan dukungan kamera bersamaan, dan tidak ada pasangan kamera yang diizinkan."))
        if (pairs.isEmpty()) return DualCameraResult(DualState.NOT_SUPPORTED, "Tidak didukung untuk depan + belakang",
            listOf("Ada ${groups.size} kelompok kamera bersamaan, tetapi tidak ada yang memuat kamera depan dan belakang sekaligus.") +
                (if (featureFlag) emptyList() else listOf("HP juga tidak menyatakan dukungan kamera bersamaan.")))
        val front = pairs.flatten().filter { it.facing == CamFacing.FRONT }.distinctBy { it.id }
        val back = pairs.flatten().filter { it.facing == CamFacing.BACK }.distinctBy { it.id }
        val bestOf = pairs.maxOfOrNull { g -> g.filter { it.facing != CamFacing.OTHER }.minOfOrNull { it.maxH } ?: 0 } ?: 0
        val lines = mutableListOf<String>()
        lines += "Kamera belakang yang bisa dipasangkan: ${back.joinToString { it.id }}"
        lines += "Kamera depan yang bisa dipasangkan: ${front.joinToString { it.id }}"
        lines += "Jumlah pasangan depan + belakang: ${pairs.size}"
        val best = pairs.firstOrNull { g -> g.filter { it.facing != CamFacing.OTHER }.minOfOrNull { it.maxH } == bestOf }
        if (bestOf > 0 && best != null) {
            val f = best.first { it.facing == CamFacing.FRONT }; val b = best.first { it.facing == CamFacing.BACK }
            lines += "Ukuran terjamin bersamaan: depan ${f.maxW}x${f.maxH}, belakang ${b.maxW}x${b.maxH}"
        } else lines += "Ukuran terjamin bersamaan: tidak dilaporkan HP."
        if (!featureFlag) lines += "Catatan: HP tidak menyalakan penanda resmi kamera bersamaan, padahal daftar pasangan ada. Perlu dicoba langsung."
        val limited = bestOf < SMALL_H
        return if (limited) DualCameraResult(DualState.LIMITED, "Didukung, tetapi terbatas",
            lines + "Ukuran gambar kecil atau tidak diketahui; hasil video dual kamera bisa lebih rendah dari kamera biasa.")
        else DualCameraResult(DualState.SUPPORTED, "Didukung", lines)
    }

    /** Plain text for the dialog. */
    fun text(r: DualCameraResult): String = (listOf(r.title) + r.lines).joinToString("\n")
}
