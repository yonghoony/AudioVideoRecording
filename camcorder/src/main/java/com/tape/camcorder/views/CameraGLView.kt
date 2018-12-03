package com.tape.camcorder.views

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: CameraGLView.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.EGL14
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder

import com.tape.camcorder.encoder.VideoEncoder
import android.graphics.Rect


/**
 * Sub class of GLSurfaceView to display camera preview and write video frame to capturing surface
 */
class CameraGLView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : GLSurfaceView(context, attrs) {

    private val lock = Any()
    private val renderer: CameraSurfaceRenderer?
    var hasSurface: Boolean = false
    var cameraHandler: CameraHandler? = null
    var videoWidth: Int = 0
    var videoHeight: Int = 0
    var rotationDegrees: Int = 0
    var scaleMode = SCALE_CROP_CENTER
        set(mode) {
            if (scaleMode != mode) {
                field = mode
                queueEvent { renderer!!.updateViewport() }
            }
        }
    private var cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT

    val surfaceTexture: SurfaceTexture?
        get() {
            Log.v(TAG, "getSurfaceTexture:")
            return renderer?.surfaceTexture
        }

    init {
        Log.v(TAG, "CameraGLView:")
        renderer = CameraSurfaceRenderer(this)
        setEGLContextClientVersion(2)    // GLES 2.0, API >= 8
        setRenderer(renderer)
        /*		// the frequency of refreshing of camera preview is at most 15 fps
		// and RENDERMODE_WHEN_DIRTY is better to reduce power consumption
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY); */
    }

    override fun onResume() {
        Log.v(TAG, "onResume:")
        super.onResume()
        if (hasSurface) {
            if (cameraHandler == null) {
                Log.v(TAG, "surface already exist")
                startPreview(width, height)
            }
        }
    }

    override fun onPause() {
        Log.v(TAG, "onPause:")
        // just request stop prviewing
        stopPreview(false)
        super.onPause()
    }

    fun setVideoSize(width: Int, height: Int) {
        if (rotationDegrees % 180 == 0) {
            videoWidth = width
            videoHeight = height
        } else {
            videoWidth = height
            videoHeight = width
        }
        queueEvent { renderer!!.updateViewport() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.v(TAG, "surfaceDestroyed:")
        // wait for finish previewing here
        // otherwise camera try to display on un-exist Surface and some error will occure
        stopPreview(true)
        cameraHandler = null
        hasSurface = false
        renderer!!.onSurfaceDestroyed()
        super.surfaceDestroyed(holder)
    }

    fun setVideoEncoder(encoder: VideoEncoder?) {
        Log.v(TAG, "setVideoEncoder:tex_id=" + renderer!!.texture + ",encoder=" + encoder)
        queueEvent {
            synchronized(renderer) {
                encoder?.setEglContext(EGL14.eglGetCurrentContext(), renderer.texture)
                renderer.videoEncoder = encoder
            }
        }
    }

    fun toggleCamera() {
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            cameraId = Camera.CameraInfo.CAMERA_FACING_BACK
        } else {
            cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT
        }

        stopPreview(true)
        startPreview(width, height)
    }

    fun startPreview(width: Int, height: Int) {
        Log.i(TAG, "startPreview [$width, $height]")
        synchronized(lock) {
            if (cameraHandler == null) {
                val thread = CameraThread(this)
                thread.start()
                cameraHandler = thread.handler
            }
            cameraHandler!!.startPreview(width, height, cameraId)
        }
    }

    fun focusOnTouch(x: Float, y: Float) {
        cameraHandler?.focus(getFocusArea(x, y))
    }

    private fun stopPreview(needWait: Boolean) {
        cameraHandler?.stopPreview(needWait)
    }

    /**
     * Maps the position ratio to the coordinates ranging from -1000 to 1000.
     */
    private fun getFocusArea(touchEventX: Float, touchEventY: Float): Rect {
        val xRatio = touchEventY / height
        val yRatio = (width - touchEventX) / width

        val focusAreaX = FOCUS_AREA_COORDINATE_RANGE * xRatio - FOCUS_AREA_COORDINATE_MAX
        val focusAreaY = FOCUS_AREA_COORDINATE_RANGE * yRatio - FOCUS_AREA_COORDINATE_MAX

        val focusArea = Rect()
        focusArea.left = (focusAreaX - FOCUS_AREA_RADIUS).validCoordinate
        focusArea.right = (focusAreaX + FOCUS_AREA_RADIUS).validCoordinate
        focusArea.top = (focusAreaY - FOCUS_AREA_RADIUS).validCoordinate
        focusArea.bottom = (focusAreaY + FOCUS_AREA_RADIUS).validCoordinate

        Log.d(TAG, "getFocusArea touchEvent[$touchEventX, $touchEventY]" +
            " ratio=[$xRatio, $yRatio] focusArea=$focusArea")
        return focusArea
    }

    companion object {

        private val TAG = "CameraGLView"

        private const val FOCUS_AREA_COORDINATE_MAX = 1000F
        private const val FOCUS_AREA_COORDINATE_RANGE = FOCUS_AREA_COORDINATE_MAX * 2F
        private const val FOCUS_AREA_RADIUS = FOCUS_AREA_COORDINATE_MAX * 0.1F

        const val SCALE_STRETCH_FIT = 0
        const val SCALE_KEEP_ASPECT_VIEWPORT = 1
        const val SCALE_KEEP_ASPECT = 2
        const val SCALE_CROP_CENTER = 3

        private val Float.validCoordinate: Int
            get() {
                return Math.max(
                    Math.min(this, FOCUS_AREA_COORDINATE_MAX), -FOCUS_AREA_COORDINATE_MAX
                ).toInt()
            }
    }
}
