package id.tege.video.core

enum class AudioMode(val label: String, val attenuationDb: Int) {
    NATURAL("Natural",0), CLEAR("Suara Jernih",-12), STRONG("Reduksi Kuat",-28);
    val latencyUs: Long get()=if(this==NATURAL) 0L else 10_000L
    companion object { fun fromOrdinal(value: Int)=entries.getOrElse(value) { NATURAL } }
}
