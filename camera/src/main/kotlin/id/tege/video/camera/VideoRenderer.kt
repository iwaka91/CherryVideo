package id.tege.video.camera

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import id.tege.video.core.CubeLut
import id.tege.video.core.FrameClock
import id.tege.video.core.FrameGate
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** One GL owner. The exact same grade feeds preview and encoder; monitoring-only bypass is explicit. */
class VideoRenderer(
    private val preview: Surface,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val sensorRotation: Int? = null,
    private val producerMirrored: Boolean = false,
    timestampOffsetNs: Long = 0L,
    private val outputFps: Int = 30,
    private val onPreviewReady: () -> Unit = {},
    private val onFailure: (String) -> Unit,
) : AutoCloseable {
    private val thread=HandlerThread("Cherry-GL",android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler=Handler(thread.looper)
    private var display=EGL14.EGL_NO_DISPLAY
    private var context=EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig?=null
    private var window=EGL14.EGL_NO_SURFACE
    private var encoder=EGL14.EGL_NO_SURFACE
    private var encoding=false
    private var program=0
    private var oes=0
    private var lutTexture=0
    private lateinit var texture: SurfaceTexture
    lateinit var input: Surface; private set
    private var width=1
    private var height=1
    private var rotation=90
    private var mirrored=false
    private var lut: CubeLut?=null
    private var strength=1f
    private var bake=true
    private var look=0
    private var lastTimestamp=0L
    private val frameClock=FrameClock(timestampOffsetNs)
    private var encoderGate=FrameGate(outputFps)
    private val previewGate=FrameGate(60)
    private var frameScheduled=false
    private var previewReady=false
    private var statsStart=0L
    private var statsFrames=0
    private var statsRenderNs=0L
    private var lastArrival=0L
    private var statsMaxGapNs=0L
    @Volatile var stats=JSONObject();private set
    private val locations=HashMap<String,Int>()
    private var closed=false
    @Volatile var renderedFrames=0L; private set
    @Volatile var lastEncodedTimestampNs=0L; private set
    @Volatile var firstEncodedTimestampNs=0L; private set
    private val uv=FloatArray(16)
    private val mvp=FloatArray(16)
    private val vertices=ByteBuffer.allocateDirect(4*4*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0)
    }
    init { sync { initialize() } }
    private fun <T> sync(block: ()->T): T {
        if(Thread.currentThread()===thread) return block()
        val task=FutureTask<T> { block() }
        check(handler.post(task)) { "GL thread is closed" }
        return task.get(12,TimeUnit.SECONDS)
    }
    private fun initialize() {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0)) { "EGL initialize failed" }
        val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,0x3142,1,EGL14.EGL_NONE)
        val configs=arrayOfNulls<EGLConfig>(1)
        check(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,IntArray(1),0) && configs[0]!=null) { "GLES 3 recordable surface unavailable" }
        config=configs[0]
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
        check(context!=EGL14.EGL_NO_CONTEXT) { "GLES 3 unavailable" }
        window=EGL14.eglCreateWindowSurface(display,config,preview,intArrayOf(EGL14.EGL_NONE),0)
        check(window!=EGL14.EGL_NO_SURFACE) { "Preview EGL surface unavailable" }
        current(window)
        program=link(VERTEX,FRAGMENT)
        val ids=IntArray(2); GLES30.glGenTextures(2,ids,0); oes=ids[0]; lutTexture=ids[1]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE)
        texture=SurfaceTexture(oes).apply {
            setDefaultBufferSize(sourceWidth,sourceHeight)
            // Coalesce pending notifications and latch the newest buffer rather than replay a queue.
            setOnFrameAvailableListener({
                if(!frameScheduled && !closed) { frameScheduled=true;handler.post { frameScheduled=false;render() } }
            },handler)
        }
        input=Surface(texture)
        uploadIdentity()
        checkGl()
    }
    private fun uploadIdentity() {
        uploadCube(2,floatArrayOf(0f,0f,0f, 1f,0f,0f, 0f,1f,0f, 1f,1f,0f,
            0f,0f,1f, 1f,0f,1f, 0f,1f,1f, 1f,1f,1f))
    }
    private fun uploadCube(size: Int, values: FloatArray) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,lutTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
        for(axis in intArrayOf(GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_TEXTURE_WRAP_T))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,axis,GLES30.GL_CLAMP_TO_EDGE)
        // Stack blue slices vertically. The largest supported atlas is only 33 x 1089.
        // Bilinear sampling within each slice plus explicit blue interpolation matches
        // CubeLut.sample, without relying on the emulator/driver's 3D texture path.
        val rgba=FloatArray(size*size*size*4)
        for(i in 0 until size*size*size) {
            rgba[i*4]=values[i*3];rgba[i*4+1]=values[i*3+1];rgba[i*4+2]=values[i*3+2];rgba[i*4+3]=1f
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT,1)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_BASE_LEVEL,0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAX_LEVEL,0)
        val buffer=ByteBuffer.allocateDirect(rgba.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(rgba)
        buffer.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RGBA16F,size,size*size,0,GLES30.GL_RGBA,GLES30.GL_FLOAT,buffer)
        checkGl()
    }
    fun resize(w: Int,h: Int,degrees: Int,front: Boolean) { sync { width=w.coerceAtLeast(1);height=h.coerceAtLeast(1);rotation=degrees;mirrored=front } }
    fun grade(value: CubeLut?, intensity: Float, recordLut: Boolean, builtIn: Int) { sync {
        current(window)
        if(value?.sha256!=lut?.sha256) { if(value==null) uploadIdentity() else uploadCube(value.size,value.values) }
        lut=value;strength=intensity.coerceIn(0f,1f);bake=recordLut;look=builtIn
    } }
    fun attachEncoder(surface: Surface) { sync {
        check(encoder==EGL14.EGL_NO_SURFACE)
        encoder=EGL14.eglCreateWindowSurface(display,config,surface,intArrayOf(EGL14.EGL_NONE),0)
        check(encoder!=EGL14.EGL_NO_SURFACE) { "Encoder EGL surface unavailable" }
        encoderGate=FrameGate(outputFps);renderedFrames=0L;lastEncodedTimestampNs=0L;firstEncodedTimestampNs=0L;encoding=true
    } }
    /** Keep the BufferQueue connected until the recorder has drained its pending frames. */
    fun pauseEncoder() { sync {
        encoding=false
        if(encoder!=EGL14.EGL_NO_SURFACE) { current(encoder);GLES30.glFinish();checkGl() }
    } }
    fun detachEncoder() { sync {
        encoding=false
        if(encoder!=EGL14.EGL_NO_SURFACE) {
            // The Activity preview may already have been destroyed while backgrounding.
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,encoder); encoder=EGL14.EGL_NO_SURFACE
        }
    } }
    private fun render() {
        if(closed) return
        try {
            val started=System.nanoTime()
            current(window); texture.updateTexImage(); texture.getTransformMatrix(uv)
            val stamp=texture.timestamp
            if(stamp<=lastTimestamp) return
            lastTimestamp=stamp
            var previewDrawn=false
            if(previewGate.accept(stamp)) {
                draw(window,width,height,true)
                check(EGL14.eglSwapBuffers(display,window)) { "Preview swap failed" };previewDrawn=true
                if(!previewReady) { previewReady=true;onPreviewReady() }
            }
            if(encoding && encoder!=EGL14.EGL_NO_SURFACE && encoderGate.accept(stamp)) {
                draw(encoder,sourceWidth,sourceHeight,false)
                check(EGLExt.eglPresentationTimeANDROID(display,encoder,frameClock.monotonic(stamp))) { "Encoder timestamp failed" }
                check(EGL14.eglSwapBuffers(display,encoder)) { "Encoder swap failed" }
                renderedFrames++;lastEncodedTimestampNs=frameClock.monotonic(stamp)
                if(firstEncodedTimestampNs==0L) firstEncodedTimestampNs=lastEncodedTimestampNs
            }
            if(previewDrawn) {
                if(statsStart==0L) statsStart=started
                if(lastArrival!=0L) statsMaxGapNs=maxOf(statsMaxGapNs,started-lastArrival)
                lastArrival=started;statsFrames++;statsRenderNs+=System.nanoTime()-started
                val elapsed=started-statsStart
                if(elapsed>=1_000_000_000L) {
                    stats=JSONObject().put("previewSubmittedFps",(statsFrames-1)*1_000_000_000.0/elapsed)
                        .put("averageRenderMs",statsRenderNs/1_000_000.0/statsFrames).put("maxArrivalGapMs",statsMaxGapNs/1_000_000.0)
                        .put("sensorRotation",sensorRotation).put("relativeRotation",rotation).put("mirroredPreview",mirrored)
                        .put("producerMatrix",org.json.JSONArray(uv.map { it.toDouble() }))
                        .put("cameraTimestampAgeMs",(System.nanoTime()-frameClock.monotonic(stamp))/1_000_000.0)
                        .put("clock","MONOTONIC").put("scope","Frame submission timing; photon-to-photon latency not measured")
                    statsStart=started;statsFrames=1;statsRenderNs=0L;statsMaxGapNs=0L
                }
            }
        } catch(e: Exception) { closed=true;onFailure("Render GPU: ${e.cause?.message ?: e.message}") }
    }
    private fun draw(surface: EGLSurface,w: Int,h: Int,isPreview: Boolean) {
        current(surface); GLES30.glViewport(0,0,w,h); GLES30.glClearColor(0f,0f,0f,1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        Matrix.setIdentityM(mvp,0)
        if(isPreview) {
            val viewport=TextureGeometry.viewport(w,h,sourceWidth,sourceHeight,rotation)
            GLES30.glViewport(viewport[0],viewport[1],viewport[2],viewport[3])
        }
        GLES30.glUniformMatrix4fv(loc("uMvp"),1,false,mvp,0)
        val sample=TextureGeometry.sampling(uv,sensorRotation,producerMirrored,if(isPreview) rotation else 0,isPreview && mirrored)
        GLES30.glUniformMatrix4fv(loc("uTex"),1,false,sample,0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes)
        GLES30.glUniform1i(loc("uCamera"),0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,lutTexture)
        GLES30.glUniform1i(loc("uLut"),1)
        val active=isPreview || bake
        GLES30.glUniform1f(loc("uStrength"),if(active) strength else 0f)
        GLES30.glUniform1i(loc("uUseLut"),if(lut!=null) 1 else 0)
        GLES30.glUniform1i(loc("uLook"),look)
        GLES30.glUniform1f(loc("uSize"),(lut?.size ?: 2).toFloat())
        GLES30.glUniform3fv(loc("uMin"),1,lut?.domainMin ?: floatArrayOf(0f,0f,0f),0)
        GLES30.glUniform3fv(loc("uMax"),1,lut?.domainMax ?: floatArrayOf(1f,1f,1f),0)
        vertices.position(0); GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0,2,GLES30.GL_FLOAT,false,16,vertices)
        vertices.position(2); GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1,2,GLES30.GL_FLOAT,false,16,vertices)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP,0,4); checkGl()
    }
    /** Real framebuffer sampling for instrumentation; this does not alter production frames. */
    fun centerPixel(): IntArray = previewPixel(.5f,.5f)
    fun previewPixel(x: Float,y: Float): IntArray = sync {
        current(window)
        // Read the freshly drawn back buffer before swap, which is defined on all EGL drivers.
        draw(window,width,height,true)
        val b=ByteBuffer.allocateDirect(4)
        GLES30.glReadPixels((width*x).toInt().coerceIn(0,width-1),(height*(1f-y)).toInt().coerceIn(0,height-1),1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,b)
        IntArray(4) { b.get(it).toInt() and 255 }
    }
    private fun current(surface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display,surface,surface,context)) { "EGL current failed" }
        EGL14.eglSwapInterval(display,0)
    }
    private fun loc(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(program,name) }
    private fun checkGl() { val error=GLES30.glGetError();check(error==GLES30.GL_NO_ERROR) { "OpenGL error 0x${error.toString(16)}" } }
    private fun link(v: String,f: String): Int {
        fun compile(type: Int,text: String): Int {
            val shader=GLES30.glCreateShader(type);GLES30.glShaderSource(shader,text);GLES30.glCompileShader(shader)
            val ok=IntArray(1);GLES30.glGetShaderiv(shader,GLES30.GL_COMPILE_STATUS,ok,0)
            check(ok[0]==GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(shader) };return shader
        }
        val vs=compile(GLES30.GL_VERTEX_SHADER,v);val fs=compile(GLES30.GL_FRAGMENT_SHADER,f)
        val p=GLES30.glCreateProgram();GLES30.glAttachShader(p,vs);GLES30.glAttachShader(p,fs);GLES30.glLinkProgram(p)
        val ok=IntArray(1);GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0)
        check(ok[0]==GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(p) }
        GLES30.glDeleteShader(vs);GLES30.glDeleteShader(fs);return p
    }
    override fun close() {
        sync {
            closed=true
            if(::texture.isInitialized) { texture.setOnFrameAvailableListener(null); input.release();texture.release() }
            if(encoder!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,encoder)
            if(window!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,window)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
        }
        thread.quitSafely()
    }
    companion object {
        private val VERTEX="""#version 300 es
            layout(location=0) in vec2 aPosition;
            layout(location=1) in vec2 aUv;
            uniform mat4 uMvp; uniform mat4 uTex; out vec2 vUv;
            void main(){gl_Position=uMvp*vec4(aPosition,0.,1.);vUv=(uTex*vec4(aUv,0.,1.)).xy;}
        """.trimIndent()
        private val FRAGMENT="""#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision highp float;
            uniform samplerExternalOES uCamera; uniform highp sampler2D uLut;
            uniform float uStrength; uniform float uSize;
            uniform int uUseLut; uniform int uLook; uniform vec3 uMin; uniform vec3 uMax;
            in vec2 vUv; out vec4 color;
            void main(){
                vec3 src=texture(uCamera,vUv).rgb; vec3 outRgb=src;
                if(uStrength<=0. || (uUseLut==0 && uLook==0)){color=vec4(src,1.);return;}
                if(uUseLut==1){
                    vec3 p=clamp((src-uMin)/(uMax-uMin),0.,1.)*(uSize-1.);
                    float low=floor(p.b);float high=min(low+1.,uSize-1.);
                    vec2 atlas=vec2(uSize,uSize*uSize);
                    vec3 a=texture(uLut,(vec2(p.r,p.g+low*uSize)+.5)/atlas).rgb;
                    vec3 b=texture(uLut,(vec2(p.r,p.g+high*uSize)+.5)/atlas).rgb;
                    outRgb=mix(a,b,p.b-low);
                }
                else if(uLook==1){outRgb=pow(src,vec3(.98,1.01,1.04))*vec3(1.025,1.,.97);}
                else if(uLook==2){float l=dot(src,vec3(.2126,.7152,.0722));outRgb=mix(vec3(l),src,.86)+vec3(-.012,.009,.015)*(1.-l);}
                else if(uLook==3){outRgb=vec3(dot(src,vec3(.2126,.7152,.0722)));}
                color=vec4(clamp(mix(src,outRgb,uStrength),0.,1.),1.);
            }
        """.trimIndent()
    }
}
