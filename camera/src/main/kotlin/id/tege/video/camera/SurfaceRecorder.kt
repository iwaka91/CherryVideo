package id.tege.video.camera

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AutomaticGainControl
import android.os.Process
import android.view.Surface
import id.tege.video.core.AudioMode
import id.tege.video.core.VideoProfile
import id.tege.video.core.AudioFrameClock
import id.tege.video.core.RecordingStart
import org.json.JSONObject
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
    private val audioMode: AudioMode = AudioMode.NATURAL,
    private val audioDeviceId: Int = 0,
) {
    private var video: MediaCodec?=null
    private var audio: MediaCodec?=null
    private var microphone: AudioRecord?=null
    private var route: AudioRoute?=null
    private var processor: AudioProcessor?=null
    private var systemNs: NoiseSuppressor?=null
    private var systemAgc: AutomaticGainControl?=null
    @Volatile private var actualInput=JSONObject()
    private var microphoneInfo=org.json.JSONArray()
    private var nsState="unavailable"
    private var agcState="unavailable"
    private val inputSquares=DoubleArray(2)
    private val outputSquares=DoubleArray(2)
    private var inputMeterFrames=0L
    private var outputMeterFrames=0L
    private fun meter(buffer: ByteBuffer,frames: Int,squares: DoubleArray) {
        for(i in 0 until frames) for(c in 0 until channels) {
            val sample=buffer.getShort((i*channels+c)*2).toDouble()/32768.0
            squares[c]+=sample*sample
        }
    }
    private fun levels(squares: DoubleArray,frames: Long)=org.json.JSONArray((0 until channels).map {
        if(frames==0L) -120.0 else 10*kotlin.math.log10((squares[it]/frames).coerceAtLeast(1e-12))
    })
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
    var epochNs=0L; private set
    @Volatile private var audioClockSource="none"
    @Volatile private var firstAudioInputUs: Long?=null
    private val startup=RecordingStart(audioSource!=0)
    val timingReport: JSONObject get()=JSONObject()
        .put("clock","MONOTONIC, shared relative epoch")
        .put("audioClockSource",audioClockSource).put("firstAudioInputUs",firstAudioInputUs ?: JSONObject.NULL)
        .put("firstVideoOutputUs",startup.firstVideoUs ?: JSONObject.NULL)
        .put("firstAudioOutputUs",startup.firstAudioUs ?: JSONObject.NULL)
        .put("startupTrimUs",startup.originUs ?: JSONObject.NULL)
        .put("audioMode",audioMode.name).put("audioModeLabel",audioMode.label)
        .put("processor",if(audioMode==AudioMode.NATURAL) "Bypass tambahan aplikasi" else "SpeexDSP")
        .put("maxNoiseAttenuationDb",audioMode.attenuationDb).put("processorLatencyUs",audioMode.latencyUs)
        .put("channels",channels).put("sampleRate",48000).put("actualInput",actualInput)
        .put("activeMicrophones",microphoneInfo).put("systemNoiseSuppressor",nsState).put("systemAutomaticGainControl",agcState)
        .put("inputRmsDbfs",levels(inputSquares,inputMeterFrames)).put("outputRmsDbfs",levels(outputSquares,outputMeterFrames))
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
                    setInteger(MediaFormat.KEY_MAX_B_FRAMES,0)
                    setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709)
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                    setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)
                },null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface=createInputSurface()
            }
            if(audioSource!=0) {
                route=AudioRoute(context,audioSource==2,audioDeviceId)
                route!!.prepare()
                microphone=runCatching { makeMicrophone(2) }.getOrNull()
                if(microphone?.state!=AudioRecord.STATE_INITIALIZED) {
                    microphone?.release();channels=1;microphone=makeMicrophone(1)
                }
                check(microphone?.state==AudioRecord.STATE_INITIALIZED) { "Mikrofon tidak dapat dibuka." }
                route!!.apply(microphone!!)
                processor=AudioProcessor(audioMode,channels)
                // Avoid stacking OEM gain/noise effects over the selected application processor.
                systemNs=runCatching { if(NoiseSuppressor.isAvailable()) NoiseSuppressor.create(microphone!!.audioSessionId) else null }.getOrNull()
                systemAgc=runCatching { if(AutomaticGainControl.isAvailable()) AutomaticGainControl.create(microphone!!.audioSessionId) else null }.getOrNull()
                systemNs?.let { nsState=runCatching { it.enabled=false;if(it.enabled) "tetap aktif (sistem)" else "nonaktif" }.getOrDefault("tidak dapat dikendalikan") }
                systemAgc?.let { agcState=runCatching { it.enabled=false;if(it.enabled) "tetap aktif (sistem)" else "nonaktif" }.getOrDefault("tidak dapat dikendalikan") }
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
        return AudioRecord(route?.source ?: MediaRecorder.AudioSource.CAMCORDER,
            48_000,mask,AudioFormat.ENCODING_PCM_16BIT,max(minimum*4,48_000*count*2/5))
    }
    fun start() {
        check(!started)
        video!!.start();audio?.start()
        epochNs=System.nanoTime()
        microphone?.let { it.startRecording();check(it.recordingState==AudioRecord.RECORDSTATE_RECORDING) }
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
        var nextUs=0L
        val clock=AudioFrameClock()
        val timestamp=AudioTimestamp()
        var clockReady=false
        var routeVerified=false
        var routedDeviceId: Int?=null
        try {
            while(capturing) {
                val index=codec.dequeueInputBuffer(10_000)
                if(index<0) continue
                val buffer=checkNotNull(codec.getInputBuffer(index)).apply { clear();order(ByteOrder.LITTLE_ENDIAN) }
                val requested=AudioProcessor.FRAME*channels*2
                check(buffer.capacity()>=requested)
                var count=0
                while(count<requested) {
                    val destination=buffer.duplicate().apply { position(count);limit(requested) }.slice()
                    val read=input.read(destination,requested-count,AudioRecord.READ_BLOCKING)
                    if(read<=0) {
                        if(capturing) error("Mikrofon berhenti mengirim data: $read")
                        break
                    }
                    count+=read
                    if(!capturing) break
                }
                if(count<=0) { codec.queueInputBuffer(index,0,0,nextUs,0);break }
                check(count%(channels*2)==0) { "PCM mikrofon tidak utuh." }
                if(!routeVerified || input.routedDevice?.id!=routedDeviceId) {
                    route!!.verify(input.routedDevice);actualInput=AudioInputs.report(input.routedDevice)
                    microphoneInfo=runCatching { org.json.JSONArray(input.activeMicrophones.map { m ->
                        JSONObject().put("id",m.id).put("description",m.description).put("type",m.type)
                    }) }.getOrDefault(org.json.JSONArray())
                    routedDeviceId=input.routedDevice?.id;routeVerified=true
                }
                var amplitude=0
                for(i in 0 until count-1 step 2) amplitude=max(amplitude,abs(buffer.getShort(i).toInt()))
                peak=amplitude
                val captured=count/(channels*2)
                meter(buffer,captured,inputSquares);inputMeterFrames+=captured
                val frameCount=if(audioMode==AudioMode.NATURAL) captured else AudioProcessor.FRAME
                if(!clockReady) {
                    if(input.getTimestamp(timestamp,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS && timestamp.nanoTime>0 && timestamp.framePosition>0) {
                        clock.anchor(timestamp.framePosition,timestamp.nanoTime);audioClockSource="AudioRecord MONOTONIC"
                    } else {
                        // Older HALs may not supply a timestamp yet. Approximate the end of
                        // the first blocking read, never the return time of startRecording().
                        clock.anchor(frames+captured,System.nanoTime());audioClockSource="first PCM read completion"
                    }
                    clockReady=true
                }
                processor!!.process(buffer,captured)
                meter(buffer,frameCount,outputSquares);outputMeterFrames+=frameCount
                val pts=clock.timeUs(frames,epochNs)-audioMode.latencyUs
                if(firstAudioInputUs==null) firstAudioInputUs=pts
                // Trim PCM preceding the shared epoch before AAC encoding, not by
                // dropping arbitrary encoded video frames (which can lose the first IDR).
                val skip=if(pts<0) minOf(frameCount,((-pts*48_000+999_999)/1_000_000).toInt()) else 0
                val offset=skip*channels*2
                val kept=frameCount*channels*2-offset
                val queuedUs=(clock.timeUs(frames+skip,epochNs)-audioMode.latencyUs).coerceAtLeast(0L)
                codec.queueInputBuffer(index,offset,kept,queuedUs,0)
                frames+=frameCount;capturedAudioFrames+=captured;nextUs=(clock.timeUs(frames,epochNs)-audioMode.latencyUs).coerceAtLeast(0L)
            }
            // Speex returns the previous 10ms window. Flush that tail before AAC EOS,
            // with timestamp compensation, rather than silently cutting the last syllable.
            if(audioMode!=AudioMode.NATURAL && frames>0) {
                val deadline=System.nanoTime()+3_000_000_000L
                var index=-1
                while(index<0 && System.nanoTime()<deadline) index=codec.dequeueInputBuffer(10_000)
                check(index>=0) { "Audio tail tidak tersedia." }
                val buffer=checkNotNull(codec.getInputBuffer(index)).apply { clear();order(ByteOrder.LITTLE_ENDIAN) }
                repeat(AudioProcessor.FRAME*channels*2) { buffer.put(it,0) }
                processor!!.process(buffer,AudioProcessor.FRAME)
                meter(buffer,AudioProcessor.FRAME,outputSquares);outputMeterFrames+=AudioProcessor.FRAME
                codec.queueInputBuffer(index,0,AudioProcessor.FRAME*channels*2,nextUs,0)
                nextUs=(clock.timeUs(frames,epochNs)).coerceAtLeast(0L)
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
        val sampleCounts=IntArray(codecs.size)
        fun write(packet: Packet) {
            val pts=startup.outputTimeUs(packet.pts) ?: return
            val sample=MediaCodec.BufferInfo().apply { set(0,packet.bytes.size,pts,packet.flags) }
            muxer!!.writeSampleData(tracks[packet.track],ByteBuffer.wrap(packet.bytes),sample)
            sampleCounts[packet.track]++
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
                            if(tracks.all { it>=0 }) { muxer!!.start();writing=true }
                        }
                        in 0..Int.MAX_VALUE -> {
                            progressed=true
                            try {
                                if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                                    val data=checkNotNull(codec.getOutputBuffer(index)).apply { position(info.offset);limit(info.offset+info.size) }
                                    val bytes=ByteArray(info.size);data.get(bytes)
                                    val packet=Packet(i,bytes,info.presentationTimeUs,info.flags)
                                    startup.observe(i==0,packet.pts,packet.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME!=0)
                                    if(writing && startup.originUs!=null) {
                                        while(pending.isNotEmpty()) write(pending.removeFirst())
                                        pendingBytes=0
                                        write(packet)
                                    } else {
                                        pending.addLast(packet);pendingBytes+=bytes.size
                                        check(pendingBytes<32L*1024*1024) { "Awal track rekaman terlalu lama menunggu: $timingReport" }
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
            check(writing && startup.originUs!=null && sampleCounts[0]>=2 && (audio==null || sampleCounts[1]>0)) {
                "Belum ada audio/video yang tumpang tindih pada keyframe valid: $timingReport"
            }
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
        runCatching { processor?.close() };processor=null
        runCatching { systemNs?.release() };systemNs=null
        runCatching { systemAgc?.release() };systemAgc=null
        runCatching { microphone?.release() };microphone=null
        runCatching { route?.close() };route=null
        listOfNotNull(video,audio).forEach { runCatching { it.stop() };runCatching { it.release() } }
        video=null;audio=null
        runCatching { muxer?.release() };muxer=null
        if(::surface.isInitialized) surface.release()
    }
}
