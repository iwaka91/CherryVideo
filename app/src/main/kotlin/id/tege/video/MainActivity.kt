package id.tege.video

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics as C
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import id.tege.video.camera.*
import id.tege.video.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.log10

class MainActivity : Activity(), CameraListener, SurfaceHolder.Callback {
    private val gold=Color.rgb(230,190,127)
    private val muted=Color.rgb(160,174,180)
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("tege",MODE_PRIVATE) }
    private lateinit var preview: SurfaceView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var record: Button
    private lateinit var mode: Button
    private lateinit var quality: Button
    private lateinit var colors: Button
    private lateinit var lens: Button
    private lateinit var stable: Button
    private lateinit var audioButton: Button
    private lateinit var controlHost: FrameLayout
    private lateinit var normalControls: LinearLayout
    private var colorDock=false
    private var colorTitle: TextView?=null
    private val lookButtons=ArrayList<Button>()
    private var qualityOptions=emptyList<VideoProfile>()
    private lateinit var proRow: LinearLayout
    private lateinit var grid: GridOverlay
    private var cameras=emptyList<CameraEntry>()
    private var cameraIndex=0
    private var selected=VideoProfile(1920,1080,30)
    private var controls=Controls()
    private var pro=false
    private var hevc=false
    private var audio=1
    private var lut: CubeLut?=null
    private var lutName=""
    private var look=0
    private var strength=1f
    private var bake=true
    private var focusA=0f
    private var focusB=0f
    private var pullDuration=2500L
    private var resumed=false
    private var recordingSince=0L
    private var thermal=0
    private var exportJson=""
    private var uiRecording=false
    private var rebuilding=false
    private var retiringEngines=0
    private var stickyError: String?=null
    @Volatile var engine: CameraEngine?=null; private set
    @Volatile var lastSaved: SavedVideo?=null; private set
    private val thermalListener=PowerManager.OnThermalStatusChangedListener { value ->
        thermal=value
        if(value>=PowerManager.THERMAL_STATUS_SEVERE && engine?.isRecording==true) {
            engine?.stop();onStatus("Rekaman dihentikan untuk suhu perangkat. Video sedang disimpan.")
        }
    }
    private val clock=object: Runnable {
        override fun run() {
            if(!resumed) return
            val e=engine
            if(e?.isRecording==true) {
                val elapsed=(SystemClock.elapsedRealtime()-recordingSince)/1000
                val amp=e.amplitude()
                val db=if(amp>0) (20*log10(amp/32767.0)).coerceAtLeast(-60.0).toInt() else -60
                detail.text=String.format(Locale.US,"REC %02d:%02d · %s · %s",elapsed/60,elapsed%60,if(audio==0) "Tanpa audio" else "$db dBFS",storageLabel())
                if(StatFs(filesDir.path).availableBytes<150L*1024*1024) e.stop()
            } else if(!uiRecording) {
                val fps=e?.previewStats?.optDouble("previewSubmittedFps",0.0) ?: 0.0
                detail.text="${if(fps>0) String.format(Locale.US,"Preview %.0f fps · ",fps) else ""}${if(bake) "Warna ke video" else "LUT pratinjau"} · ${storageLabel()} · ${thermalLabel()}"
            }
            main.postDelayed(this,500)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pro=prefs.getBoolean("pro",false);hevc=prefs.getBoolean("hevc",false);audio=prefs.getInt("audio",1)
        controls=Controls(stabilization=prefs.getInt("stabilization",-1))
        cameraIndex=prefs.getInt("cameraIndex",0)
        selected=VideoProfile(prefs.getInt("width",1920),prefs.getInt("height",1080),prefs.getInt("fps",30),prefs.getInt("captureFps",30))
        look=prefs.getInt("look",0);strength=prefs.getFloat("strength",1f);bake=prefs.getBoolean("bake",true)
        val stored=prefs.getString("lut",null)
        if(stored!=null) runCatching { lut=CubeLut.parse(File(filesDir,"luts/$stored.cube").readBytes());lutName=prefs.getString("lutName","LUT Saya").orEmpty() }
        cameras=runCatching { CameraCatalog.list(this) }.getOrDefault(emptyList())
        cameraIndex=cameraIndex.coerceIn(0,(cameras.size-1).coerceAtLeast(0))
        buildUi()
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO),1)
    }
    private fun dp(v: Int)= (v*resources.displayMetrics.density).toInt()
    private fun text(value: String,size: Float=14f,color: Int=Color.WHITE)=TextView(this).apply { text=value;textSize=size;setTextColor(color);setPadding(dp(4),dp(4),dp(4),dp(4)) }
    private fun shape(color: Int,radius: Int=14)=GradientDrawable().apply { setColor(color);cornerRadius=dp(radius).toFloat() }
    private fun button(value: String,tagName: String,action: ()->Unit)=Button(this).apply {
        text=value;tag=tagName;textSize=12f;isAllCaps=false;setTextColor(Color.WHITE);minWidth=0;minimumWidth=0
        maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        setPadding(dp(9),0,dp(9),0);background=shape(Color.rgb(34,42,47),12)
        setOnClickListener { action() }
        contentDescription=value
    }
    private fun row(vararg views: View)=LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        views.forEach { addView(it,LinearLayout.LayoutParams(0,dp(46),1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) }) }
    }
    private fun buildUi() {
        rebuilding=true
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        colorDock=false;colorTitle=null;lookButtons.clear()
        val root=LinearLayout(this).apply {
            orientation=if(landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16,19,21));setPadding(dp(10),dp(8),dp(10),dp(8))
            setOnApplyWindowInsetsListener { view,insets ->
                if(Build.VERSION.SDK_INT>=30) {
                    val bars=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(dp(10)+bars.left,dp(6)+bars.top,dp(10)+bars.right,dp(6)+bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(dp(10)+insets.systemWindowInsetLeft,dp(6)+insets.systemWindowInsetTop,dp(10)+insets.systemWindowInsetRight,dp(6)+insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        preview=SurfaceView(this).apply { holder.addCallback(this@MainActivity);tag="viewfinder" }
        grid=GridOverlay(this)
        val frame=FrameLayout(this).apply { addView(preview,FrameLayout.LayoutParams(-1,-1));addView(grid,FrameLayout.LayoutParams(-1,-1)) }
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };normalControls=panel
        controlHost=FrameLayout(this).apply { tag="control-host";addView(panel) }
        mode=button(if(pro) "Pro" else "Simple","mode") {
            if(locked()) return@button
            pro=!pro
            if(!pro) { controls=Controls(stabilization=controls.stabilization);engine?.update(controls) }
            prefs.edit().putBoolean("pro",pro).apply();mode.text=if(pro) "Pro" else "Simple"
            proRow.visibility=if(pro) View.VISIBLE else View.GONE
        }
        val title=text("Cherry Video",20f,gold).apply { tag="app-title";typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;addView(title,LinearLayout.LayoutParams(0,dp(48),1f));addView(mode,LinearLayout.LayoutParams(dp(80),dp(42))) }
        status=text("Menyiapkan kamera…",13f,gold).apply { tag="status";maxLines=3 }
        detail=text("1080p · cinematic video",11f,muted)
        lens=button("Kamera utama","lens") { chooseLens() }
        quality=button(selected.label,"quality") { chooseQuality() }
        colors=button("Warna / LUT","colors") { showColors() }
        stable=button("Stabilisasi","stabilization") { showStabilization() }
        audioButton=button("Audio","audio") { showAudio() }
        val settings=button("Pengaturan","settings") { showSettings() }
        proRow=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;visibility=if(pro) View.VISIBLE else View.GONE
            addView(row(button("Fokus / Pull","focus") { showFocus() },button("Exposure","exposure") { showExposure() },button("WB","white-balance") { showWhiteBalance() }))
        }
        record=button("●  REKAM","record") {
            if(colorDock) closeColors()
            val e=engine ?: return@button
            if(e.isRecording) { record.isEnabled=false;onStatus("Menyimpan dan memeriksa video…");e.stop() }
            else if(e.isReady && !e.busy) { record.isEnabled=false;onStatus("Memulai rekaman…");e.start(audio) }
        }.apply { textSize=18f;setTextColor(Color.rgb(16,19,21));background=shape(Color.rgb(255,110,99),28);isEnabled=false }
        val gallery=button("Galeri","gallery") { showGallery() }
        panel.addView(row(lens,quality));panel.addView(row(colors,stable));panel.addView(proRow)
        panel.addView(row(audioButton,settings))
        val recordRow=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL;addView(gallery,LinearLayout.LayoutParams(dp(74),dp(52)).apply { setMargins(dp(3),dp(6),dp(6),dp(6)) })
            addView(record,LinearLayout.LayoutParams(0,dp(60),1f).apply { setMargins(dp(3),dp(6),dp(3),dp(6)) })
        }
        if(landscape) {
            root.addView(frame,LinearLayout.LayoutParams(0,-1,1f))
            val sidebar=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL;addView(header);addView(status);addView(detail)
                addView(ScrollView(this@MainActivity).apply { addView(controlHost) },LinearLayout.LayoutParams(-1,0,1f))
                addView(recordRow)
            }
            root.addView(sidebar,LinearLayout.LayoutParams(dp(254),-1))
        } else {
            root.addView(header);root.addView(status);root.addView(detail)
            root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
            root.addView(controlHost);root.addView(recordRow)
        }
        setContentView(root);root.requestApplyInsets();updateSelectionLabels();rebuilding=false
    }
    private fun storageLabel(): String=String.format(Locale.US,"%.1f GB kosong",StatFs(filesDir.path).availableBytes/1_073_741_824.0)
    private fun thermalLabel()=when { thermal>=3 -> "Panas";thermal>=1 -> "Suhu meningkat";else -> "Suhu normal" }
    private fun gradeLabel()=if(lut!=null) lutName else arrayOf("Natural","Hangat","Teal","Mono").getOrElse(look) { "Natural" }
    private fun stabilizationLabel()=when(controls.stabilization) { 1->"OIS";2->"EIS";else->"OFF" }
    private fun audioLabel()=when(audio) { 1->"Kamera";2->"Kabel/USB";else->"OFF" }
    private fun updateSelectionLabels() {
        colors.text="LUT · ${gradeLabel()}";colors.contentDescription=colors.text
        stable.text="Stabilisasi · ${stabilizationLabel()}";stable.contentDescription=stable.text
        audioButton.text="Audio · ${audioLabel()}";audioButton.contentDescription=audioButton.text
        colorTitle?.text="${gradeLabel()} · ${(strength*100).toInt()}%"
        lookButtons.forEachIndexed { index,b ->
            val active=lut==null && look==index
            b.isSelected=active;b.background=shape(if(active) gold else Color.rgb(34,42,47),10)
            b.setTextColor(if(active) Color.rgb(16,19,21) else Color.WHITE)
        }
    }
    private fun rotation(): Int {
        val c=cameras.getOrNull(cameraIndex) ?: return 0
        @Suppress("DEPRECATION") val displayDegrees=when(windowManager.defaultDisplay.rotation) { Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0 }
        return if(c.front) (c.rotation+displayDegrees)%360 else (c.rotation-displayDegrees+360)%360
    }
    private fun startCamera() {
        if(!resumed || engine!=null || retiringEngines>0 || !preview.holder.surface.isValid || cameras.isEmpty()) return
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { onStatus("Berikan izin Kamera melalui Pengaturan Android untuk merekam.");return }
        val camera=cameras[cameraIndex]
        val options=camera.profiles(hevc);qualityOptions=options
        if(options.isEmpty()) { onError("Tidak ada profil video standar yang memenuhi batas kamera dan encoder.");return }
        if(selected !in options) { selected=options.first();onStatus("Profil tersedia: ${selected.label}") }
        quality.text=selected.label;lens.text=if(camera.id=="0") "Utama · 1×" else if(camera.front) "Depan" else "Kamera ${camera.id}"
        if(controls.stabilization<0 || (controls.stabilization==1 && !camera.ois) || (controls.stabilization==2 && !camera.eis))
            controls=controls.copy(stabilization=if(camera.ois) 1 else if(camera.eis) 2 else 0)
        if(selected.highSpeed) controls=controls.copy(stabilization=0,focus=null,iso=0,shutterNs=0)
        prefs.edit().putInt("width",selected.width).putInt("height",selected.height).putInt("fps",selected.fps).putInt("captureFps",selected.captureFps).apply()
        updateSelectionLabels()
        val e=CameraEngine(applicationContext,this);engine=e
        e.open(camera,selected,hevc,preview.holder.surface,preview.width,preview.height,rotation())
        e.update(controls);e.grade(lut,strength,bake,look)
    }
    private fun retireEngine() {
        val old=engine ?: return
        engine=null;retiringEngines++
        old.close { retiringEngines--;startCamera() }
    }
    private fun restart() { record.isEnabled=false;onStatus("Menyiapkan kamera…");retireEngine();main.post { startCamera() } }
    override fun surfaceCreated(holder: SurfaceHolder) { if(!rebuilding) startCamera() }
    override fun surfaceChanged(holder: SurfaceHolder,format: Int,width: Int,height: Int) { startCamera();engine?.resize(width,height,rotation()) }
    override fun surfaceDestroyed(holder: SurfaceHolder) { retireEngine() }
    override fun onResume() {
        super.onResume();resumed=true
        getSystemService(PowerManager::class.java).apply { thermal=currentThermalStatus;addThermalStatusListener(mainExecutor,thermalListener) }
        main.post { startCamera() };main.post(clock)
    }
    override fun onPause() {
        resumed=false;main.removeCallbacks(clock)
        getSystemService(PowerManager::class.java).removeThermalStatusListener(thermalListener)
        retireEngine();super.onPause()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if(engine?.isRecording==true) {
            engine?.resize(preview.width,preview.height,rotation())
        } else { retireEngine();buildUi();main.post { startCamera() } }
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,results: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) audio=0
        updateSelectionLabels()
        startCamera()
    }
    override fun onStatus(message: String) {
        if(isDestroyed || (message.startsWith("Siap") && stickyError!=null)) return
        stickyError=null;status.text=message
    }
    override fun onReady(entry: CameraEntry) { if(resumed && !isDestroyed && engine?.isReady==true) record.isEnabled=true }
    override fun onRecording(active: Boolean) {
        uiRecording=active
        if(isDestroyed) return
        requestedOrientation=if(active) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if(active) recordingSince=SystemClock.elapsedRealtime()
        record.text=if(active) "■  STOP" else "●  REKAM"
        record.contentDescription=if(active) "Stop rekaman" else "Mulai rekaman"
        record.isEnabled=engine?.isReady==true
        mode.isEnabled=!active;lens.isEnabled=!active;quality.isEnabled=!active;colors.isEnabled=!active
        if(active) onStatus("Merekam · ${selected.label} · ${if(bake) "warna tersimpan" else "LUT pratinjau"}")
    }
    override fun onSaved(video: SavedVideo) {
        lastSaved=video
        val r=video.report
        onStatus(String.format(Locale.US,"Tersimpan · %.2f fps aktual%s",r.getDouble("actualFps"),if(r.getBoolean("steadyCadence")) "" else " · ritme frame tidak stabil"))
    }
    override fun onError(message: String) {
        android.util.Log.e("TeGeVideo",message);stickyError=message
        if(!isDestroyed) { status.text=message;record.isEnabled=engine?.isReady==true;Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
    }
    private fun locked(): Boolean {
        if(engine?.isRecording==true || engine?.busy==true) { Toast.makeText(this,"Selesaikan rekaman dahulu.",Toast.LENGTH_SHORT).show();return true };return false
    }
    private fun chooseLens() {
        if(locked()) return
        AlertDialog.Builder(this).setTitle("Pilih kamera").setSingleChoiceItems(cameras.map { it.label }.toTypedArray(),cameraIndex) { dialog,index ->
            cameraIndex=index;prefs.edit().putInt("cameraIndex",index).apply();controls=Controls();focusA=0f;focusB=0f;dialog.dismiss();restart()
        }.setNegativeButton("Tutup",null).show()
    }
    private fun chooseQuality() {
        if(locked()) return
        val options=qualityOptions
        val camera=cameras.getOrNull(cameraIndex) ?: return
        AlertDialog.Builder(this).setTitle("${if(camera.front) "Depan" else "Belakang"} · ID ${camera.id} · ${if(hevc) "HEVC" else "AVC"}")
            .setSingleChoiceItems(options.map { it.label+if(it.highSpeed) " · HS (uji)" else "" }.toTypedArray(),options.indexOf(selected)) { dialog,index ->
            selected=options[index];dialog.dismiss();restart()
        }.setPositiveButton("Info") { _,_ -> message("Dukungan video","Mode tersedia ditentukan per kamera dan codec. 4K hanya ditampilkan pada kamera yang mendukungnya. Mode 60 fps memakai sesi standar bila tersedia, atau high-speed 120+ fps yang disampel menjadi video 60 fps dengan waktu normal. Pada high-speed kontrol manual dan stabilisasi dinonaktifkan. Keberhasilan dan FPS aktual harus diuji di perangkat; buka Diagnostik untuk rincian dukungan.") }
            .setNegativeButton("Tutup",null).show()
    }
    private fun body()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(12)) }
    private fun sheet(title: String,body: View): AlertDialog = AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Selesai",null).show()
    private fun message(title: String,value: String) { sheet(title,body().apply { addView(text(value,14f).apply { setTextIsSelectable(true) }) }) }
    private fun slider(panel: LinearLayout,title: String,initial: Int,max: Int,change: (Int,TextView)->Unit) {
        val label=text(title);panel.addView(label)
        panel.addView(SeekBar(this).apply {
            this.max=max;progress=initial;contentDescription=title
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar,value: Int,fromUser: Boolean) { if(fromUser) change(value,label) }
                override fun onStartTrackingTouch(bar: SeekBar) {} ;override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        },LinearLayout.LayoutParams(-1,dp(48)))
    }
    private fun check(panel: LinearLayout,title: String,value: Boolean,enabled: Boolean=true,change: (Boolean)->Unit) {
        panel.addView(CheckBox(this).apply { text=title;isChecked=value;isEnabled=enabled;setTextColor(Color.WHITE);setOnCheckedChangeListener { _,v -> change(v) } })
    }
    private fun persistGrade() {
        prefs.edit().putInt("look",look).putFloat("strength",strength).putBoolean("bake",bake).putString("lut",lut?.sha256).putString("lutName",lutName).apply()
        engine?.grade(lut,strength,bake,look)
        updateSelectionLabels()
    }
    private fun showColors() {
        if(locked()) return
        val panel=body()
        panel.tag="lut-dock";lookButtons.clear()
        colorTitle=text("",14f,gold).apply { tag="lut-active";maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END }
        panel.addView(LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL;addView(colorTitle,LinearLayout.LayoutParams(0,dp(38),1f))
            addView(button("Tutup","close-lut") { closeColors() },LinearLayout.LayoutParams(dp(64),dp(38)))
        })
        val lookRow=LinearLayout(this)
        arrayOf("Natural","Hangat","Teal","Mono").forEachIndexed { index,name ->
            val b=button(name,"look-$index") { lut=null;lutName="";look=index;persistGrade();onStatus("Warna: $name") }
            lookButtons.add(b);lookRow.addView(b,LinearLayout.LayoutParams(dp(88),dp(40)).apply { rightMargin=dp(5) })
        }
        panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;addView(lookRow) })
        val import=button("＋ Impor .cube","import-lut") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*";addCategory(Intent.CATEGORY_OPENABLE) },100)
        }
        panel.addView(row(import,button("LUT Saya","my-luts") { showLutLibrary() },button("Info","lut-info") {
            message("Format LUT","Impor .cube 3D ukuran 2–33 (termasuk 17³/33³), maksimal 4 MB. Gunakan LUT kreatif SDR / Rec.709. LUT konversi Log/HDR memerlukan profil sumber yang cocok. LUT 1D, shaper dan 65³ belum didukung.\n\nJika Terapkan ke video dimatikan, warna hanya tampak di preview.")
        }))
        slider(panel,"Intensitas ${(strength*100).toInt()}%",(strength*100).toInt(),100) { value,label -> strength=value/100f;label.text="Intensitas $value%";persistGrade() }
        check(panel,"Terapkan ke video",bake) { bake=it;persistGrade() }
        controlHost.removeAllViews()
        if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) controlHost.addView(panel)
        else {
            controlHost.layoutParams.height=dp(260)
            controlHost.addView(ScrollView(this).apply { addView(panel) },FrameLayout.LayoutParams(-1,-1))
        }
        colorDock=true;updateSelectionLabels();controlHost.requestLayout()
    }
    private fun closeColors() {
        controlHost.removeAllViews();controlHost.addView(normalControls)
        controlHost.layoutParams.height=ViewGroup.LayoutParams.WRAP_CONTENT
        colorDock=false;colorTitle=null;lookButtons.clear();controlHost.requestLayout()
    }
    private fun showLutLibrary() {
        val directory=File(filesDir,"luts").apply { mkdirs() }
        val files=directory.listFiles()?.filter { it.extension=="cube" }?.sortedBy { it.name }.orEmpty()
        if(files.isEmpty()) { message("LUT Saya","Belum ada LUT. Pilih Impor LUT untuk menambahkan berkas .cube.");return }
        val names=files.map { File(directory,"${it.nameWithoutExtension}.name").takeIf { n -> n.exists() }?.readText() ?: it.name }
        AlertDialog.Builder(this).setTitle("LUT Saya").setItems(names.toTypedArray()) { _,index ->
            AlertDialog.Builder(this).setTitle(names[index]).setItems(arrayOf("Pakai LUT","Hapus dari aplikasi")) { _,action ->
                if(action==0) {
                    io.execute { runCatching { CubeLut.parse(files[index].readBytes()) }.onSuccess { parsed -> main.post {
                        lut=parsed;lutName=names[index];persistGrade();onStatus("LUT aktif: $lutName")
                    } }.onFailure { e -> main.post { onError("LUT tidak valid: ${e.message}") } } }
                } else {
                    if(lut?.sha256==files[index].nameWithoutExtension) { lut=null;persistGrade() }
                    files[index].delete();File(directory,"${files[index].nameWithoutExtension}.name").delete()
                }
            }.show()
        }.setNegativeButton("Tutup",null).show()
    }
    @Deprecated("Legacy Activity API used for a dependency-light file picker")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        val uri=data?.data ?: return
        if(resultCode!=RESULT_OK) return
        if(requestCode==100) {
            onStatus("Memeriksa LUT…")
            io.execute {
                runCatching {
                    val name=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } ?: "LUT impor.cube"
                    require(name.endsWith(".cube",true)) { "Pilih berkas dengan ekstensi .cube." }
                    val bytes=checkNotNull(contentResolver.openInputStream(uri)).use { input ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val count=input.read(buffer)
                            if(count<0) break
                            require(output.size()+count<=CubeLut.MAX_BYTES) { "Ukuran LUT melebihi 4 MB." }
                            output.write(buffer,0,count)
                        }
                        output.toByteArray()
                    }
                    val parsed=CubeLut.parse(bytes)
                    val directory=File(filesDir,"luts").apply { mkdirs() }
                    File(directory,"${parsed.sha256}.cube").writeBytes(bytes)
                    File(directory,"${parsed.sha256}.name").writeText(name)
                    parsed to name
                }.onSuccess { (parsed,name) -> main.post {
                    lut=parsed;lutName=name;persistGrade();onStatus("LUT diimpor: $name · ${parsed.size}³")
                } }.onFailure { e -> main.post { onError("Impor LUT ditolak: ${e.message}") } }
            }
        } else if(requestCode==101) {
            io.execute { runCatching { checkNotNull(contentResolver.openOutputStream(uri,"wt")).use { it.write(exportJson.toByteArray()) } }
                .onSuccess { main.post { onStatus("Laporan diagnostik diekspor.") } }.onFailure { e -> main.post { onError("Ekspor gagal: ${e.message}") } } }
        }
    }
    private fun showStabilization() {
        if(locked()) return
        val c=cameras.getOrNull(cameraIndex) ?: return
        if(selected.highSpeed) { message("Stabilisasi","Mode high-speed menggunakan OFF. Pilih 30 fps untuk memakai OIS atau EIS.");return }
        val options=mutableListOf("OFF" to 0)
        if(c.ois) options.add("Optik (OIS)" to 1)
        if(c.eis) options.add("Video (EIS)" to 2)
        AlertDialog.Builder(this).setTitle("Stabilisasi").setSingleChoiceItems(options.map { it.first }.toTypedArray(),options.indexOfFirst { it.second==controls.stabilization }) { dialog,index ->
            controls=controls.copy(stabilization=options[index].second);engine?.update(controls)
            prefs.edit().putInt("stabilization",controls.stabilization).apply();updateSelectionLabels();dialog.dismiss();onStatus("Stabilisasi: ${options[index].first}")
        }.setNegativeButton("Tutup",null).show()
    }
    private fun showAudio() {
        if(locked()) return
        AlertDialog.Builder(this).setTitle("Sumber audio").setSingleChoiceItems(arrayOf("Tanpa audio","Mikrofon kamera","Mikrofon kabel / USB"),audio) { dialog,index ->
            if(index!=0 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),2)
            else { audio=index;prefs.edit().putInt("audio",audio).apply();updateSelectionLabels();onStatus("Audio: ${arrayOf("tanpa audio","mikrofon kamera","mikrofon kabel / USB")[index]}") }
            dialog.dismiss()
        }.setNegativeButton("Tutup",null).show()
    }
    private fun showFocus() {
        if(selected.highSpeed) { message("Fokus","Sesi high-speed memakai autofocus otomatis. Gunakan 30 fps untuk kontrol fokus manual.");return }
        val c=cameras.getOrNull(cameraIndex) ?: return
        if(c.minFocus<=0) { message("Fokus","Kamera ini tidak menawarkan lensa fokus yang bisa digerakkan.");return }
        val panel=body()
        panel.addView(button("Autofocus video","autofocus") { controls=controls.copy(focus=null);engine?.update(controls) },LinearLayout.LayoutParams(-1,dp(46)))
        slider(panel,"Fokus manual · ∞ sampai dekat",(((controls.focus ?: 0f)/c.minFocus)*1000).toInt(),1000) { v,label ->
            val d=v/1000f*c.minFocus;controls=controls.copy(focus=d);engine?.update(controls)
            label.text=if(d<.001f) "Fokus ∞" else String.format(Locale.US,"Fokus %.2f diopter",d)
        }
        val marks=text("A: $focusA D   →   B: $focusB D",14f,gold);panel.addView(marks)
        panel.addView(row(button("Simpan A","mark-a") { focusA=controls.focus ?: 0f;marks.text="A: $focusA D → B: $focusB D" },button("Simpan B","mark-b") { focusB=controls.focus ?: 0f;marks.text="A: $focusA D → B: $focusB D" }))
        slider(panel,"Durasi ${pullDuration/1000f} detik",(pullDuration/100).toInt()-5,95) { v,label -> pullDuration=(v+5)*100L;label.text="Durasi ${pullDuration/1000f} detik" }
        panel.addView(row(button("A → B","pull-ab") { controls=controls.copy(focus=focusB);engine?.pull(focusA,focusB,pullDuration) },button("B → A","pull-ba") { controls=controls.copy(focus=focusA);engine?.pull(focusB,focusA,pullDuration) }))
        panel.addView(text("Tandai A dan B sambil melihat ketajaman gambar. Kecepatan gerak lensa tetap bergantung pada motor fokus kamera.",12f,muted))
        sheet("Fokus & focus pull",panel)
    }
    private fun showExposure() {
        if(selected.highSpeed) { message("Exposure","Sesi high-speed memakai exposure otomatis. Gunakan 30 fps untuk ISO dan shutter manual.");return }
        val c=cameras.getOrNull(cameraIndex) ?: return
        val panel=body()
        check(panel,"Exposure manual",controls.iso>0,c.manual) { enabled ->
            controls=if(enabled) controls.copy(iso=c.characteristics[C.SENSOR_INFO_SENSITIVITY_RANGE]?.clamp(400) ?: 400,shutterNs=1_000_000_000L/(selected.fps*2)) else controls.copy(iso=0,shutterNs=0)
            engine?.update(controls)
        }
        val isoRange=c.characteristics[C.SENSOR_INFO_SENSITIVITY_RANGE]
        if(c.manual && isoRange!=null) {
            slider(panel,"ISO ${if(controls.iso>0) controls.iso else "Auto"}",((controls.iso.takeIf { it>0 } ?: 400)-isoRange.lower).coerceAtLeast(0),isoRange.upper.coerceAtMost(6400)-isoRange.lower) { value,label ->
                controls=controls.copy(iso=value+isoRange.lower,shutterNs=controls.shutterNs.takeIf { it>0 } ?: 1_000_000_000L/(selected.fps*2));engine?.update(controls);label.text="ISO ${controls.iso}"
            }
            panel.addView(button("Shutter: ${if(controls.shutterNs>0) "1/${1_000_000_000L/controls.shutterNs}" else "Auto"}","shutter") {
                val denominators=listOf(30,60,100,120,250,500,1000).filter { it>=selected.fps }
                AlertDialog.Builder(this).setTitle("Shutter").setItems(denominators.map { "1/$it detik" }.toTypedArray()) { _,index ->
                    controls=controls.copy(iso=controls.iso.takeIf { it>0 } ?: isoRange.clamp(400),shutterNs=1_000_000_000L/denominators[index]);engine?.update(controls);onStatus("Shutter 1/${denominators[index]}")
                }.show()
            },LinearLayout.LayoutParams(-1,dp(48)))
        }
        val evRange=c.characteristics[C.CONTROL_AE_COMPENSATION_RANGE]
        if(evRange!=null) slider(panel,"Kompensasi exposure (Auto)",controls.ev-evRange.lower,evRange.upper-evRange.lower) { value,label ->
            controls=controls.copy(ev=value+evRange.lower,iso=0,shutterNs=0);engine?.update(controls)
            val step=c.characteristics[C.CONTROL_AE_COMPENSATION_STEP]?.toFloat() ?: 0f
            label.text=String.format(Locale.US,"Exposure %+.1f EV",controls.ev*step)
        }
        check(panel,"Kunci exposure otomatis",controls.aeLock,c.characteristics[C.CONTROL_AE_LOCK_AVAILABLE]==true) { controls=controls.copy(aeLock=it);engine?.update(controls) }
        panel.addView(text("Exposure manual mengatur ISO dan shutter bersamaan. Shutter dibatasi agar tidak melebihi satu periode frame.",12f,muted))
        sheet("Exposure",panel)
    }
    private fun showWhiteBalance() {
        if(selected.highSpeed) { message("White balance","Sesi high-speed memakai white balance otomatis.");return }
        val c=cameras.getOrNull(cameraIndex) ?: return
        val panel=body()
        val available=c.characteristics[C.CONTROL_AWB_AVAILABLE_MODES] ?: intArrayOf()
        val options=listOf("Auto" to 1,"Siang" to 5,"Berawan" to 6,"Teduh" to 8,"Lampu pijar" to 2,"Fluoresen" to 3)
        options.filter { it.second in available }.forEach { (name,value) ->
            panel.addView(button(name,"wb-$value") { controls=controls.copy(wb=value,awbLock=false);engine?.update(controls);onStatus("White balance: $name") },LinearLayout.LayoutParams(-1,dp(44)).apply { bottomMargin=dp(4) })
        }
        check(panel,"Kunci AWB",controls.awbLock,c.characteristics[C.CONTROL_AWB_LOCK_AVAILABLE]==true) { controls=controls.copy(awbLock=it);engine?.update(controls) }
        sheet("White balance",panel)
    }
    private fun showSettings() {
        if(locked()) return
        val panel=body()
        check(panel,"HEVC / H.265",hevc) { value ->
            if(cameras.getOrNull(cameraIndex)?.profiles(value).isNullOrEmpty()) onError("Codec ini tidak menawarkan profil yang sesuai.")
            else { hevc=value;prefs.edit().putBoolean("hevc",hevc).apply();restart() }
        }
        check(panel,"Grid komposisi",grid.enabledGrid) { grid.enabledGrid=it;grid.invalidate() }
        check(panel,"Lampu kontinu",controls.torch,cameras.getOrNull(cameraIndex)?.characteristics?.get(C.FLASH_INFO_AVAILABLE)==true) { controls=controls.copy(torch=it);engine?.update(controls) }
        panel.addView(text("Penyimpanan: Movies/CherryVideo\n${storageLabel()}\nSaat aplikasi masuk latar atau layar dikunci, rekaman dihentikan dan disimpan. Jaga aplikasi tetap terbuka untuk merekam. Suhu berat dan ruang menipis memicu penyimpanan otomatis.",13f,muted))
        panel.addView(button("Diagnostik perangkat & FPS","diagnostics") { showDiagnostics() },LinearLayout.LayoutParams(-1,dp(48)))
        val about=button("Tentang Cherry Video","about") {}
        panel.addView(about,LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(5) })
        val dialog=sheet("Pengaturan",panel)
        about.setOnClickListener { dialog.dismiss();showAbout() }
    }
    private fun showAbout() {
        val panel=body().apply { tag="about-content" }
        panel.addView(text("Cherry Video",22f,gold))
        panel.addView(text("Versi ${BuildConfig.VERSION_NAME}",13f,muted))
        panel.addView(text("Kamera video sinematik untuk Xiaomi 13T.\nDikembangkan untuk Toto Gutomo.",15f))
        panel.addView(text("Komponen open source",16f,gold))
        panel.addView(text("CubeLut dan FocusPull diadaptasi dari OpenCineCam.\nCopyright 2026 OpenCineCam contributors.\nApache License 2.0.",14f))
        panel.addView(button("Source Cherry Video","source-link") { openLink("https://github.com/iwaka91/CherryVideo") },LinearLayout.LayoutParams(-1,dp(46)))
        panel.addView(button("Source OpenCineCam","upstream-link") { openLink("https://github.com/LibreStatic/opencinecam") },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(6) })
        panel.addView(button("Baca lisensi lengkap","license") {
            val raw=assets.open("LICENSE").bufferedReader().use { it.readText() }
            val readable=raw.split(Regex("\\n\\s*\\n")).joinToString("\n\n") { p -> p.lines().joinToString(" ") { it.trim() } }
            message("Apache License 2.0",readable)
        },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(6) })
        sheet("Tentang",panel)
    }
    private fun openLink(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { onError("Tidak ada aplikasi pembuka tautan.") }
    }
    private fun showDiagnostics() {
        val last=File(filesDir,"last-recording.json").takeIf { it.exists() }?.readText()
        val attempt=File(filesDir,"last-recording-attempt.json").takeIf { it.exists() }?.readText()
        exportJson=JSONObject().put("device","${Build.MANUFACTURER} ${Build.MODEL}").put("api",Build.VERSION.SDK_INT)
            .put("version",BuildConfig.VERSION_NAME).put("cameras",JSONArray(cameras.map { it.report() }))
            .put("physicalCameras",CameraCatalog.physicalReport(this,cameras)).put("preview",engine?.previewStats)
            .put("selectedCamera",cameras.getOrNull(cameraIndex)?.id).put("selectedProfile",selected.label).put("codec",if(hevc) "HEVC" else "AVC")
            .put("lastRecording",last?.let { JSONObject(it) } ?: JSONObject.NULL)
            .put("lastRecordingAttempt",attempt?.let { JSONObject(it) } ?: JSONObject.NULL).toString(2)
        val panel=body()
        val p=engine?.previewStats ?: JSONObject()
        panel.addView(text("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid API ${Build.VERSION.SDK_INT} · ${BuildConfig.VERSION_NAME}",15f,gold))
        panel.addView(text("Kamera ${cameras.getOrNull(cameraIndex)?.id} · ${selected.label} · ${if(hevc) "HEVC" else "AVC"}\n${String.format(Locale.US,"Preview %.1f fps · render %.1f ms\nJeda frame maksimum %.1f ms",p.optDouble("previewSubmittedFps",0.0),p.optDouble("averageRenderMs",0.0),p.optDouble("maxArrivalGapMs",0.0))}",14f))
        cameras.forEach { c ->
            panel.addView(text("${c.label}\nSensor ${c.rotation}° · fokus 0–${c.minFocus} D\nOIS ${c.ois} · EIS ${c.eis}\nAVC: ${c.profiles(false).joinToString { it.label }}\nHEVC: ${c.profiles(true).joinToString { it.label }}\nHigh-speed 1080: ${c.highSpeedRates(1920,1080).joinToString().ifEmpty { "tidak dilaporkan" }}",13f))
        }
        panel.addView(button("JSON lengkap","diagnostic-json") { message("Diagnostik JSON",exportJson) },LinearLayout.LayoutParams(-1,dp(46)))
        panel.addView(button("Ekspor JSON","export-diagnostic") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"CherryVideo-diagnostik.json") },101)
        },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        sheet("Diagnostik",panel)
    }
    private fun showGallery() {
        if(locked()) return
        io.execute {
            runCatching {
                val items=ArrayList<Pair<String,Uri>>()
                contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME),
                    "${MediaStore.Video.Media.RELATIVE_PATH} IN (?, ?)",arrayOf("Movies/CherryVideo/","Movies/TeGeVideo/"),"${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                    while(c.moveToNext() && items.size<100) items.add(c.getString(1) to ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(0)))
                };items
            }.onSuccess { items -> main.post {
                if(items.isEmpty()) message("Galeri","Belum ada rekaman Cherry Video.")
                else AlertDialog.Builder(this).setTitle("Video Cherry").setItems(items.map { it.first }.toTypedArray()) { _,index ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(items[index].second,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { onError("Pasang pemutar video untuk membuka rekaman.") }
                }.setNegativeButton("Tutup",null).show()
            } }.onFailure { e -> main.post { onError("Galeri: ${e.message}") } }
        }
    }
    @Deprecated("Legacy Activity API")
    override fun onBackPressed() {
        if(engine?.isRecording==true) AlertDialog.Builder(this).setTitle("Simpan dan keluar?").setMessage("Rekaman akan dihentikan dan disimpan.").setPositiveButton("Simpan") { _,_ -> engine?.stop() }.setNegativeButton("Lanjut merekam",null).show()
        else if(colorDock) closeColors() else super.onBackPressed()
    }
}

private class GridOverlay(context: Context): View(context) {
    var enabledGrid=false
    private val paint=Paint().apply { color=Color.argb(75,255,255,255);strokeWidth=resources.displayMetrics.density }
    override fun onDraw(canvas: Canvas) {
        if(!enabledGrid) return
        for(i in 1..2) { canvas.drawLine(width*i/3f,0f,width*i/3f,height.toFloat(),paint);canvas.drawLine(0f,height*i/3f,width.toFloat(),height*i/3f,paint) }
    }
}
