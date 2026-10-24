package id.tege.video.camera

import android.opengl.Matrix

/** Camera2's SurfaceTexture already carries sensor rotation (and, by default, front mirroring).
 * Remove that predicted orientation once, preserving the producer's crop, before our own transform.
 * The vertical flip is the image-to-GL coordinate conversion, not an additional camera rotation. */
object TextureGeometry {
    private fun identity() = FloatArray(16).also { Matrix.setIdentityM(it,0) }
    private fun rotate(m: FloatArray,degrees: Int) {
        Matrix.translateM(m,0,.5f,.5f,0f);Matrix.rotateM(m,0,degrees.toFloat(),0f,0f,1f);Matrix.translateM(m,0,-.5f,-.5f,0f)
    }
    private fun mirror(m: FloatArray) { Matrix.translateM(m,0,1f,0f,0f);Matrix.scaleM(m,0,-1f,1f,1f) }
    fun cameraTransform(sensorRotation: Int,mirrored: Boolean): FloatArray = identity().also {
        Matrix.translateM(it,0,0f,1f,0f);Matrix.scaleM(it,0,1f,-1f,1f)
        rotate(it,sensorRotation);if(mirrored) mirror(it)
    }
    fun sampling(producer: FloatArray,sensorRotation: Int?,producerMirrored: Boolean,rotation: Int,mirrorPreview: Boolean): FloatArray {
        val additional=identity()
        if(sensorRotation!=null) {
            val inverse=FloatArray(16)
            check(Matrix.invertM(inverse,0,cameraTransform(sensorRotation,producerMirrored),0))
            val imageToGl=cameraTransform(0,false)
            Matrix.multiplyMM(additional,0,inverse,0,imageToGl,0)
        }
        rotate(additional,rotation);if(mirrorPreview) mirror(additional)
        return FloatArray(16).also { Matrix.multiplyMM(it,0,producer,0,additional,0) }
    }
    fun viewport(w: Int,h: Int,sourceW: Int,sourceH: Int,rotation: Int): IntArray {
        val aspect=if(rotation%180!=0) sourceH.toFloat()/sourceW else sourceW.toFloat()/sourceH
        val viewW=if(w.toFloat()/h>aspect) (h*aspect).toInt() else w
        val viewH=if(w.toFloat()/h>aspect) h else (w/aspect).toInt()
        return intArrayOf((w-viewW)/2,(h-viewH)/2,viewW,viewH)
    }
}
