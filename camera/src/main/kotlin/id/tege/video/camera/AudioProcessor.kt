package id.tege.video.camera

import id.tege.video.core.AudioMode
import java.nio.ByteBuffer

/** 48kHz, 10ms frames. Speex WOLA adds exactly one frame of signal delay.
 * The recorder compensates PTS and drains one padded tail frame before AAC EOS. */
class AudioProcessor(val mode: AudioMode,private val channels: Int): AutoCloseable {
    private var handle=0L
    init {
        require(channels in 1..2)
        if(mode!=AudioMode.NATURAL) {
            check(available) { "Pengolah audio tidak tersedia untuk perangkat ini. Pilih Natural." }
            handle=create(channels,mode.attenuationDb)
            check(handle!=0L) { "Pengolah audio tidak dapat disiapkan." }
        }
    }
    fun process(buffer: ByteBuffer,frames: Int) {
        require(frames in 1..FRAME && buffer.isDirect && buffer.capacity()>=FRAME*channels*2)
        if(mode==AudioMode.NATURAL) return
        check(handle!=0L)
        // Only the final short read is padded. All ordinary blocks are exactly 10ms.
        for(i in frames*channels*2 until FRAME*channels*2) buffer.put(i,0)
        check(processNative(handle,buffer,channels)) { "Pengolahan audio gagal." }
    }
    override fun close() { if(handle!=0L) { destroy(handle);handle=0L } }
    private external fun create(channels: Int,db: Int): Long
    private external fun processNative(handle: Long,buffer: ByteBuffer,channels: Int): Boolean
    private external fun destroy(handle: Long)
    companion object {
        const val FRAME=480
        val available=runCatching { System.loadLibrary("cherry_audio");true }.getOrDefault(false)
    }
}
