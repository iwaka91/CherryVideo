package id.tege.video.core

import java.util.Locale
import kotlin.math.roundToInt

/** Eight looks for the location stamp that is drawn into video and photos. */
enum class StampTemplate(val label: String, val hint: String) {
    POLOS("Polos", "Dua baris kecil di bawah, huruf tipis"),
    KARTU("Kartu", "Kotak transparan kiri bawah, huruf tegas"),
    BERITA("Berita", "Pita penuh di bawah ala siaran TV"),
    CATATAN("Catatan", "Kertas tempel kanan atas, huruf tulisan tangan"),
    LAPORAN("Laporan", "Blok rapi ala dokumen resmi"),
    JUDUL("Judul Tempat", "Nama tempat besar di kiri atas"),
    POSTER("Poster", "Tulisan besar di tengah bawah"),
    STEMPEL("Stempel", "Cap miring kanan bawah");

    fun next(): StampTemplate { val all = values(); return all[(ordinal + 1) % all.size] }

    companion object {
        /** Unknown or missing names fall back to Kartu. */
        fun of(name: String?): StampTemplate = values().firstOrNull { it.name == name } ?: KARTU
    }
}

/** What the user wants inside the stamp. The activity name has its own checkbox and is not part of this. */
data class StampFields(val place: Boolean = true, val coords: Boolean = true, val date: Boolean = true, val time: Boolean = true) {
    val any: Boolean get() = place || coords || date || time
}

/** A position fix. [ageMs] is how old it is when it is used. */
data class GeoFix(val lat: Double, val lon: Double, val accuracyM: Float?, val ageMs: Long)

enum class LocationState { NO_PERMISSION, LOCATION_OFF, SEARCHING, WEAK, STALE, GOOD }

object LocationStatus {
    /** Worse than this (metres) counts as a weak signal. */
    const val WEAK_ACCURACY_M = 50f
    /** A fix older than this (ms) is reported as old. */
    const val STALE_MS = 120_000L

    fun classify(permission: Boolean, providerOn: Boolean, fix: GeoFix?): LocationState = when {
        !permission -> LocationState.NO_PERMISSION
        fix == null -> if (providerOn) LocationState.SEARCHING else LocationState.LOCATION_OFF
        fix.ageMs > STALE_MS -> LocationState.STALE
        fix.accuracyM != null && fix.accuracyM > WEAK_ACCURACY_M -> LocationState.WEAK
        else -> LocationState.GOOD
    }

    fun message(state: LocationState, fix: GeoFix?): String? = when (state) {
        LocationState.NO_PERMISSION -> "Izin lokasi belum diberikan. Cap lokasi hanya memuat tanggal dan jam."
        LocationState.LOCATION_OFF -> "Lokasi HP mati. Nyalakan Lokasi di pengaturan HP; sementara cap hanya memuat tanggal dan jam."
        LocationState.SEARCHING -> "Mencari sinyal lokasi… tunggu sebentar atau pindah ke tempat terbuka."
        LocationState.WEAK -> "Sinyal lokasi lemah (±${fix?.accuracyM?.roundToInt() ?: 0} m). Koordinat bisa meleset."
        LocationState.STALE -> "Lokasi terakhir sudah ${((fix?.ageMs ?: 0L) / 60_000L).coerceAtLeast(2L)} menit lalu. Cap memakai lokasi itu."
        LocationState.GOOD -> null
    }

    /** Text for the warning banner: a signal problem first, otherwise the internet limit of place names. */
    fun banner(state: LocationState, fix: GeoFix?, fields: StampFields, placeUnavailable: Boolean): String? =
        message(state, fix) ?: if (fields.place && placeUnavailable) "Nama tempat butuh internet. Cap sementara memakai koordinat." else null

    /** A one-time note appears when the state changes; recovery is announced only after a problem. */
    fun shouldNotify(previous: LocationState?, now: LocationState): Boolean =
        previous != now && (now != LocationState.GOOD || (previous != null && previous != LocationState.GOOD))
}

/** Joins an address into a short place name: street, village or district, regency or province. */
object PlaceName {
    fun build(thoroughfare: String?, subLocality: String?, locality: String?, subAdmin: String?, admin: String?): String? {
        fun ok(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Unnamed Road", ignoreCase = true) }
        val parts = listOfNotNull(ok(thoroughfare), ok(subLocality) ?: ok(locality), ok(subAdmin) ?: ok(admin)).distinct()
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
}

/** The lines the stamp templates draw. Any line may be null. */
data class StampContent(val place: String?, val coords: String?, val date: String?, val time: String?, val note: String?) {
    val isEmpty: Boolean get() = place == null && coords == null && date == null && time == null
    /** First part of the place name ("Jl. Tjilik Riwut"), for templates that show it large. */
    val headline: String? get() = place?.substringBefore(", ")
    /** The rest of the place name, or null. */
    val subline: String? get() = place?.takeIf { it.contains(", ") }?.substringAfter(", ")
    /** Date and time joined for one-line use. */
    val dateTime: String? get() = listOfNotNull(date, time).joinToString(" · ").ifEmpty { null }
}

object StampText {
    /** "-3.2891, 114.0142", with the accuracy when the signal is weak. */
    fun coords(fix: GeoFix, weak: Boolean): String {
        val base = String.format(Locale.US, "%.4f, %.4f", fix.lat, fix.lon)
        val acc = fix.accuracyM
        return if (weak && acc != null) "$base ±${acc.roundToInt()} m" else base
    }

    /**
     * Builds what the stamp shows. Without a usable position the stamp says so, never a made-up place.
     * [place] is the looked-up place name, or null when none is known (no internet, no address).
     */
    fun content(fields: StampFields, state: LocationState, fix: GeoFix?, place: String?, date: String, time: String): StampContent {
        val hasFix = fix != null && (state == LocationState.GOOD || state == LocationState.WEAK || state == LocationState.STALE)
        val coordsText = if (hasFix) coords(fix!!, state == LocationState.WEAK) else null
        val wantsPlace = fields.place
        val placeLine: String? = when {
            !wantsPlace && !fields.coords -> null
            !hasFix -> "Lokasi belum didapat"
            wantsPlace -> place?.takeIf { it.isNotBlank() } ?: coordsText
            else -> null
        }
        val coordsLine: String? = when {
            !fields.coords || !hasFix -> null
            placeLine == coordsText -> null
            else -> coordsText
        }
        val note = if (hasFix && state == LocationState.STALE && (wantsPlace || fields.coords))
            "lokasi lama (${((fix!!.ageMs) / 60_000L).coerceAtLeast(2L)} mnt)" else null
        return StampContent(placeLine, coordsLine, date.takeIf { fields.date }, time.takeIf { fields.time }, note)
    }
}
