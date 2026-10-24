package id.tege.video.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.hardware.camera2.CameraCharacteristics as C
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.Surface
import id.tege.video.core.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Controls(
    val focus: Float?=null,
    val iso: Int=0,
    val shutterNs: Long=0,
    val ev: Int=0,
    val aeLock: Boolean=false,
    val awbLock: Boolean=false,
    val wb: Int=CaptureRequest.CONTROL_AWB_MODE_AUTO,
    val stabilization: Int=0,
    val torch: Boolean=false,
)

interface CameraListener {
    fun onStatus(message: String)
    fun onReady(entry: CameraEntry)
    fun onRecording(active: Boolean)
    fun onSaved(video: SavedVideo)
    fun onError(message: String)
}

/** Camera and recorder ownership live on one serial executor. GL owns only surfaces and grading. */
class CameraEngine(private val context: Context, private val listener: CameraListener) {
    private val thread=HandlerThread("TeGe-Camera").apply { start() }
    private val handler=Handler(thread.looper)
    private val main=Handler(Looper.getMainLooper())
    private var device: CameraDevice?=null
    private var session: CameraCaptureSession?=null
    private var request: CaptureRequest.Builder?=null
    private var renderer: VideoRenderer?=null
    private var recorder: MediaRecorder?=null
    private var encoderSurface: Surface?=null
    private var pending: File?=null
    private var name=""
    private var orientation=90
    private var controls=Controls()
    private var lut: CubeLut?=null
    private var intensity=1f
    private var bake=true
    private var look=0
    private var recordAudio=false
    private var requestedAudio=0
    private val focus=FocusPullSession()
    private var exposureInfo=""
    @Volatile var isReady=false; private set
    @Volatile var isRecording=false; private set
    @Volatile var busy=false; private set
    @Volatile private var closing=false
    @Volatile var lastSaved: SavedVideo?=null; private set
    @Volatile var lastError: String?=null; private set
    lateinit var entry: CameraEntry; private set
    lateinit var profile: VideoProfile; private set
    var hevc=false; private set
    private fun ui(block: ()->Unit) { main.post(block) }
    private fun error(message: String) { lastError=message;Log.e("TeGeVideo",message);ui { listener.onError(message) } }
    @SuppressLint("MissingPermission")
    fun open(camera: CameraEntry,p: VideoProfile,h265: Boolean,surface: Surface,w: Int,h: Int,rotation: Int) {
        handler.post {
            if(closing) return@post
            entry=camera;profile=p;hevc=h265;orientation=rotation
            try {
                check(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) { "Izin kamera belum diberikan." }
                check(p in entry.profiles(h265)) { "Kombinasi resolusi/FPS/codec tidak didukung sesi standar." }
                renderer=VideoRenderer(surface,p.width,p.height) { message -> handler.post {
                    if(!closing) { if(isRecording) stopInternal();error(message) }
                } }.apply { resize(w,h,rotation,entry.front) }
                val manager=context.getSystemService(CameraManager::class.java)
                manager.openCamera(entry.id,object: CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if(closing) { camera.close();return }
                        device=camera
                        runCatching { createSession() }.onFailure { failure ->
                            releaseCamera();error("Sesi kamera gagal: ${failure.message}")
                        }
                    }
                    override fun onDisconnected(camera: CameraDevice) { if(isRecording) stopInternal();camera.close();isReady=false;error("Kamera terputus. Buka kembali aplikasi.") }
                    override fun onError(camera: CameraDevice,code: Int) { if(isRecording) stopInternal();camera.close();isReady=false;error("Camera2 error $code. Tutup aplikasi kamera lain, lalu coba kembali.") }
                },handler)
            } catch(e: Exception) { error(e.cause?.message ?: e.message ?: "Gagal membuka kamera.");releaseCamera() }
        }
    }
    @Suppress("DEPRECATION")
    private fun createSession() {
        val camera=device ?: return
        val input=renderer?.input ?: return
        camera.createCaptureSession(listOf(input),object: CameraCaptureSession.StateCallback() {
            override fun onConfigured(value: CameraCaptureSession) {
                if(closing) { value.close();return }
                session=value
                try {
                    request=camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(input) }
                    applyControls();isReady=true;ui { listener.onReady(entry);listener.onStatus("Siap · ${profile.label}") }
                } catch(e: Exception) { error(e.message ?: "Konfigurasi kamera gagal.") }
            }
            override fun onConfigureFailed(value: CameraCaptureSession) { value.close();error("Kamera menolak sesi video ${profile.label}.") }
        },handler)
    }
    private fun applyControls() {
        val b=request ?: return
        b.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
        b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,entry.range(profile.fps))
        if(controls.iso>0 && controls.shutterNs>0 && entry.manual) {
            b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
            val isoRange=entry.characteristics[C.SENSOR_INFO_SENSITIVITY_RANGE]
            val shutterRange=entry.characteristics[C.SENSOR_INFO_EXPOSURE_TIME_RANGE]
            val period=1_000_000_000L/profile.fps
            b.set(CaptureRequest.SENSOR_SENSITIVITY,isoRange?.clamp(controls.iso) ?: controls.iso)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME,shutterRange?.clamp(controls.shutterNs.coerceAtMost(period)) ?: controls.shutterNs.coerceAtMost(period))
            b.set(CaptureRequest.SENSOR_FRAME_DURATION,period)
            b.set(CaptureRequest.CONTROL_AE_LOCK,false)
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,entry.characteristics[C.CONTROL_AE_COMPENSATION_RANGE]?.clamp(controls.ev) ?: 0)
            if(entry.characteristics[C.CONTROL_AE_LOCK_AVAILABLE]==true) b.set(CaptureRequest.CONTROL_AE_LOCK,controls.aeLock)
        }
        val distance=controls.focus
        if(distance!=null && entry.minFocus>0) {
            b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF)
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE,distance.coerceIn(0f,entry.minFocus))
        } else {
            val available=entry.characteristics[C.CONTROL_AF_AVAILABLE_MODES] ?: intArrayOf()
            b.set(CaptureRequest.CONTROL_AF_MODE,if(available.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO else CaptureRequest.CONTROL_AF_MODE_OFF)
        }
        b.set(CaptureRequest.CONTROL_AWB_MODE,controls.wb)
        if(entry.characteristics[C.CONTROL_AWB_LOCK_AVAILABLE]==true) b.set(CaptureRequest.CONTROL_AWB_LOCK,controls.awbLock)
        b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,if(controls.stabilization==1 && entry.ois) 1 else 0)
        b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,if(controls.stabilization==2 && entry.eis) 1 else 0)
        if(entry.characteristics[C.FLASH_INFO_AVAILABLE]==true) b.set(CaptureRequest.FLASH_MODE,if(controls.torch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
        session?.setRepeatingRequest(b.build(),object: CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(s: CameraCaptureSession,r: CaptureRequest,result: TotalCaptureResult) {
                exposureInfo="ISO ${result[CaptureResult.SENSOR_SENSITIVITY]} · ${result[CaptureResult.SENSOR_EXPOSURE_TIME]} ns"
            }
        },handler)
    }
    fun update(value: Controls) { handler.post {
        focus.cancel();controls=value
        runCatching { applyControls() }.onFailure { error("Kontrol kamera ditolak: ${it.message}") }
    } }
    fun pull(from: Float,to: Float,duration: Long) { handler.post {
        if(!isReady || entry.minFocus<=0) return@post
        val token=focus.start(FocusPullPlan(from.coerceIn(0f,entry.minFocus),to.coerceIn(0f,entry.minFocus),duration,FocusPullEasing.EASE_IN_OUT),SystemClock.elapsedRealtime())
        val tick=object: Runnable { override fun run() {
            if(closing) return
            val step=focus.tick(token,SystemClock.elapsedRealtime()) ?: return
            controls=controls.copy(focus=step.diopters)
            runCatching { applyControls() }.onFailure { error("Focus pull: ${it.message}") }
            if(!step.finished) handler.postDelayed(this,33)
        } };tick.run()
    } }
    fun grade(value: CubeLut?,strength: Float,recordLut: Boolean,builtIn: Int) { handler.post {
        if(isRecording || busy) return@post
        try { renderer?.grade(value,strength,recordLut,builtIn);lut=value;intensity=strength;bake=recordLut;look=builtIn }
        catch(e: Exception) { error("LUT gagal diterapkan: ${e.cause?.message ?: e.message}") }
    } }
    fun resize(w: Int,h: Int,rotation: Int) { handler.post {
        if(!isRecording) orientation=rotation
        runCatching { renderer?.resize(w,h,rotation,entry.front) }
    } }
    fun start(audioSource: Int) { handler.post {
        if(closing || busy || isRecording || !isReady) return@post
        busy=true
        try {
            check(StatFs(context.filesDir.path).availableBytes>250L*1024*1024) { "Ruang kosong minimal 250 MB diperlukan." }
            recordAudio=audioSource!=0
            requestedAudio=audioSource
            check(!recordAudio || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "Izin mikrofon belum diberikan. Pilih Tanpa audio atau berikan izin." }
            name="TeGe_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())}.mp4"
            pending=File(context.filesDir,"pending-$name")
            @Suppress("DEPRECATION")
            val rec=MediaRecorder()
            recorder=rec
            if(recordAudio) rec.setAudioSource(if(audioSource==2) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.CAMCORDER)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(if(hevc) MediaRecorder.VideoEncoder.HEVC else MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(profile.width,profile.height);rec.setVideoFrameRate(profile.fps);rec.setVideoEncodingBitRate(profile.bitrate)
            rec.setOrientationHint(orientation)
            if(recordAudio) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);rec.setAudioSamplingRate(48_000);rec.setAudioChannels(2);rec.setAudioEncodingBitRate(192_000)
            }
            rec.setOutputFile(pending!!.absolutePath)
            rec.setMaxFileSize((StatFs(context.filesDir.path).availableBytes-128L*1024*1024).coerceAtMost(8L*1024*1024*1024))
            rec.setOnErrorListener { _,what,extra -> handler.post { if(isRecording) stopInternal();error("Perekam error $what/$extra.") } }
            rec.setOnInfoListener { _,what,_ -> if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) handler.post { stopInternal() } }
            rec.prepare()
            if(audioSource==2) {
                val external=context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
                    it.type in setOf(AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADSET)
                }
                check(external!=null && rec.setPreferredDevice(external)) { "Mikrofon kabel/USB tidak tersedia atau ditolak." }
            }
            encoderSurface=rec.surface
            rec.start()
            renderer!!.attachEncoder(encoderSurface!!)
            isRecording=true;busy=false;ui { listener.onRecording(true) }
        } catch(e: Exception) {
            runCatching { renderer?.detachEncoder() };runCatching { recorder?.release() };recorder=null
            encoderSurface?.release();encoderSurface=null;pending?.delete();pending=null;busy=false
            error("Gagal merekam: ${e.cause?.message ?: e.message}")
            ui { listener.onRecording(false) }
        }
    } }
    fun stop() { handler.post { if(isRecording) stopInternal() } }
    private fun stopInternal() {
        if(!isRecording || busy) return
        busy=true;isRecording=false;focus.cancel()
        var stopped=false
        try {
            renderer?.detachEncoder()
            recorder?.stop();stopped=true
        } catch(e: Exception) { error("Video belum dapat difinalisasi: ${e.cause?.message ?: e.message}") }
        finally {
            runCatching { recorder?.release() };recorder=null;encoderSurface?.release();encoderSurface=null
        }
        val file=pending;pending=null
        if(stopped && file!=null) {
            try {
                val report=VideoFiles.inspect(file,profile,recordAudio)
                    .put("cameraId",entry.id).put("device","${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("androidApi",Build.VERSION.SDK_INT).put("lutSha256",lut?.sha256 ?: JSONObject.NULL)
                    .put("look",look).put("strength",intensity.toDouble()).put("baked",bake)
                    .put("audioSourceRequest",requestedAudio).put("lastSensorExposure",exposureInfo)
                val saved=VideoFiles.publish(context,file,name,report)
                lastSaved=saved;ui { listener.onSaved(saved) }
            } catch(e: Exception) { error("Penyimpanan belum selesai. Berkas sementara dipertahankan: ${e.message}") }
        } else { file?.delete() }
        busy=false;ui { listener.onRecording(false) }
    }
    fun amplitude(): Int = try { recorder?.maxAmplitude ?: 0 } catch(_: Exception) { 0 }
    private fun releaseCamera() {
        isReady=false;focus.cancel();session?.close();session=null;device?.close();device=null
        runCatching { renderer?.close() };renderer=null
    }
    fun close() {
        if(closing) return
        closing=true
        handler.post { if(isRecording) stopInternal();releaseCamera();thread.quitSafely() }
    }
}
