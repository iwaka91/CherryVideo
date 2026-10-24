package id.tege.video.camera

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Process
import android.view.Surface
import id.tege.video.core.VideoProfile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/** Surface video and PCM audio share one MONOTONIC epoch and one muxer owner.
 * Unlike MediaRecorder's short stop timeout, EOS is drained explicitly before releasing codecs. */
@SuppressLint("MissingPermission")
class SurfaceRecorder(
    private val context: Context,
    private val file: File,
    private val profile: VideoProfile,
    private val hevc: Boolean,
    private val audioSource: Int,
    private val orientation: Int,
    private val maxBytes: Long,
    private val onLimit: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private var video: MediaCodec?=null
    private var audio: MediaCodec?=null
    private var microphone: AudioRecord?=null
    private var muxer: MediaMuxer?=null
    lateinit var surface: Surface; private set
    private var drainThread: Thread?=null
    private var audioThread: Thread?=null
    private val done=CountDownLatch(1)
    @Volatile private var draining=false
    @Volatile private var capturing=false
    @Volatile private var failure: Throwable?=null
    @Volatile private var peak=0
    @Volatile var capturedAudioFrames=0L; private set
    private var epochUs=0L
    private var audioStartUs=0L
    private var channels=2
    private var started=false
    private var stopped=false
    val maxAmplitude: Int get()=peak

    fun prepare() {
        try {
            val mime=if(hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            video=MediaCodec.createEncoderByType(mime).also { video=it }.apply {
                configure(MediaFormat.createVideoFormat(mime,profile.width,profile.height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE,profile.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE,profile.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                    setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709)
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                    setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)
                },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface=createInputSurface()
            }
            if(audioSource!=0) {
                microphone=runCatching { makeMicrophone(2) }.getOrNull()
                if(microphone?.state!=AudioRecord.STATE_INITIALIZED) {
                    microphone?.release();channels=1;microphone=makeMicrophone(1)
                }
                check(microphone?.state==AudioRecord.STATE_INITIALIZED) { "Mikrofon tidak dapat dibuka." }
                if(audioSource==2) {
                    val external=context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
                        it.type in setOf(AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADSET)
                    }
                    check(external!=null && microphone!!.setPreferredDevice(external)) { "Mikrofon kabel/USB tidak tersedia atau ditolak." }
                }
                audio=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also { audio=it }.apply {
                    configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,48_000,channels).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE,192_000)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16_384)
                    },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
            }
            muxer=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply { setOrientationHint(orientation) }
        } catch(e: Throwable) { release();throw e }
    }
    private fun makeMicrophone(count: Int): AudioRecord {
        val mask=if(count==2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minimum=AudioRecord.getMinBufferSize(48_000,mask,AudioFormat.ENCODING_PCM_16BIT)
        check(minimum>0) { "Format mikrofon tidak didukung." }
        return AudioRecord(if(audioSource==2) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.CAMCORDER,
            48_000,mask,AudioFormat.ENCODING_PCM_16BIT,max(minimum*4,48_000*count*2/5))
    }
    fun start() {
        check(!started)
        video!!.start();audio?.start()
        epochUs=System.nanoTime()/1000
        microphone?.let { it.startRecording();check(it.recordingState==AudioRecord.RECORDSTATE_RECORDING);audioStartUs=System.nanoTime()/1000 }
        started=true;draining=true;capturing=microphone!=null
        drainThread=Thread({ drain() },"Cherry-Muxer").apply { start() }
        if(capturing) audioThread=Thread({ captureAudio() },"Cherry-Audio").apply { start() }
    }
    private fun fail(e: Throwable) {
        if(failure==null) { failure=e;onFailure(e.message ?: "Codec gagal.") }
    }
    private fun captureAudio() {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val codec=audio ?: return
        val input=microphone ?: return
        var frames=0L
        var nextUs=audioStartUs
        try {
            while(capturing) {
                val index=codec.dequeueInputBuffer(10_000)
                if(index<0) continue
                val buffer=checkNotNull(codec.getInputBuffer(index)).apply { clear();order(ByteOrder.LITTLE_ENDIAN) }
                val requested=minOf(buffer.remaining(),4096*channels).let { it-it%(channels*2) }
                val count=input.read(buffer,requested,AudioRecord.READ_BLOCKING)
                if(count<=0) {
                    if(capturing) error("Mikrofon berhenti mengirim data: $count")
                    codec.queueInputBuffer(index,0,0,nextUs,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    return
                }
                var amplitude=0
                for(i in 0 until count-1 step 2) amplitude=max(amplitude,abs(buffer.getShort(i).toInt()))
                peak=amplitude
                // Sample-count timing is anchored to the microphone start on the same clock.
                val pts=audioStartUs+frames*1_000_000/48_000
                codec.queueInputBuffer(index,0,count,pts,0)
                frames+=count/(channels*2);capturedAudioFrames=frames;nextUs=audioStartUs+frames*1_000_000/48_000
            }
            val deadline=System.nanoTime()+3_000_000_000L
            while(System.nanoTime()<deadline) {
                val index=codec.dequeueInputBuffer(10_000)
                if(index>=0) { codec.queueInputBuffer(index,0,0,nextUs,MediaCodec.BUFFER_FLAG_END_OF_STREAM);return }
            }
            error("Audio EOS tidak tersedia.")
        } catch(e: Throwable) { if(draining) fail(e) }
    }
    private data class Packet(val track: Int,val bytes: ByteArray,val pts: Long,val flags: Int)
    private fun drain() {
        val pending=ArrayDeque<Packet>()
        var pendingBytes=0L
        val codecs=listOfNotNull(video,audio)
        val tracks=IntArray(codecs.size) { -1 }
        val ended=BooleanArray(codecs.size)
        val info=MediaCodec.BufferInfo()
        var writing=false
        var written=0L
        var limitSent=false
        fun write(packet: Packet) {
            if(packet.pts<epochUs) return
            val sample=MediaCodec.BufferInfo().apply { set(0,packet.bytes.size,packet.pts-epochUs,packet.flags) }
            muxer!!.writeSampleData(packet.track,ByteBuffer.wrap(packet.bytes),sample)
            written+=packet.bytes.size
            if(written>=maxBytes && !limitSent) { limitSent=true;onLimit() }
        }
        try {
            while(draining && ended.any { !it }) {
                var progressed=false
                codecs.forEachIndexed { i,codec ->
                    if(ended[i]) return@forEachIndexed
                    when(val index=codec.dequeueOutputBuffer(info,2_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(tracks[i]<0) { "Format codec berubah saat merekam." }
                            tracks[i]=muxer!!.addTrack(codec.outputFormat);progressed=true
                            if(tracks.all { it>=0 }) {
                                muxer!!.start();writing=true
                                while(pending.isNotEmpty()) write(pending.removeFirst())
                                pendingBytes=0
                            }
                        }
                        in 0..Int.MAX_VALUE -> {
                            progressed=true
                            try {
                                if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                                    val data=checkNotNull(codec.getOutputBuffer(index)).apply { position(info.offset);limit(info.offset+info.size) }
                                    val bytes=ByteArray(info.size);data.get(bytes)
                                    val packet=Packet(tracks[i],bytes,info.presentationTimeUs,info.flags)
                                    if(writing) write(packet) else {
                                        pending.addLast(packet);pendingBytes+=bytes.size
                                        check(pendingBytes<32L*1024*1024) { "Format codec terlalu lama menunggu." }
                                    }
                                }
                                if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0) ended[i]=true
                            } finally { codec.releaseOutputBuffer(index,false) }
                        }
                    }
                }
                if(!progressed) Thread.yield()
            }
            check(ended.all { it }) { "Codec belum mengirim semua frame EOS." }
            check(writing) { "Track rekaman belum siap." }
        } catch(e: Throwable) { fail(e) }
        finally {
            if(writing) runCatching { muxer?.stop() }.onFailure { fail(it) }
            done.countDown()
        }
    }
    fun stop() {
        check(started && !stopped)
        capturing=false
        runCatching { microphone?.stop() }
        audioThread?.join(4_000)
        check(audioThread?.isAlive!=true) { "Mikrofon belum berhenti." }
        video!!.signalEndOfInputStream()
        check(done.await(20,TimeUnit.SECONDS)) { "Codec belum selesai memproses antrean frame." }
        stopped=true
        failure?.let { throw IllegalStateException("Finalisasi codec gagal: ${it.message}",it) }
    }
    fun release() {
        capturing=false;runCatching { microphone?.stop() }
        audioThread?.join(4_000)
        draining=false;drainThread?.join(4_000)
        runCatching { microphone?.release() };microphone=null
        listOfNotNull(video,audio).forEach { runCatching { it.stop() };runCatching { it.release() } }
        video=null;audio=null
        runCatching { muxer?.release() };muxer=null
        if(::surface.isInitialized) surface.release()
    }
}
