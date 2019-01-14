package com.tape.camcorder.views

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log

import com.tape.camcorder.encoder.VideoEncoder
import com.tape.camcorder.utils.GLDrawer2D
import com.tape.camcorder.views.CameraGLView.Companion.SCALE_CROP_CENTER
import com.tape.camcorder.views.CameraGLView.Companion.SCALE_KEEP_ASPECT
import com.tape.camcorder.views.CameraGLView.Companion.SCALE_KEEP_ASPECT_VIEWPORT
import com.tape.camcorder.views.CameraGLView.Companion.SCALE_STRETCH_FIT

import java.lang.ref.WeakReference

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceViewのRenderer
 */
internal class CameraSurfaceRenderer(cameraView: CameraGLView)
    : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private val TAG = "CameraSurfaceRenderer"
    }

    private val cameraViewRef: WeakReference<CameraGLView>
    var surfaceTexture: SurfaceTexture? = null
    var texture: Int = 0
    private var drawer: GLDrawer2D? = null
    private val surfaceTextureMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    var videoEncoder: VideoEncoder? = null

    @Volatile
    private var requesrUpdateTex = false
    private var flip = true

    init {
        Log.v(TAG, "CameraSurfaceRenderer:")
        cameraViewRef = WeakReference(cameraView)
        Matrix.setIdentityM(mvpMatrix, 0)
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        Log.v(TAG, "onSurfaceCreated:")
        // This renderer required OES_EGL_image_external extension
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)    // API >= 8
        //			if (DEBUG) Log.i(TAG, "onSurfaceCreated:Gl extensions: " + extensions);
        if (!extensions.contains("OES_EGL_image_external"))
            throw RuntimeException("This system does not support OES_EGL_image_external.")
        // create textur ID
        texture = GLDrawer2D.initTex()
        // create SurfaceTexture with texture ID.
        surfaceTexture = SurfaceTexture(texture)
        surfaceTexture!!.setOnFrameAvailableListener(this)
        // clear screen with yellow color so that you can see rendering rectangle
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f)
        val parent = cameraViewRef.get()
        if (parent != null) {
            parent.hasSurface = true
        }
        // create object for preview display
        drawer = GLDrawer2D()
        drawer!!.setMatrix(mvpMatrix, 0)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        Log.v(TAG, String.format("onSurfaceChanged:(%d,%d)", width, height))
        // if at least with or height is zero, initialization of this view is still progress.
        if (width == 0 || height == 0) return
        updateViewport()
        val parent = cameraViewRef.get()
        parent?.startPreview(width, height)
    }

    /**
     * when GLSurface context is soon destroyed
     */
    fun onSurfaceDestroyed() {
        Log.v(TAG, "onSurfaceDestroyed:")
        if (drawer != null) {
            drawer!!.release()
            drawer = null
        }
        if (surfaceTexture != null) {
            surfaceTexture!!.release()
            surfaceTexture = null
        }
        GLDrawer2D.deleteTex(texture)
    }

    fun updateViewport() {
        val parent = cameraViewRef.get()
        if (parent != null) {
            val viewWidth = parent.width
            val viewHeight = parent.height

            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            val videoWidth = parent.videoWidth.toDouble()
            val videoHeight = parent.videoHeight.toDouble()

            Log.d(TAG, "updateViewport view [$viewWidth, $viewHeight] video [$videoWidth, $videoHeight]")

            if (videoWidth == 0.0 || videoHeight == 0.0) return

            Matrix.setIdentityM(mvpMatrix, 0)
            val viewAspectRatio = viewWidth / viewHeight.toDouble()
            Log.i(TAG, String.format("view(%d,%d)%f,video(%1.0f,%1.0f)", viewWidth, viewHeight, viewAspectRatio, videoWidth, videoHeight))
            when (parent.scaleMode) {
                SCALE_STRETCH_FIT -> {}
                SCALE_KEEP_ASPECT_VIEWPORT -> {
                    val req = videoWidth / videoHeight
                    val x: Int
                    val y: Int
                    val width: Int
                    val height: Int
                    if (viewAspectRatio > req) {
                        // if view is wider than camera image, calc width of drawing area based on view height
                        y = 0
                        height = viewHeight
                        width = (req * viewHeight).toInt()
                        x = (viewWidth - width) / 2
                    } else {
                        // if view is higher than camera image, calc height of drawing area based on view width
                        x = 0
                        width = viewWidth
                        height = (viewWidth / req).toInt()
                        y = (viewHeight - height) / 2
                    }
                    // set viewport to draw keeping aspect ration of camera image
                    Log.v(TAG, String.format("xy(%d,%d),size(%d,%d)", x, y, width, height))
                    GLES20.glViewport(x, y, width, height)
                }
                SCALE_KEEP_ASPECT,
                SCALE_CROP_CENTER -> {
                    val scaleX = viewWidth / videoWidth
                    val scaleY = viewHeight / videoHeight
                    val scale = if (parent.scaleMode == SCALE_CROP_CENTER)
                        Math.max(scaleX, scaleY)
                    else
                        Math.min(scaleX, scaleY)
                    val width = scale * videoWidth
                    val height = scale * videoHeight
                    Log.v(TAG, String.format("size(%1.0f,%1.0f),scale(%f,%f),mat(%f,%f)",
                        width, height, scaleX, scaleY, width / viewWidth, height / viewHeight))
                    Matrix.scaleM(mvpMatrix, 0, (width / viewWidth).toFloat(), (height / viewHeight).toFloat(), 1.0f)
                }
            }
            if (drawer != null)
                drawer!!.setMatrix(mvpMatrix, 0)
        }
    }

    /**
     * drawing to GLSurface
     * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
     * this method is only called when #requestRender is called(= when texture is required to update)
     * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
     */
    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (requesrUpdateTex) {
            requesrUpdateTex = false
            // update texture(came from camera)
            surfaceTexture!!.updateTexImage()
            // get texture matrix
            surfaceTexture!!.getTransformMatrix(surfaceTextureMatrix)
        }
        // draw to preview screen
        drawer!!.draw(texture, surfaceTextureMatrix)
        flip = !flip
        if (flip) {    // ~30fps
            synchronized(this) {
                if (videoEncoder != null) {
                    // notify to capturing thread that the camera frame is available.
                    // NOTE:
                    // Don't pass the mvp matrix which causes the aspect ratio of the output video
                    // to be distorted by vertical or horizontal stretching.
                    videoEncoder!!.frameAvailableSoon(surfaceTextureMatrix)
                    // TODO(yonghoon): Investigate the necessity of mvp matrix for other cases.
                    //                 At least, it seems unnecessary for video encoding
                    //                 when the camera view uses the SCALE_CROP_CENTER mode.
//                    videoEncoder!!.frameAvailableSoon(surfaceTextureMatrix, mvpMatrix)
                }
            }
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        requesrUpdateTex = true
        //			final CameraGLView cameraView = cameraViewRef.get();
        //			if (cameraView != null)
        //				cameraView.requestRender();
    }
}
