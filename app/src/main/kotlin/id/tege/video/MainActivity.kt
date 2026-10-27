package id.tege.video

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
    private val green=Color.rgb(47,191,113)
    private val surface=Color.rgb(34,42,47)
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("tege",MODE_PRIVATE) }
    private lateinit var preview: SurfaceView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var record: Button
    private lateinit var modeSwitch: Switch
    private lateinit var proBadge: TextView
    private lateinit var moreButton: Button
    private lateinit var zoomBadge: TextView
    private lateinit var zoomChips: LinearLayout
    private lateinit var note: TextView
    private val chipViews=ArrayList<TextView>()
    private var chipValues=emptyList<Float>()
    private var chipKey=""
    private var zoomChangedAt=0L
    private var probing=false
    private val lookNames=arrayOf("Natural","Hangat","Teal","Mono","Cinematic 1","Cinematic 2")
    private val hideNote=Runnable { if(::note.isInitialized) note.visibility=View.GONE }
    private var drawerOpen=false
    private lateinit var quality: Button
    private lateinit var colors: Button
    private lateinit var lens: Button
    private lateinit var stable: Button
    private lateinit var audioButton: Button
    private lateinit var zoomButton: Button
    private lateinit var zoomDock: LinearLayout
    private lateinit var zoomSeek: SeekBar
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
    /** Counters for diagnostics in automated tests only. */
    @Volatile internal var touchEvents=0
    @Volatile internal var scaleBegins=0
    @Volatile internal var lastTouchPointers=0
    @Volatile internal var maxTouchPointers=0
    private var controls=Controls()
    private var pro=false
    private var hevc=false
    private var audio=1
    private var audioMode=AudioMode.NATURAL
    private var audioDeviceId=0
    private var lut: CubeLut?=null
    private var lutName=""
    private var look=0
    private var strength=1f
    private var bake=true
    // Live Bokeh and Cinematic Look. The two switches start off at every launch; strengths and choices are remembered.
    private var bokeh=false
    private var bokehStrength=BokehLook.DEFAULT_STRENGTH
    private var bokehSensitivity=.5f
    private var bokehSmooth=true
    private var bokehFeather=BokehPreset.POTRET.feather
    private var bokehGrade=BokehPreset.POTRET.grade
    private var bokehShowMask=false
    private var bokehPreset: BokehPreset?=BokehPreset.POTRET
    private var cinema=false
    private var cinemaLook=4
    private var cinemaStrength=.7f
    private var cinemaBars=false
    private var cinemaBarsInVideo=false
    private lateinit var bokehButton: Button
    private lateinit var cinemaButton: Button
    private lateinit var banner: TextView
    private lateinit var focusRing: View
    private lateinit var lockChip: Button
    private var lockToken=0
    private var savingSince=0L
    private var savingShown=false
    private val hideRing=Runnable { if(::focusRing.isInitialized && !controls.lock) focusRing.visibility=View.GONE }
    private val hot=HotStopTimer()
    private var hotText: String?=null
    private var fpsText: String?=null
    private var fpsMarkAt=0L
    private var fpsMarkFrames=0L
    private var fpsLow=0
    private val hotStartMessage="HP panas, dinginkan HP dulu sebelum mulai merekam."
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
        val wasBlocked=ThermalPolicy.blocksStart(thermal)
        thermal=value
        if(value>=ThermalPolicy.STOP_NOW && engine?.isRecording==true) {
            engine?.stop();onStatus("Rekaman dihentikan karena HP terlalu panas. Video sedang disimpan.")
        } else if(!wasBlocked && ThermalPolicy.blocksStart(value) && engine?.isRecording!=true) flashNote(hotStartMessage)
        updateHotUi()
    }
    private val clock=object: Runnable {
        override fun run() {
            if(!resumed) return
            val e=engine
            syncZoomFromCamera(e)
            tickHot(e);tickFps(e);tickSaving(e)
            if(e?.isRecording==true) {
                val elapsed=(SystemClock.elapsedRealtime()-recordingSince)/1000
                val amp=e.amplitude()
                val db=if(amp>0) (20*log10(amp/32767.0)).coerceAtLeast(-60.0).toInt() else -60
                detail.text=String.format(Locale.US,"REC %02d:%02d · %s · %s",elapsed/60,elapsed%60,if(audio==0) "Tanpa audio" else "$db dBFS",storageLabel())
                if(StatFs(filesDir.path).availableBytes<150L*1024*1024) e.stop()
            } else if(!uiRecording) {
                val fps=e?.previewStats?.optDouble("previewSubmittedFps",0.0) ?: 0.0
                detail.text="${if(fps>0) String.format(Locale.US,"Preview %.0f fps · ",fps) else ""}${if(bake || cinema) "Warna ke video" else "LUT pratinjau"} · ${storageLabel()} · ${thermalLabel()}"
            }
            main.postDelayed(this,500)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pro=prefs.getBoolean("pro",false);hevc=prefs.getBoolean("hevc",false);audio=prefs.getInt("audio",1)
        controls=Controls(stabilization=prefs.getInt("stabilization",-1))
        audioMode=AudioMode.fromOrdinal(prefs.getInt("audioMode",0));audioDeviceId=prefs.getInt("audioDeviceId",0)
        selected=VideoProfile(prefs.getInt("width",1920),prefs.getInt("height",1080),prefs.getInt("fps",30),prefs.getInt("captureFps",30))
        look=prefs.getInt("look",0);strength=prefs.getFloat("strength",1f);bake=prefs.getBoolean("bake",true)
        // 0.3.4: standard blur is 6% (F13). Older saved strengths are reset once; afterwards the user's own value is kept.
        if(!prefs.getBoolean("bokehStandard6",false)) prefs.edit().putFloat("bokehStrength",BokehLook.DEFAULT_STRENGTH).putBoolean("bokehStandard6",true).apply()
        bokehStrength=prefs.getFloat("bokehStrength",BokehLook.DEFAULT_STRENGTH).coerceIn(0f,1f)
        bokehSensitivity=prefs.getFloat("bokehSensitivity",.5f).coerceIn(0f,1f);bokehSmooth=prefs.getBoolean("bokehSmooth",true)
        bokehFeather=prefs.getFloat("bokehFeather",BokehPreset.POTRET.feather).coerceIn(0f,1f);bokehGrade=prefs.getFloat("bokehGrade",BokehPreset.POTRET.grade).coerceIn(0f,1f)
        bokehPreset=BokehPreset.of(prefs.getString("bokehPreset",BokehPreset.POTRET.name))
        cinemaLook=if(prefs.getInt("cinemaLook",4)==5) 5 else 4;cinemaStrength=prefs.getFloat("cinemaStrength",.7f).coerceIn(0f,1f)
        cinemaBars=prefs.getBoolean("cinemaBars",false);cinemaBarsInVideo=prefs.getBoolean("cinemaBarsInVideo",false)
        val stored=prefs.getString("lut",null)
        if(stored!=null) runCatching { lut=CubeLut.parse(File(filesDir,"luts/$stored.cube").readBytes());lutName=prefs.getString("lutName","LUT Saya").orEmpty() }
        cameras=runCatching { CameraCatalog.list(this) }.getOrDefault(emptyList())
        // Rear lenses are merged into one "Belakang" camera. Older versions stored a list index; keep its facing only.
        val storedFacing=prefs.getString("facing",null)
        val wantFront=if(storedFacing!=null) storedFacing=="front" else cameras.getOrNull(prefs.getInt("cameraIndex",0))?.front==true
        cameraIndex=defaultIndex(wantFront)
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
        var pinched=false
        // Own two-finger handling (distance between the first two fingers) so zoom does not depend on the
        // platform detector's minimum-span rules. The preview takes the whole touch sequence from the first finger
        // down; if the first finger is refused, Android never delivers the second finger to this view.
        var lastGap=0f
        fun gapOf(event: MotionEvent): Float = if(event.pointerCount>=2) kotlin.math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1)) else 0f
        // One finger: tap = focus here, long press = lock AE/AF, drag up/down = brightness. Two fingers = zoom.
        val slop=ViewConfiguration.get(this).scaledTouchSlop
        var downX=0f;var downY=0f;var lastX=0f;var lastY=0f
        var moved=false;var longFired=false;var dragStartEv=0;var dragNoted=false
        val longPress=Runnable { if(!pinched && !moved) { longFired=true;beginLock(lastX,lastY) } }
        preview.setOnTouchListener { view,event ->
            touchEvents++;lastTouchPointers=event.pointerCount;if(event.pointerCount>maxTouchPointers) maxTouchPointers=event.pointerCount
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pinched=false;lastGap=0f;downX=event.x;downY=event.y;lastX=downX;lastY=downY
                    moved=false;longFired=false;dragNoted=false;dragStartEv=controls.ev
                    main.removeCallbacks(longPress);main.postDelayed(longPress,ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_POINTER_DOWN -> { pinched=true;lastGap=gapOf(event);main.removeCallbacks(longPress) }
                MotionEvent.ACTION_POINTER_UP -> lastGap=0f
                MotionEvent.ACTION_MOVE -> {
                    val gap=gapOf(event)
                    if(gap>0f && lastGap>0f && kotlin.math.abs(gap-lastGap)>=1f) { pinched=true;scaleBegins++;setZoom(controls.zoom*gap/lastGap);lastGap=gap }
                    else if(gap>0f && lastGap==0f) lastGap=gap
                    if(event.pointerCount==1 && !pinched) {
                        lastX=event.x;lastY=event.y
                        if(!moved && kotlin.math.hypot(lastX-downX,lastY-downY)>slop) { moved=true;main.removeCallbacks(longPress) }
                        if(moved && !longFired && kotlin.math.abs(lastY-downY)>kotlin.math.abs(lastX-downX)) {
                            val why=brightnessBlockedReason()
                            if(why!=null) { if(!dragNoted) { dragNoted=true;flashNote(why) } }
                            else dragBrightness(dragStartEv,downY-lastY)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    if(!pinched && !moved && !longFired) tapFocusAt(event.x,event.y)
                    if(!pinched) view.performClick()
                    lastGap=0f
                }
                MotionEvent.ACTION_CANCEL -> { lastGap=0f;main.removeCallbacks(longPress) }
            }
            true
        }
        grid=GridOverlay(this)
        detail=text("1080p · cinematic video",11f,Color.WHITE).apply {
            tag="detail";maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            background=shape(Color.argb(150,0,0,0),10);setPadding(dp(8),dp(3),dp(8),dp(3))
        }
        zoomBadge=text("1.0×",14f,Color.WHITE).apply {
            tag="zoom-badge";typeface=Typeface.DEFAULT_BOLD
            background=shape(Color.argb(150,0,0,0),16);setPadding(dp(12),dp(5),dp(12),dp(5))
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        zoomChips=LinearLayout(this).apply {
            tag="zoom-chips";gravity=Gravity.CENTER_VERTICAL;visibility=View.GONE
            background=shape(Color.argb(150,0,0,0),20);setPadding(dp(4),dp(3),dp(4),dp(3))
        }
        val zoomOverlay=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL
            addView(zoomBadge,LinearLayout.LayoutParams(-2,-2).apply { bottomMargin=dp(6) })
            addView(zoomChips,LinearLayout.LayoutParams(-2,-2))
        }
        note=text("",13f,Color.WHITE).apply {
            tag="zoom-note";visibility=View.GONE;gravity=Gravity.CENTER
            background=shape(Color.argb(190,0,0,0),14);setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        banner=text("",13f,Color.WHITE).apply {
            tag="banner";visibility=View.GONE;gravity=Gravity.CENTER
            background=shape(Color.argb(215,150,40,30),14);setPadding(dp(12),dp(7),dp(12),dp(7))
        }
        focusRing=View(this).apply {
            tag="focus-ring";visibility=View.GONE;isClickable=false;isFocusable=false
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        lockChip=button("AE/AF terkunci · Buka kunci","lock-chip") { releaseLock(true) }.apply {
            visibility=View.GONE;background=shape(Color.argb(215,24,120,70),16);setPadding(dp(14),0,dp(14),0)
        }
        chipKey="";chipViews.clear()
        val frame=FrameLayout(this).apply {
            addView(preview,FrameLayout.LayoutParams(-1,-1));addView(grid,FrameLayout.LayoutParams(-1,-1))
            addView(detail,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply { setMargins(dp(8),dp(8),dp(8),0) })
            addView(zoomOverlay,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(12) })
            addView(note,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER).apply { setMargins(dp(16),0,dp(16),0) })
            addView(banner,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(16),dp(40),dp(16),0) })
            addView(focusRing,FrameLayout.LayoutParams(dp(64),dp(64),Gravity.TOP or Gravity.START))
            addView(lockChip,FrameLayout.LayoutParams(-2,dp(36),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin=dp(76) })
        }
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };normalControls=panel
        controlHost=FrameLayout(this).apply { tag="control-host";addView(panel) }
        // Simpel | Pro lives in the "Lainnya" drawer as one switch; a small PRO badge in the header shows the active mode.
        modeSwitch=Switch(this).apply {
            tag="mode-pro";textSize=14f;setTextColor(Color.WHITE);setPadding(dp(12),0,dp(12),0)
            background=shape(surface,12)
            val states=arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf())
            thumbTintList=ColorStateList(states,intArrayOf(green,Color.rgb(174,183,188)))
            trackTintList=ColorStateList(states,intArrayOf(Color.argb(150,47,191,113),Color.rgb(70,80,86)))
            isChecked=pro
            setOnCheckedChangeListener { _,checked -> if(!setPro(checked)) applyModeUi() }
        }
        proBadge=text("PRO",12f,Color.rgb(11,26,16)).apply {
            tag="pro-badge";typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;visibility=View.GONE
            background=shape(green,12);setPadding(dp(10),dp(3),dp(10),dp(3))
        }
        val title=text("Cherry Video",20f,gold).apply { tag="app-title";typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=1 }
        val header=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(title,LinearLayout.LayoutParams(0,dp(46),1f));addView(proBadge,LinearLayout.LayoutParams(-2,-2))
        }
        status=text("Menyiapkan kamera…",13f,gold).apply { tag="status";maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END }
        lens=button("Belakang","lens") { flipCamera() }.apply {
            textSize=13f;compoundDrawablePadding=dp(4)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_flip_camera,0,0,0)
        }
        quality=button(selected.label,"quality") { chooseQuality() }.apply { textSize=13f }
        zoomButton=button("1.0×","zoom") {
            zoomDock.visibility=if(zoomDock.visibility==View.VISIBLE) View.GONE else View.VISIBLE
        }.apply { textSize=14f;setTextColor(gold);typeface=Typeface.DEFAULT_BOLD }
        zoomSeek=SeekBar(this).apply {
            tag="zoom-slider";max=1000;contentDescription="Zoom kamera"
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar,value: Int,fromUser: Boolean) {
                    if(fromUser) cameras.getOrNull(cameraIndex)?.let { c -> setZoom(c.zoomMin+(c.zoomMax-c.zoomMin)*value/1000f) }
                }
                override fun onStartTrackingTouch(bar: SeekBar) {};override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        zoomDock=LinearLayout(this).apply {
            visibility=View.GONE;gravity=Gravity.CENTER_VERTICAL
            addView(button("1×","zoom-reset") { setZoom(1f) },LinearLayout.LayoutParams(dp(48),dp(40)))
            addView(zoomSeek,LinearLayout.LayoutParams(0,dp(44),1f))
        }
        colors=button("Warna / LUT","colors") { showColors() }
        stable=button("Stabilisasi","stabilization") { showStabilization() }
        bokehButton=button("Bokeh (eksperimen)","bokeh") { showBokeh() }
        cinemaButton=button("Cinematic","cinematic") { showCinema() }
        audioButton=button("Audio","audio") { showAudio() }
        val settings=button("Pengaturan","settings") { showSettings() }
        proRow=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;visibility=if(pro) View.VISIBLE else View.GONE
            addView(row(button("Fokus / Pull","focus") { showFocus() },button("Exposure","exposure") { showExposure() },button("WB","white-balance") { showWhiteBalance() }))
        }
        record=RecordButton(this).apply {
            tag="record";text="";contentDescription="Mulai rekaman"
            minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;setPadding(0,0,0,0);stateListAnimator=null
            background=recordBackground(false);isEnabled=false
            setOnClickListener {
                if(colorDock) closeColors()
                val e=engine ?: return@setOnClickListener
                if(e.isRecording) { record.isEnabled=false;onStatus("Menyimpan dan memeriksa video…");e.stop() }
                else if(ThermalPolicy.blocksStart(thermal)) flashNote(hotStartMessage)
                else if(e.isReady && !e.busy) { record.isEnabled=false;onStatus("Memulai rekaman…");e.start(audio,audioMode,audioDeviceId) }
            }
        }
        val gallery=toolButton("Galeri",R.drawable.ic_gallery,"gallery") { showGallery() }
        moreButton=toolButton("Lainnya",R.drawable.ic_more,"more") { toggleDrawer() }
        val topRow=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            addView(lens,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            addView(quality,LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
            addView(zoomButton,LinearLayout.LayoutParams(dp(if(landscape) 58 else 76),dp(44)).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
        }
        // "Lainnya" drawer: secondary controls stay one tap away so the preview keeps most of the screen.
        panel.addView(modeSwitch,LinearLayout.LayoutParams(-1,dp(46)).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
        panel.addView(row(colors,stable));panel.addView(row(bokehButton,cinemaButton));panel.addView(proRow);panel.addView(row(audioButton,settings))
        val side=if(landscape) 64 else 84
        val recordRow=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(6),0,dp(2))
            addView(gallery,LinearLayout.LayoutParams(dp(side),dp(56)))
            addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
            addView(record,LinearLayout.LayoutParams(dp(76),dp(76)))
            addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
            addView(moreButton,LinearLayout.LayoutParams(dp(side),dp(56)))
        }
        val footer=text("Cherry Video ©2026 | totogutomo.com",10f,muted).apply { tag="footer";gravity=Gravity.CENTER;setPadding(0,dp(2),0,0) }
        if(landscape) {
            root.addView(frame,LinearLayout.LayoutParams(0,-1,1f))
            val sidebar=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,0,0)
                addView(header);addView(status);addView(topRow);addView(zoomDock)
                addView(ScrollView(this@MainActivity).apply { addView(controlHost) },LinearLayout.LayoutParams(-1,0,1f))
                addView(recordRow);addView(footer)
            }
            root.addView(sidebar,LinearLayout.LayoutParams(dp(254),-1))
        } else {
            root.addView(header);root.addView(status);root.addView(topRow);root.addView(zoomDock)
            root.addView(frame,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(4) })
            root.addView(controlHost);root.addView(recordRow);root.addView(footer)
        }
        applyModeUi();applyDrawerUi();updateLensUi();updateZoomUi()
        setContentView(root);root.requestApplyInsets();updateSelectionLabels();rebuilding=false
    }
    /** Returns false when the change was refused (recording), so the switch can snap back. */
    private fun setPro(value: Boolean): Boolean {
        if(value==pro) return true
        if(locked()) return false
        pro=value
        if(!pro) { controls=Controls(stabilization=controls.stabilization,zoom=controls.zoom);engine?.update(controls);lockToken++;updateLockUi();focusRing.visibility=View.GONE }
        prefs.edit().putBoolean("pro",pro).apply();applyModeUi()
        return true
    }
    /** Switch off = Simpel, on = Pro. Pro adds Fokus, Exposure and WB to the drawer and shows a PRO badge. */
    private fun applyModeUi() {
        modeSwitch.text=if(pro) "Mode Pro" else "Mode Simpel"
        modeSwitch.contentDescription="${modeSwitch.text}. Geser untuk berganti antara Simpel dan Pro."
        if(modeSwitch.isChecked!=pro) modeSwitch.isChecked=pro
        proBadge.visibility=if(pro) View.VISIBLE else View.GONE
        proRow.visibility=if(pro) View.VISIBLE else View.GONE
    }
    private fun toolButton(value: String,icon: Int,tagName: String,action: ()->Unit)=button(value,tagName,action).apply {
        maxLines=1;setPadding(dp(4),dp(6),dp(4),dp(6));compoundDrawablePadding=dp(2)
        setCompoundDrawablesRelativeWithIntrinsicBounds(0,icon,0,0);background=shape(surface,14)
    }
    private fun toggleDrawer() {
        if(colorDock) { closeColors();return }
        drawerOpen=!drawerOpen;applyDrawerUi()
    }
    private fun applyDrawerUi() {
        normalControls.visibility=if(drawerOpen) View.VISIBLE else View.GONE
        moreButton.text=if(drawerOpen) "Tutup" else "Lainnya"
        moreButton.contentDescription=if(drawerOpen) "Tutup menu lainnya" else "Buka menu lainnya"
        moreButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0,if(drawerOpen) R.drawable.ic_less else R.drawable.ic_more,0,0)
        moreButton.background=shape(if(drawerOpen) Color.rgb(52,63,69) else surface,14)
    }
    private fun recordBackground(active: Boolean): android.graphics.drawable.Drawable {
        val ring=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(Color.TRANSPARENT);setStroke(dp(4),Color.WHITE) }
        val core=GradientDrawable()
        if(active) core.cornerRadius=dp(6).toFloat() else core.shape=GradientDrawable.OVAL
        core.setColor(Color.rgb(255,110,99))
        return android.graphics.drawable.LayerDrawable(arrayOf(ring,core)).apply {
            val inset=dp(if(active) 22 else 10);setLayerInset(1,inset,inset,inset,inset)
        }
    }
    private fun storageLabel(): String=String.format(Locale.US,"%.1f GB kosong",StatFs(filesDir.path).availableBytes/1_073_741_824.0)
    private fun thermalLabel()=when { thermal>=3 -> "Panas";thermal>=1 -> "Suhu meningkat";else -> "Suhu normal" }
    private fun gradeLabel()=if(lut!=null) lutName else lookNames.getOrElse(look) { "Natural" }
    private fun stabilizationLabel()=when(controls.stabilization) { 1->"OIS";2->"EIS";else->"OFF" }
    private fun audioLabel()=when(audio) { 1->"Kamera";2->"Mic eksternal";else->"OFF" }
    private fun updateSelectionLabels() {
        colors.text="LUT · ${gradeLabel()}";colors.contentDescription=colors.text
        stable.text="Stabilisasi · ${stabilizationLabel()}";stable.contentDescription=stable.text
        audioButton.text="Audio · ${audioLabel()}${if(audio!=0) "\n${audioMode.label}" else ""}";audioButton.contentDescription=audioButton.text
        colorTitle?.text="${gradeLabel()} · ${(strength*100).toInt()}%"
        bokehButton.text="Bokeh (eksperimen)\n${bokehState()}${if(bokeh) " · ${BokehLook.fLabel(bokehStrength)}" else ""}";bokehButton.contentDescription=bokehButton.text
        cinemaButton.text="Cinematic\n${if(cinema) "Aktif" else "Mati"}";cinemaButton.contentDescription=cinemaButton.text
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
        if(!resumed || probing || engine!=null || retiringEngines>0 || !preview.holder.surface.isValid || cameras.isEmpty()) return
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { onStatus("Berikan izin Kamera melalui Pengaturan Android untuk merekam.");return }
        val camera=cameras[cameraIndex]
        val options=camera.profiles(hevc);qualityOptions=options
        if(options.isEmpty()) { onError("Tidak ada profil video standar yang memenuhi batas kamera dan encoder.");return }
        if(selected !in options) { selected=options.first();onStatus("Profil tersedia: ${selected.label}") }
        quality.text=selected.label;updateLensUi()
        controls=controls.copy(zoom=if(selected.highSpeed) 1f else ZoomPolicy.clamp(controls.zoom,camera.zoomMin,camera.zoomMax))
        zoomChangedAt=SystemClock.elapsedRealtime()
        updateZoomUi()
        lockToken++;controls=controls.copy(lock=false,meterX=-1f,meterY=-1f);updateLockUi();focusRing.visibility=View.GONE
        if(controls.stabilization<0 || (controls.stabilization==1 && !camera.ois) || (controls.stabilization==2 && !camera.eis))
            controls=controls.copy(stabilization=if(camera.ois) 1 else if(camera.eis) 2 else 0)
        if(selected.highSpeed) controls=controls.copy(stabilization=0,focus=null,iso=0,shutterNs=0)
        prefs.edit().putInt("width",selected.width).putInt("height",selected.height).putInt("fps",selected.fps).putInt("captureFps",selected.captureFps).apply()
        updateSelectionLabels()
        val e=CameraEngine(applicationContext,this);engine=e
        val useBokeh=bokeh && BokehPolicy.eligible(selected,camera.physical)
        if(bokeh && !useBokeh) BokehPolicy.reason(selected,camera.physical)?.let { flashNote(it) }
        e.open(camera,selected,hevc,preview.holder.surface,preview.width,preview.height,rotation(),useBokeh,bokehStrength,bokehTuning())
        e.update(controls);applyGrade(e)
    }
    private fun setZoom(ratio: Float) {
        val camera=cameras.getOrNull(cameraIndex) ?: return
        if(camera.zoomMax<=camera.zoomMin) { flashNote("Kamera ini tidak mendukung zoom.");return }
        if(selected.highSpeed) { flashNote("Zoom dinonaktifkan pada 60 fps high-speed. Pilih 30 fps untuk zoom.");return }
        if(engine?.busy==true) return
        controls=controls.copy(zoom=ZoomPolicy.clamp(ratio,camera.zoomMin,camera.zoomMax))
        zoomChangedAt=SystemClock.elapsedRealtime()
        engine?.update(controls);updateZoomUi()
    }
    /** Short on-screen message that hides itself; used when zoom is limited by the profile or the lens. */
    private fun flashNote(value: String) {
        if(!::note.isInitialized) return
        note.text=value;note.visibility=View.VISIBLE
        main.removeCallbacks(hideNote);main.postDelayed(hideNote,2600)
    }
    /** One place that sends the current controls to the camera; a manual focus or exposure choice releases the AE/AF lock. */
    private fun commit() {
        if(controls.lock && (controls.focus!=null || controls.iso>0)) { lockToken++;controls=controls.copy(lock=false);updateLockUi() }
        engine?.update(controls)
    }
    /** After Stop, saving can take a while: a short "Menyimpan…" toast appears once if it is still going after about a second. */
    private fun tickSaving(e: CameraEngine?) {
        val saving=e!=null && uiRecording && !e.isRecording && e.busy
        if(!saving) { savingSince=0L;savingShown=false;return }
        val now=SystemClock.elapsedRealtime()
        if(savingSince==0L) { savingSince=now;return }
        if(!savingShown && now-savingSince>=1000) { savingShown=true;Toast.makeText(this,"Menyimpan…",Toast.LENGTH_SHORT).show() }
    }
    private fun updateLockUi() {
        if(!::lockChip.isInitialized) return
        lockChip.visibility=if(controls.lock) View.VISIBLE else View.GONE
        focusRing.background=ring(if(controls.lock) green else Color.WHITE)
    }
    private fun ring(color: Int)=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(Color.TRANSPARENT);setStroke(dp(2),color) }
    private fun showRing(x: Float,y: Float) {
        focusRing.translationX=x-dp(32);focusRing.translationY=y-dp(32)
        focusRing.background=ring(if(controls.lock) green else Color.WHITE)
        focusRing.visibility=View.VISIBLE
        main.removeCallbacks(hideRing);if(!controls.lock) main.postDelayed(hideRing,1400)
    }
    /** Why tapping to focus cannot work right now, or null when it can. */
    private fun focusBlockedReason(camera: CameraEntry): String? = when {
        selected.highSpeed -> "Ketuk fokus tidak tersedia pada 60 fps high-speed."
        controls.focus!=null -> "Fokus manual aktif. Pilih Autofocus video di panel Fokus untuk ketuk fokus."
        (camera.characteristics[C.CONTROL_MAX_REGIONS_AF] ?: 0)<=0 && (camera.characteristics[C.CONTROL_MAX_REGIONS_AE] ?: 0)<=0 -> "Kamera ini tidak mendukung titik fokus."
        else -> null
    }
    private fun lockBlockedReason(camera: CameraEntry): String? = when {
        selected.highSpeed -> "Kunci AE/AF tidak tersedia pada 60 fps high-speed."
        controls.focus!=null || controls.iso>0 -> "Kunci AE/AF tidak dipakai saat fokus atau exposure manual (Pro)."
        else -> focusBlockedReason(camera)
    }
    private fun brightnessBlockedReason(): String? = when {
        selected.highSpeed -> "Geser terang tidak tersedia pada 60 fps high-speed."
        controls.iso>0 -> "Exposure manual aktif. Atur terang lewat panel Exposure."
        controls.lock -> "Buka kunci dahulu untuk mengatur terang."
        cameras.getOrNull(cameraIndex)?.characteristics?.get(C.CONTROL_AE_COMPENSATION_RANGE)?.let { it.upper>it.lower }!=true -> "Kamera ini tidak mendukung pengaturan terang."
        else -> null
    }
    /** Screen point to a point of the whole sensor area, following rotation, mirror, letterbox and zoom crop. */
    private fun meterPoint(x: Float,y: Float,camera: CameraEntry): Pair<Float,Float>? {
        val active=camera.characteristics[C.SENSOR_INFO_ACTIVE_ARRAY_SIZE] ?: return null
        val vp=TextureGeometry.viewport(preview.width,preview.height,selected.width,selected.height,rotation())
        if(vp[2]<=0 || vp[3]<=0) return null
        val u=(x-vp[0])/vp[2];val v=(y-vp[1])/vp[3]
        if(u<0f || u>1f || v<0f || v>1f) return null
        val s=TapMath.toSensor(u,v,rotation(),camera.front)
        val crop=ZoomPolicy.crop(active.left,active.top,active.right,active.bottom,controls.zoom.coerceAtLeast(1f))
        return TapMath.toActive(s.first,s.second,crop,active.left,active.top,active.right,active.bottom)
    }
    private fun tapFocusAt(x: Float,y: Float) {
        val camera=cameras.getOrNull(cameraIndex) ?: return
        val e=engine ?: return
        if(!e.isReady || e.busy && !e.isRecording) return
        if(controls.lock) { releaseLock(true);return }
        focusBlockedReason(camera)?.let { flashNote(it);return }
        val point=meterPoint(x,y,camera) ?: return
        controls=controls.copy(meterX=point.first,meterY=point.second)
        commit();showRing(x,y)
    }
    /** Long press: focus here, then lock AE and AF once the exposure has settled on the new spot. */
    private fun beginLock(x: Float,y: Float) {
        val camera=cameras.getOrNull(cameraIndex) ?: return
        val e=engine ?: return
        if(!e.isReady || e.busy && !e.isRecording || controls.lock) return
        lockBlockedReason(camera)?.let { flashNote(it);return }
        val point=meterPoint(x,y,camera) ?: return
        controls=controls.copy(meterX=point.first,meterY=point.second)
        commit();showRing(x,y)
        val token=++lockToken
        main.postDelayed({
            if(token==lockToken && !isDestroyed && engine?.isReady==true && lockBlockedReason(camera)==null) {
                controls=controls.copy(lock=true);engine?.update(controls);updateLockUi()
                focusRing.visibility=View.VISIBLE;flashNote("AE/AF terkunci. Ketuk layar atau tombol Buka kunci untuk melepas.")
            }
        },700)
    }
    private fun releaseLock(announce: Boolean) {
        lockToken++
        val had=controls.lock
        controls=controls.copy(lock=false,meterX=-1f,meterY=-1f)
        engine?.update(controls);updateLockUi();focusRing.visibility=View.GONE
        if(announce && had) flashNote("Kunci dibuka.")
    }
    /** Dragging up or down changes the exposure compensation by whole steps. */
    private fun dragBrightness(startEv: Int,dragPx: Float) {
        val range=cameras.getOrNull(cameraIndex)?.characteristics?.get(C.CONTROL_AE_COMPENSATION_RANGE) ?: return
        val ev=TapMath.evFromDrag(startEv,dragPx,preview.height.toFloat(),range.lower,range.upper)
        if(ev==controls.ev) return
        controls=controls.copy(ev=ev);commit()
        val step=cameras.getOrNull(cameraIndex)?.characteristics?.get(C.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f
        flashNote(String.format(Locale.US,"Terang %+.1f EV",ev*step))
    }
    /** The display must never show a ratio the camera is not using, so it follows the camera's own report. */
    private fun syncZoomFromCamera(e: CameraEngine?) {
        if(e==null || !e.isReady || e.busy || selected.highSpeed) return
        if(SystemClock.elapsedRealtime()-zoomChangedAt<1200) return
        val camera=cameras.getOrNull(cameraIndex) ?: return
        val reported=e.reportedZoom ?: return
        if(reported.isFinite() && reported>=camera.zoomMin-.01f && reported<=camera.zoomMax+.01f && kotlin.math.abs(reported-controls.zoom)>.12f) {
            controls=controls.copy(zoom=reported);updateZoomUi()
        }
    }
    private fun updateZoomUi() {
        val camera=cameras.getOrNull(cameraIndex) ?: return
        val limited=selected.highSpeed || camera.zoomMax<=camera.zoomMin
        zoomButton.text=String.format(Locale.US,"%.1f×",controls.zoom)
        zoomButton.contentDescription="Zoom ${zoomButton.text}"
        zoomBadge.text=zoomButton.text
        zoomButton.isEnabled=!limited
        zoomSeek.isEnabled=zoomButton.isEnabled
        zoomSeek.progress=if(camera.zoomMax>camera.zoomMin) ((controls.zoom-camera.zoomMin)/(camera.zoomMax-camera.zoomMin)*1000).toInt() else 0
        if(!zoomButton.isEnabled) zoomDock.visibility=View.GONE
        updateZoomChips(camera)
    }
    /** Rear camera shortcuts such as 0.6× | 1× | 2×, limited to ratios the camera supports. */
    private fun updateZoomChips(camera: CameraEntry) {
        val key="$cameraIndex"
        if(key!=chipKey) {
            chipKey=key;zoomChips.removeAllViews();chipViews.clear()
            chipValues=ZoomPolicy.presets(camera.zoomMin,camera.zoomMax,camera.front)
            chipValues.forEach { value ->
                val chip=text(ZoomPolicy.label(value),12f,Color.WHITE).apply {
                    tag="zoom-chip-${ZoomPolicy.label(value).removeSuffix("×")}";gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD
                    contentDescription="Zoom ${ZoomPolicy.label(value)}"
                    setOnClickListener { setZoom(value) }
                }
                chipViews.add(chip);zoomChips.addView(chip,LinearLayout.LayoutParams(dp(46),dp(32)).apply { setMargins(dp(1),0,dp(1),0) })
            }
            zoomChips.visibility=if(chipValues.size>=2) View.VISIBLE else View.GONE
        }
        val matched=ZoomPolicy.matchesPreset(controls.zoom,chipValues)
        chipValues.forEachIndexed { index,value ->
            val chip=chipViews[index];val active=matched==value
            chip.setTextColor(if(active) Color.rgb(16,19,21) else Color.WHITE)
            chip.background=if(active) shape(gold,16) else null
            chip.alpha=if(selected.highSpeed) .45f else 1f
        }
        zoomBadge.visibility=if(chipValues.size>=2 && matched!=null) View.GONE else View.VISIBLE
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
        updateHotUi()
        if(ThermalPolicy.blocksStart(thermal)) main.post { flashNote(hotStartMessage) }
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
        if(requestCode==1) {
            cameras=runCatching { CameraCatalog.list(this) }.getOrDefault(emptyList())
            cameraIndex=defaultIndex(prefs.getString("facing","back")=="front")
        }
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
        record.background=recordBackground(active)
        record.contentDescription=if(active) "Stop rekaman" else "Mulai rekaman"
        record.isEnabled=engine?.isReady==true
        modeSwitch.isEnabled=!active;modeSwitch.alpha=if(active) .5f else 1f
        lens.isEnabled=!active && hasBothFacings();quality.isEnabled=!active;colors.isEnabled=!active
        bokehButton.isEnabled=!active;cinemaButton.isEnabled=!active
        if(!active) { hot.reset();hotText=null;renderBanner() }
        updateHotUi()
        if(active) onStatus("Merekam · ${selected.label} · ${if(bake || cinema) "warna tersimpan" else "LUT pratinjau"}")
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
    override fun onNotice(message: String) {
        android.util.Log.w("TeGeVideo",message)
        main.post { if(!isDestroyed) flashNote(message) }
    }
    private fun locked(): Boolean {
        if(engine?.isRecording==true || engine?.busy==true) { Toast.makeText(this,"Selesaikan rekaman dahulu.",Toast.LENGTH_SHORT).show();return true };return false
    }
    /** First camera of the wanted facing. The list puts the main lens first, so this is the merged "Belakang"/"Depan". */
    private fun defaultIndex(front: Boolean): Int {
        val plain=cameras.indexOfFirst { it.front==front && !it.physical }
        if(plain>=0) return plain
        val any=cameras.indexOfFirst { it.front==front }
        return if(any>=0) any else 0
    }
    private fun hasBothFacings()=cameras.any { it.front } && cameras.any { !it.front }
    private fun updateLensUi() {
        val c=cameras.getOrNull(cameraIndex)
        val facing=if(c?.front==true) "Depan" else "Belakang"
        val test=c!=null && cameraIndex!=defaultIndex(c.front)
        lens.text=if(test) "$facing · ID ${c?.id}" else facing
        lens.contentDescription="Balik kamera. Sekarang kamera ${facing.lowercase(Locale.US)}."
        lens.isEnabled=hasBothFacings() && engine?.isRecording!=true
    }
    /** One button flips between front and back; the rear lenses are merged into the main logical camera. */
    private fun flipCamera() {
        if(locked()) return
        val target=cameras.getOrNull(cameraIndex)?.front!=true
        val index=defaultIndex(target)
        if(cameras.getOrNull(index)?.front!=target) { flashNote("Kamera ${if(target) "depan" else "belakang"} tidak tersedia.");return }
        cameraIndex=index
        prefs.edit().putString("facing",if(target) "front" else "back").apply()
        controls=Controls(stabilization=prefs.getInt("stabilization",-1));focusA=0f;focusB=0f
        lens.animate().scaleX(.82f).scaleY(.82f).setDuration(90).withEndAction { lens.animate().scaleX(1f).scaleY(1f).setDuration(110).start() }.start()
        restart()
    }
    /** Technical lens chooser kept for tests in Diagnostik. It is not saved; flipping returns to the merged camera. */
    private fun chooseTestLens(onChosen: ()->Unit) {
        if(locked()) return
        val mainCamera=cameras.firstOrNull { !it.front && !it.physical }
        val options=cameras.withIndex().filter { (_,c) -> !duplicatesMain(c,mainCamera) }
        AlertDialog.Builder(this).setTitle("Lensa teknis (uji)").setSingleChoiceItems(options.map { it.value.label }.toTypedArray(),options.indexOfFirst { it.index==cameraIndex }) { dialog,which ->
            cameraIndex=options[which].index
            controls=Controls(stabilization=prefs.getInt("stabilization",-1));focusA=0f;focusB=0f
            dialog.dismiss();onChosen();restart()
        }.setNegativeButton("Tutup",null).show()
    }
    /** A physical lens that is the same optics as the main camera (for example ID 7) only repeats it in the chooser. */
    private fun duplicatesMain(c: CameraEntry,main: CameraEntry?): Boolean {
        if(main==null || !c.physical || c.front) return false
        val a=c.equivalentFocal;val b=main.equivalentFocal
        return a>0f && b>0f && kotlin.math.abs(a-b)/b<.03f
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
        applyGrade()
        updateSelectionLabels()
    }
    /** While Cinematic is on it supplies the look, grain, vignette and bars; otherwise the normal Warna / LUT choice applies, unchanged. */
    private fun applyGrade(target: CameraEngine?=engine) {
        val e=target ?: return
        if(cinema) {
            e.grade(null,cinemaStrength,true,cinemaLook)
            e.effects(CinematicMath.grain(cinemaStrength),CinematicMath.vignette(cinemaStrength),cinemaBars,cinemaBarsInVideo)
        } else {
            e.grade(lut,strength,bake,look)
            e.effects(0f,0f,false,false)
        }
    }
    private fun bokehReason(): String? {
        val camera=cameras.getOrNull(cameraIndex) ?: return "Kamera belum siap."
        return BokehPolicy.reason(selected,camera.physical)
    }
    private fun bokehState(): String = when {
        !bokeh -> "Mati"
        bokehReason()!=null -> "Tidak aktif di profil ini"
        else -> "Aktif"
    }
    /** Turning Bokeh on or off changes the camera streams, so the camera restarts for a moment. */
    private fun setBokeh(value: Boolean) {
        if(value==bokeh) return
        if(locked()) return
        bokeh=value
        updateSelectionLabels()
        restart()
    }
    /** Opens a dock in the control area (the preview stays visible), the same way Warna / LUT does. */
    private fun openDock(panel: LinearLayout) {
        lookButtons.clear();colorTitle=null
        controlHost.removeAllViews()
        if(resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) controlHost.addView(panel)
        else {
            controlHost.layoutParams.height=dp(260)
            controlHost.addView(ScrollView(this).apply { addView(panel) },FrameLayout.LayoutParams(-1,-1))
        }
        colorDock=true;controlHost.requestLayout()
    }
    private fun dockHeader(title: String,titleTag: String,closeTag: String): LinearLayout = LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(text(title,14f,gold).apply { tag=titleTag;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END },LinearLayout.LayoutParams(0,dp(38),1f))
        addView(button("Tutup",closeTag) { closeColors() },LinearLayout.LayoutParams(dp(64),dp(38)))
    }
    private fun bokehTuning()=BokehTuning(bokehStrength,bokehSensitivity,bokehSmooth,bokehFeather,bokehGrade,bokehShowMask)
    private fun persistBokeh() {
        prefs.edit().putFloat("bokehStrength",bokehStrength).putFloat("bokehSensitivity",bokehSensitivity).putBoolean("bokehSmooth",bokehSmooth)
            .putFloat("bokehFeather",bokehFeather).putFloat("bokehGrade",bokehGrade).putString("bokehPreset",bokehPreset?.name ?: "").apply()
        engine?.setBokehStrength(bokehStrength);engine?.tuneBokeh(bokehTuning())
    }
    private fun bokehStrengthLabel()="Bukaan ${BokehLook.fLabel(bokehStrength)} · blur ${(bokehStrength*100).toInt()}%"
    private fun showBokeh() {
        if(locked()) return
        val why=bokehReason()
        val panel=body()
        panel.tag="bokeh-dock"
        panel.addView(dockHeader("Bokeh (eksperimen)","bokeh-title","close-bokeh"))
        check(panel,"Bokeh latar (hanya orang)",bokeh && why==null,why==null) { value -> setBokeh(value) }
        // Presets set the F value, the soft edge and the graded blur together; sensitivity and the mask view stay as they are.
        val presetRow=LinearLayout(this)
        val presetButtons=ArrayList<Pair<BokehPreset,Button>>()
        fun refreshPresets() { presetButtons.forEach { (preset,b) ->
            val on=bokehPreset==preset
            b.background=shape(if(on) gold else Color.rgb(34,42,47),10);b.setTextColor(if(on) Color.rgb(16,19,21) else Color.WHITE)
        } }
        BokehPreset.entries.forEach { preset ->
            val b=button(preset.label,"bokeh-preset-${preset.name.lowercase(Locale.US)}") {
                val t=preset.apply(bokehTuning())
                bokehStrength=t.strength;bokehFeather=t.feather;bokehGrade=t.grade;bokehSmooth=t.smooth;bokehPreset=preset
                persistBokeh();onStatus("Bokeh: preset ${preset.label}");showBokeh()
            }
            presetButtons.add(preset to b);presetRow.addView(b,LinearLayout.LayoutParams(0,dp(40),1f).apply { rightMargin=dp(5) })
        }
        refreshPresets();panel.addView(presetRow)
        slider(panel,bokehStrengthLabel(),(bokehStrength*100).toInt(),100) { value,label ->
            bokehStrength=value/100f;bokehPreset=null;label.text=bokehStrengthLabel();persistBokeh();refreshPresets();updateSelectionLabels()
        }
        slider(panel,"Sensitivitas deteksi orang ${(bokehSensitivity*100).toInt()}%",(bokehSensitivity*100).toInt(),100) { value,label ->
            bokehSensitivity=value/100f;label.text="Sensitivitas deteksi orang $value%";persistBokeh()
        }
        panel.addView(text("Turunkan bila benda (motor, meja) ikut tajam; naikkan bila bagian tubuh ikut buram.",12f,muted))
        panel.addView(text("Ikut gerak",14f))
        val speeds=ArrayList<Pair<Boolean,Button>>()
        fun refreshSpeed() { speeds.forEach { (smooth,b) ->
            val on=bokehSmooth==smooth
            b.background=shape(if(on) gold else Color.rgb(34,42,47),10);b.setTextColor(if(on) Color.rgb(16,19,21) else Color.WHITE)
        } }
        val speedRow=LinearLayout(this)
        listOf("Halus" to true,"Cepat" to false).forEach { (name,smooth) ->
            val b=button(name,"bokeh-speed-${if(smooth) "smooth" else "fast"}") { bokehSmooth=smooth;bokehPreset=null;persistBokeh();refreshSpeed();refreshPresets() }
            speeds.add(smooth to b);speedRow.addView(b,LinearLayout.LayoutParams(0,dp(40),1f).apply { rightMargin=dp(5) })
        }
        refreshSpeed();panel.addView(speedRow)
        check(panel,"Lihat topeng (uji, tidak masuk video)",bokehShowMask) { value -> bokehShowMask=value;engine?.tuneBokeh(bokehTuning()) }
        panel.addView(text(why ?: "Latar diburamkan, orang tetap tajam. Tepi dibuat lembut dan blur bertambah perlahan menjauhi orang. Nilai F hanya simulasi (lensa HP tidak berubah). Hanya 1080p atau 720p 30 fps; 60 fps dan 4K tidak memakai Bokeh. Mengubah sakelar memulai ulang kamera sebentar. Bila tidak ada orang di gambar, video tetap tajam.",12f,muted))
        openDock(panel)
    }
    private fun showCinema() {
        if(locked()) return
        val panel=body()
        panel.tag="cinema-dock"
        panel.addView(dockHeader("Cinematic","cinema-title","close-cinema"))
        check(panel,"Cinematic Look",cinema) { value ->
            cinema=value;persistCinema();onStatus(if(value) "Cinematic aktif" else "Cinematic mati")
        }
        val looks=ArrayList<Pair<Int,Button>>()
        fun refreshLooks() { looks.forEach { (id,b) ->
            val on=cinemaLook==id
            b.background=shape(if(on) gold else Color.rgb(34,42,47),10);b.setTextColor(if(on) Color.rgb(16,19,21) else Color.WHITE)
        } }
        val lookRow=LinearLayout(this)
        listOf("Cinematic 1" to 4,"Cinematic 2" to 5).forEach { (name,id) ->
            val b=button(name,"cinema-look-$id") { cinemaLook=id;persistCinema();refreshLooks() }
            looks.add(id to b);lookRow.addView(b,LinearLayout.LayoutParams(0,dp(40),1f).apply { rightMargin=dp(5) })
        }
        refreshLooks()
        panel.addView(lookRow)
        slider(panel,"Kekuatan ${(cinemaStrength*100).toInt()}%",(cinemaStrength*100).toInt(),100) { value,label ->
            cinemaStrength=value/100f;label.text="Kekuatan $value%";persistCinema()
        }
        check(panel,"Bilah 2,39:1 di preview",cinemaBars) { value ->
            cinemaBars=value;persistCinema()
            panel.findViewWithTag<View>("cinema-bars-video")?.isEnabled=value
        }
        check(panel,"Bilah ikut masuk ke video",cinemaBarsInVideo,cinemaBars) { value -> cinemaBarsInVideo=value;persistCinema() }
        panel.getChildAt(panel.childCount-1).tag="cinema-bars-video"
        panel.addView(text("Cinematic menambah warna film, grain, dan vinyet tipis; semuanya ikut masuk ke video. Saat Cinematic aktif, pilihan Warna / LUT disimpan dan dipakai kembali setelah Cinematic dimatikan.",12f,muted))
        openDock(panel)
    }
    private fun persistCinema() {
        prefs.edit().putInt("cinemaLook",cinemaLook).putFloat("cinemaStrength",cinemaStrength)
            .putBoolean("cinemaBars",cinemaBars).putBoolean("cinemaBarsInVideo",cinemaBarsInVideo).apply()
        applyGrade();updateSelectionLabels()
    }
    private fun renderBanner() {
        if(!::banner.isInitialized) return
        val shown=hotText ?: fpsText
        banner.text=shown ?: ""
        banner.visibility=if(shown==null) View.GONE else View.VISIBLE
    }
    /** Recording: severe heat starts a 30 second countdown, then the video is stopped and saved. */
    private fun tickHot(e: CameraEngine?) {
        if(e?.isRecording==true) {
            val state=hot.update(thermal,SystemClock.elapsedRealtime())
            when(state.action) {
                HotAction.COUNTING -> hotText="HP panas, video akan dimatikan dalam ${state.remainingSeconds} detik"
                HotAction.STOP -> {
                    hotText=null;hot.reset();e.stop()
                    onStatus("Rekaman dihentikan karena HP panas. Video sedang disimpan.")
                }
                HotAction.NONE -> hotText=null
            }
        } else { hot.reset();hotText=null }
        renderBanner();updateHotUi()
    }
    /** While recording with Bokeh: warning text only when the real frame rate drops well below the chosen one. */
    private fun tickFps(e: CameraEngine?) {
        if(e==null || !e.isRecording || !e.bokehActive) {
            fpsMarkAt=0L;fpsLow=0
            if(fpsText!=null) { fpsText=null;renderBanner() }
            return
        }
        val now=SystemClock.elapsedRealtime();val frames=e.renderedFrames
        if(fpsMarkAt==0L) { fpsMarkAt=now;fpsMarkFrames=frames;return }
        val elapsed=now-fpsMarkAt
        if(elapsed<1500) return
        val fps=(frames-fpsMarkFrames)*1000.0/elapsed
        fpsMarkAt=now;fpsMarkFrames=frames
        if(fps<selected.fps*.85) fpsLow++ else fpsLow=0
        val warn=if(fpsLow>=2) String.format(Locale.US,"FPS turun: %.0f dari %d. Rekaman bisa tersendat.",fps,selected.fps) else null
        if(warn!=fpsText) { fpsText=warn;renderBanner() }
    }
    /** On the home screen the record button looks dimmed (but stays tappable) while the phone is hot. */
    private fun updateHotUi() {
        if(!::record.isInitialized) return
        (record as? RecordButton)?.softLocked=!uiRecording && ThermalPolicy.blocksStart(thermal)
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
        lookNames.forEachIndexed { index,name ->
            val b=button(name,"look-$index") { lut=null;lutName="";look=index;persistGrade();onStatus("Warna: $name") }
            lookButtons.add(b);lookRow.addView(b,LinearLayout.LayoutParams(dp(if(index>=4) 104 else 88),dp(40)).apply { rightMargin=dp(5) })
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
        val panel=body()
        panel.addView(text("Input audio",15f,gold))
        val sources=RadioGroup(this)
        val sourceIds=IntArray(3) { View.generateViewId() }
        listOf("Tanpa audio","Mikrofon kamera","Mic eksternal").forEachIndexed { index,name ->
            sources.addView(RadioButton(this).apply { id=sourceIds[index];text=name;tag="audio-source-$index";setTextColor(Color.WHITE) })
        }
        sources.check(sourceIds[audio.coerceIn(0,2)])
        sources.setOnCheckedChangeListener { _,id ->
            val index=sourceIds.indexOf(id)
            if(index>0 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),2)
            else if(index>=0) { audio=index;prefs.edit().putInt("audio",audio).apply();updateSelectionLabels() }
        }
        panel.addView(sources)
        panel.addView(button("Pilih perangkat mic eksternal","external-input") { chooseExternalInput() },LinearLayout.LayoutParams(-1,dp(44)))
        panel.addView(text("Mic wireless dengan penerima USB mengikuti input USB. Bluetooth memerlukan profil mikrofon, izin, dan dukungan Android; perangkat yang gagal menjadi input aktif akan dilaporkan.",12f,muted))
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            panel.addView(button("Izinkan mic Bluetooth","bluetooth-permission") { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),3) },LinearLayout.LayoutParams(-1,dp(44)))
        panel.addView(text("Pengolahan suara",15f,gold))
        val modes=RadioGroup(this)
        val ids=AudioMode.entries.map { View.generateViewId() }
        AudioMode.entries.forEachIndexed { index,value ->
            modes.addView(RadioButton(this).apply { id=ids[index];text=value.label;tag="audio-mode-$index";setTextColor(Color.WHITE)
                isEnabled=value==AudioMode.NATURAL || AudioProcessor.available })
        }
        modes.check(ids[audioMode.ordinal])
        modes.setOnCheckedChangeListener { _,id ->
            val index=ids.indexOf(id)
            if(index>=0) { audioMode=AudioMode.entries[index];prefs.edit().putInt("audioMode",index).apply();updateSelectionLabels() }
        }
        panel.addView(modes)
        panel.addView(text("Natural: tanpa peredam tambahan. Suara Jernih: reduksi sedang. Reduksi Kuat: lebih agresif, dapat mengurangi detail suara dan suasana. Pilih sebelum merekam.",12f,muted))
        if(!AudioProcessor.available) panel.addView(text("Pengolah suara tidak tersedia pada perangkat ini. Gunakan Natural.",13f,gold))
        sheet("Audio",panel)
    }
    private fun chooseExternalInput() {
        val devices=AudioInputs.devices(this).filter { AudioInputs.external(it) }
        if(devices.isEmpty()) { message("Mic eksternal","Belum ada input eksternal yang dilaporkan Android. Sambungkan penerima USB/kabel atau hubungkan mic Bluetooth, lalu buka kembali pilihan ini.");return }
        AlertDialog.Builder(this).setTitle("Pilih mic eksternal").setSingleChoiceItems(devices.map { AudioInputs.name(it) }.toTypedArray(),devices.indexOfFirst { it.id==audioDeviceId }) { dialog,index ->
            audioDeviceId=devices[index].id;audio=2
            prefs.edit().putInt("audio",audio).putInt("audioDeviceId",audioDeviceId).apply()
            updateSelectionLabels();dialog.dismiss();onStatus("Mic dipilih: ${AudioInputs.name(devices[index])}. Input aktif diverifikasi saat rekam.")
        }.setNegativeButton("Tutup",null).show()
    }

    private fun showFocus() {
        if(selected.highSpeed) { message("Fokus","Sesi high-speed memakai autofocus otomatis. Gunakan 30 fps untuk kontrol fokus manual.");return }
        val c=cameras.getOrNull(cameraIndex) ?: return
        if(c.minFocus<=0) { message("Fokus","Kamera ini tidak menawarkan lensa fokus yang bisa digerakkan.");return }
        val panel=body()
        panel.tag="focus-dock"
        panel.addView(dockHeader("Fokus & focus pull","focus-title","close-focus"))
        panel.addView(button("Autofocus video","autofocus") { controls=controls.copy(focus=null);commit() },LinearLayout.LayoutParams(-1,dp(46)))
        slider(panel,"Fokus manual · ∞ sampai dekat",(((controls.focus ?: 0f)/c.minFocus)*1000).toInt(),1000) { v,label ->
            val d=v/1000f*c.minFocus;controls=controls.copy(focus=d);commit()
            label.text=if(d<.001f) "Fokus ∞" else String.format(Locale.US,"Fokus %.2f diopter",d)
        }
        val marks=text("A: $focusA D   →   B: $focusB D",14f,gold);panel.addView(marks)
        panel.addView(row(button("Simpan A","mark-a") { focusA=controls.focus ?: 0f;marks.text="A: $focusA D → B: $focusB D" },button("Simpan B","mark-b") { focusB=controls.focus ?: 0f;marks.text="A: $focusA D → B: $focusB D" }))
        slider(panel,"Durasi ${pullDuration/1000f} detik",(pullDuration/100).toInt()-5,95) { v,label -> pullDuration=(v+5)*100L;label.text="Durasi ${pullDuration/1000f} detik" }
        panel.addView(row(button("A → B","pull-ab") { controls=controls.copy(focus=focusB);commitPull(focusA,focusB) },button("B → A","pull-ba") { controls=controls.copy(focus=focusA);commitPull(focusB,focusA) }))
        panel.addView(text("Tandai A dan B sambil melihat ketajaman gambar. Kecepatan gerak lensa tetap bergantung pada motor fokus kamera.",12f,muted))
        openDock(panel)
    }
    /** A focus pull is manual focus, so it releases the AE/AF lock first. */
    private fun commitPull(from: Float,to: Float) {
        if(controls.lock) { lockToken++;controls=controls.copy(lock=false);updateLockUi() }
        engine?.pull(from,to,pullDuration)
    }
    private fun showExposure() {
        if(selected.highSpeed) { message("Exposure","Sesi high-speed memakai exposure otomatis. Gunakan 30 fps untuk ISO dan shutter manual.");return }
        val c=cameras.getOrNull(cameraIndex) ?: return
        val panel=body()
        panel.tag="exposure-dock"
        panel.addView(dockHeader("Exposure","exposure-title","close-exposure"))
        check(panel,"Exposure manual",controls.iso>0,c.manual) { enabled ->
            controls=if(enabled) controls.copy(iso=c.characteristics[C.SENSOR_INFO_SENSITIVITY_RANGE]?.clamp(400) ?: 400,shutterNs=1_000_000_000L/(selected.fps*2)) else controls.copy(iso=0,shutterNs=0)
            commit()
        }
        val isoRange=c.characteristics[C.SENSOR_INFO_SENSITIVITY_RANGE]
        if(c.manual && isoRange!=null) {
            slider(panel,"ISO ${if(controls.iso>0) controls.iso else "Auto"}",((controls.iso.takeIf { it>0 } ?: 400)-isoRange.lower).coerceAtLeast(0),isoRange.upper.coerceAtMost(6400)-isoRange.lower) { value,label ->
                controls=controls.copy(iso=value+isoRange.lower,shutterNs=controls.shutterNs.takeIf { it>0 } ?: 1_000_000_000L/(selected.fps*2));commit();label.text="ISO ${controls.iso}"
            }
            panel.addView(button("Shutter: ${if(controls.shutterNs>0) "1/${1_000_000_000L/controls.shutterNs}" else "Auto"}","shutter") {
                val denominators=listOf(30,60,100,120,250,500,1000).filter { it>=selected.fps }
                AlertDialog.Builder(this).setTitle("Shutter").setItems(denominators.map { "1/$it detik" }.toTypedArray()) { _,index ->
                    controls=controls.copy(iso=controls.iso.takeIf { it>0 } ?: isoRange.clamp(400),shutterNs=1_000_000_000L/denominators[index]);commit();onStatus("Shutter 1/${denominators[index]}")
                }.show()
            },LinearLayout.LayoutParams(-1,dp(48)))
        }
        val evRange=c.characteristics[C.CONTROL_AE_COMPENSATION_RANGE]
        if(evRange!=null) slider(panel,"Kompensasi exposure (Auto)",controls.ev-evRange.lower,evRange.upper-evRange.lower) { value,label ->
            controls=controls.copy(ev=value+evRange.lower,iso=0,shutterNs=0);commit()
            val step=c.characteristics[C.CONTROL_AE_COMPENSATION_STEP]?.toFloat() ?: 0f
            label.text=String.format(Locale.US,"Exposure %+.1f EV",controls.ev*step)
        }
        check(panel,"Kunci exposure otomatis",controls.aeLock,c.characteristics[C.CONTROL_AE_LOCK_AVAILABLE]==true) { controls=controls.copy(aeLock=it);commit() }
        panel.addView(text("Exposure manual mengatur ISO dan shutter bersamaan. Shutter dibatasi agar tidak melebihi satu periode frame. Terang juga bisa diatur dengan menggeser jari naik/turun di preview.",12f,muted))
        openDock(panel)
    }
    private fun showWhiteBalance() {
        if(selected.highSpeed) { message("White balance","Sesi high-speed memakai white balance otomatis.");return }
        val c=cameras.getOrNull(cameraIndex) ?: return
        val panel=body()
        panel.tag="wb-dock"
        panel.addView(dockHeader("White balance","wb-title","close-wb"))
        val available=c.characteristics[C.CONTROL_AWB_AVAILABLE_MODES] ?: intArrayOf()
        val options=listOf("Auto" to 1,"Siang" to 5,"Berawan" to 6,"Teduh" to 8,"Lampu pijar" to 2,"Fluoresen" to 3)
        options.filter { it.second in available }.forEach { (name,value) ->
            panel.addView(button(name,"wb-$value") { controls=controls.copy(wb=value,awbLock=false);commit();onStatus("White balance: $name") },LinearLayout.LayoutParams(-1,dp(44)).apply { bottomMargin=dp(4) })
        }
        check(panel,"Kunci AWB",controls.awbLock,c.characteristics[C.CONTROL_AWB_LOCK_AVAILABLE]==true) { controls=controls.copy(awbLock=it);commit() }
        openDock(panel)
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
        panel.addView(text("Penyimpanan: Movies/CherryVideo\n${storageLabel()}\nSaat aplikasi masuk latar atau layar dikunci, rekaman dihentikan dan disimpan. Jaga aplikasi tetap terbuka untuk merekam. Suhu berat memberi hitungan mundur 30 detik sebelum rekaman disimpan otomatis; suhu kritis dan ruang menipis menyimpan segera. Saat HP panas, rekaman baru tidak bisa dimulai sampai suhu turun.",13f,muted))
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
        panel.addView(text("Peredam suara: SpeexDSP, lisensi BSD. Pemrosesan lokal di perangkat.",14f))
        panel.addView(text("Bokeh dan Uji Bokeh: MediaPipe Tasks Vision dan model Selfie Segmenter.\nCopyright Google LLC.\nApache License 2.0. Berjalan di perangkat, tanpa internet.",14f))
        panel.addView(button("Lisensi SpeexDSP","speex-license") { message("Lisensi SpeexDSP",assets.open("SPEEXDSP-LICENSE").bufferedReader().use { it.readText() }) },LinearLayout.LayoutParams(-1,dp(46)))
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
            .put("audioInputs",AudioInputs.catalog(this)).put("audioProcessorAvailable",AudioProcessor.available)
            .put("audioModeSelected",audioMode.name).put("requestedAudioDeviceId",audioDeviceId)
            .put("zoomSelected",controls.zoom.toDouble()).put("zoomApplied",engine?.appliedZoom?.toDouble())
            .put("activePhysicalId",engine?.activePhysicalId ?: JSONObject.NULL)
            .put("physicalCameras",CameraCatalog.physicalReport(this,cameras)).put("preview",engine?.previewStats).put("bokeh",engine?.bokehInfo ?: JSONObject.NULL)
            .put("selectedCamera",cameras.getOrNull(cameraIndex)?.id).put("selectedProfile",selected.label).put("codec",if(hevc) "HEVC" else "AVC")
            .put("lastRecording",last?.let { JSONObject(it) } ?: JSONObject.NULL)
            .put("lastRecordingAttempt",attempt?.let { JSONObject(it) } ?: JSONObject.NULL).toString(2)
        val panel=body()
        val p=engine?.previewStats ?: JSONObject()
        panel.addView(text("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid API ${Build.VERSION.SDK_INT} · ${BuildConfig.VERSION_NAME}",15f,gold))
        panel.addView(text("Kamera ${cameras.getOrNull(cameraIndex)?.id} · ${selected.label} · ${if(hevc) "HEVC" else "AVC"}\n${String.format(Locale.US,"Preview %.1f fps · render %.1f ms\nJeda frame maksimum %.1f ms",p.optDouble("previewSubmittedFps",0.0),p.optDouble("averageRenderMs",0.0),p.optDouble("maxArrivalGapMs",0.0))}",14f))
        panel.addView(text("Audio: ${audioLabel()} · ${audioMode.label}\nPengolah suara: ${if(AudioProcessor.available) "tersedia" else "tidak tersedia"}\nZoom: ${String.format(Locale.US,"%.1f×",controls.zoom)}",13f))
        val timing=attempt?.let { runCatching { JSONObject(it).optJSONObject("recorderTiming") }.getOrNull() }
        timing?.let { panel.addView(text("Percobaan terakhir: ${it.optString("audioModeLabel")}\nInput aktif: ${it.optJSONObject("actualInput")?.optString("name") ?: "belum diverifikasi"}\nKanal: ${it.optInt("channels")} · Level masuk: ${it.optJSONArray("inputRmsDbfs")} dBFS\nLevel keluar: ${it.optJSONArray("outputRmsDbfs")} dBFS",12f,muted)) }
        val mainCamera=cameras.firstOrNull { !it.front && !it.physical }
        cameras.forEach { c ->
            panel.addView(text("${c.label}${if(duplicatesMain(c,mainCamera)) " (lensa sama dengan Utama)" else ""} · zoom ${c.zoomMin}–${c.zoomMax}×\nSensor ${c.rotation}° · fokus 0–${c.minFocus} D\nOIS ${c.ois} · EIS ${c.eis}\nAVC: ${c.profiles(false).joinToString { it.label }}\nHEVC: ${c.profiles(true).joinToString { it.label }}\nHigh-speed 1080: ${c.highSpeedRates(1920,1080).joinToString().ifEmpty { "tidak dilaporkan" }}",13f))
        }
        var diagnosticDialog: AlertDialog?=null
        panel.addView(button("Pilih lensa teknis (uji)","test-lens") { diagnosticDialog?.dismiss();chooseTestLens {} },LinearLayout.LayoutParams(-1,dp(46)))
        panel.addView(button("Uji telefoto (±40 detik)","tele-probe") { diagnosticDialog?.dismiss();runTelephotoProbe() },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        panel.addView(button("Uji Bokeh (±15 detik)","bokeh-probe") { diagnosticDialog?.dismiss();runBokehProbe() },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        panel.addView(button("Uji mode bokeh sistem","system-bokeh-probe") { diagnosticDialog?.dismiss();runSystemBokehProbe() },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        panel.addView(button("JSON lengkap","diagnostic-json") { message("Diagnostik JSON",exportJson) },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        panel.addView(button("Ekspor JSON","export-diagnostic") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"CherryVideo-diagnostik.json") },101)
        },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        diagnosticDialog=sheet("Diagnostik",panel)
    }
    /** Releases the app's camera, lets [TelephotoProbe] investigate every candidate lens, then restarts the camera. */
    private fun runTelephotoProbe()=runProbe(ProbeKind("Uji telefoto","Hasil uji telefoto","Menguji telefoto… jangan tutup aplikasi (±40 detik).",
        "telephoto-probe.json","CherryVideo-uji-telefoto.json") { TelephotoProbe.run(it) })
    /** Releases the app's camera, lets [BokehProbe] measure on-device person segmentation (no recording), then restarts the camera. */
    private fun runBokehProbe()=runProbe(ProbeKind("Uji Bokeh","Hasil uji Bokeh","Uji Bokeh… arahkan kamera belakang ke orang, jangan tutup aplikasi (±15 detik).",
        "bokeh-probe.json","CherryVideo-uji-bokeh.json") { BokehProbe.run(it) })
    /** Only reads what the camera reports (system bokeh / nilai F for other apps); no recording. */
    private fun runSystemBokehProbe()=runProbe(ProbeKind("Uji mode bokeh sistem","Hasil uji mode bokeh sistem","Membaca dukungan bokeh sistem…",
        "system-bokeh-probe.json","CherryVideo-uji-bokeh-sistem.json") { SystemBokehProbe.run(it) })
    private class ProbeKind(val name: String,val resultTitle: String,val status: String,val saveName: String,val exportName: String,val task: (Context)->JSONObject)
    private fun runProbe(kind: ProbeKind) {
        if(locked() || probing) return
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { onError("Berikan izin Kamera melalui Pengaturan Android dahulu.");return }
        probing=true;record.isEnabled=false
        onStatus(kind.status)
        val old=engine
        if(old==null) startProbe(kind) else { engine=null;retiringEngines++;old.close { retiringEngines--;startProbe(kind) } }
    }
    private fun startProbe(kind: ProbeKind) {
        val app=applicationContext
        io.execute {
            val result=runCatching { kind.task(app) }
            main.post {
                probing=false
                if(isDestroyed) return@post
                result.onSuccess { json -> showProbeResult(kind,json) }.onFailure { e -> onError("${kind.name} gagal: ${e.message}") }
                restart()
            }
        }
    }
    private fun showProbeResult(kind: ProbeKind,json: JSONObject) {
        exportJson=json.toString(2)
        runCatching { File(filesDir,kind.saveName).writeText(exportJson) }
        val panel=body().apply { tag="probe-result" }
        panel.addView(text(json.optString("summary","Tidak ada ringkasan."),14f).apply { setTextIsSelectable(true) })
        panel.addView(button("JSON lengkap","probe-json") { message("${kind.name} · JSON",exportJson) },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(8) })
        panel.addView(button("Ekspor JSON","probe-export") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="application/json";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,kind.exportName) },101)
        },LinearLayout.LayoutParams(-1,dp(46)).apply { topMargin=dp(5) })
        sheet(kind.resultTitle,panel)
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
        else if(colorDock) closeColors()
        else if(drawerOpen) { drawerOpen=false;applyDrawerUi() }
        else super.onBackPressed()
    }
}

/** Round shutter control; dims itself while the camera is not ready. */
private class RecordButton(context: Context): Button(context) {
    /** Dimmed but still tappable, so a tap can explain why recording cannot start. */
    var softLocked=false
        set(value) { field=value;shade() }
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled);shade() }
    private fun shade() { alpha=if(isEnabled && !softLocked) 1f else .45f }
}

private class GridOverlay(context: Context): View(context) {
    var enabledGrid=false
    private val paint=Paint().apply { color=Color.argb(75,255,255,255);strokeWidth=resources.displayMetrics.density }
    override fun onDraw(canvas: Canvas) {
        if(!enabledGrid) return
        for(i in 1..2) { canvas.drawLine(width*i/3f,0f,width*i/3f,height.toFloat(),paint);canvas.drawLine(0f,height*i/3f,width.toFloat(),height*i/3f,paint) }
    }
}
