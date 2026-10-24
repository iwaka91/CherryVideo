package id.tege.video.camera

import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import id.tege.video.core.CubeLut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** One GL owner. The exact same grade feeds preview and encoder; monitoring-only bypass is explicit. */
class VideoRenderer(
    private val preview: Surface,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val onFailure: (String) -> Unit,
) : AutoCloseable {
    private val thread=HandlerThread("TeGe-GL").apply { start() }
    private val handler=Handler(thread.looper)
    private var display=EGL14.EGL_NO_DISPLAY
    private var context=EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig?=null
    private var window=EGL14.EGL_NO_SURFACE
    private var encoder=EGL14.EGL_NO_SURFACE
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
    private var closed=false
    @Volatile var renderedFrames=0L; private set
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
        val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
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
            setOnFrameAvailableListener({ render() },handler)
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
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D,lutTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_LINEAR)
        for(axis in intArrayOf(GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_TEXTURE_WRAP_R))
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D,axis,GLES30.GL_CLAMP_TO_EDGE)
        val buffer=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(values)
        buffer.position(0)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D,0,GLES30.GL_RGB16F,size,size,size,0,GLES30.GL_RGB,GLES30.GL_FLOAT,buffer)
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
    } }
    fun detachEncoder() { sync {
        if(encoder!=EGL14.EGL_NO_SURFACE) {
            current(window); EGL14.eglDestroySurface(display,encoder); encoder=EGL14.EGL_NO_SURFACE
        }
    } }
    private fun render() {
        if(closed) return
        try {
            current(window); texture.updateTexImage(); texture.getTransformMatrix(uv)
            val stamp=texture.timestamp
            if(stamp<=lastTimestamp) return
            lastTimestamp=stamp
            draw(window,width,height,true)
            check(EGL14.eglSwapBuffers(display,window)) { "Preview swap failed" }
            if(encoder!=EGL14.EGL_NO_SURFACE) {
                draw(encoder,sourceWidth,sourceHeight,false)
                EGLExt.eglPresentationTimeANDROID(display,encoder,stamp)
                check(EGL14.eglSwapBuffers(display,encoder)) { "Encoder swap failed" }
                renderedFrames++
            }
        } catch(e: Exception) { onFailure("Render GPU: ${e.cause?.message ?: e.message}") }
    }
    private fun draw(surface: EGLSurface,w: Int,h: Int,isPreview: Boolean) {
        current(surface); GLES30.glViewport(0,0,w,h); GLES30.glClearColor(0f,0f,0f,1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        Matrix.setIdentityM(mvp,0)
        if(isPreview) {
            val rotated=rotation%180!=0
            val imageAspect=if(rotated) sourceHeight.toFloat()/sourceWidth else sourceWidth.toFloat()/sourceHeight
            val screenAspect=w.toFloat()/h
            Matrix.scaleM(mvp,0,if(imageAspect<screenAspect) imageAspect/screenAspect else 1f,
                if(imageAspect>screenAspect) screenAspect/imageAspect else 1f,1f)
            if(mirrored) Matrix.scaleM(mvp,0,-1f,1f,1f)
            Matrix.rotateM(mvp,0,-rotation.toFloat(),0f,0f,1f)
        }
        GLES30.glUniformMatrix4fv(loc("uMvp"),1,false,mvp,0)
        GLES30.glUniformMatrix4fv(loc("uTex"),1,false,uv,0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,oes)
        GLES30.glUniform1i(loc("uCamera"),0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1); GLES30.glBindTexture(GLES30.GL_TEXTURE_3D,lutTexture)
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
    fun centerPixel(): IntArray = sync {
        current(window)
        // Read the freshly drawn back buffer before swap, which is defined on all EGL drivers.
        draw(window,width,height,true)
        val b=ByteBuffer.allocateDirect(4)
        GLES30.glReadPixels(width/2,height/2,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,b)
        IntArray(4) { b.get(it).toInt() and 255 }
    }
    private fun current(surface: EGLSurface) { check(EGL14.eglMakeCurrent(display,surface,surface,context)) { "EGL current failed" } }
    private fun loc(name: String): Int = GLES30.glGetUniformLocation(program,name)
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
            uniform samplerExternalOES uCamera; uniform highp sampler3D uLut;
            uniform float uStrength; uniform float uSize;
            uniform int uUseLut; uniform int uLook; uniform vec3 uMin; uniform vec3 uMax;
            in vec2 vUv; out vec4 color;
            void main(){
                vec3 src=texture(uCamera,vUv).rgb; vec3 outRgb=src;
                if(uUseLut==1){vec3 c=clamp((src-uMin)/(uMax-uMin),0.,1.);outRgb=texture(uLut,(c*(uSize-1.)+.5)/uSize).rgb;}
                else if(uLook==1){outRgb=pow(src,vec3(.98,1.01,1.04))*vec3(1.025,1.,.97);}
                else if(uLook==2){float l=dot(src,vec3(.2126,.7152,.0722));outRgb=mix(vec3(l),src,.86)+vec3(-.012,.009,.015)*(1.-l);}
                else if(uLook==3){outRgb=vec3(dot(src,vec3(.2126,.7152,.0722)));}
                color=vec4(clamp(mix(src,outRgb,uStrength),0.,1.),1.);
            }
        """.trimIndent()
    }
}
