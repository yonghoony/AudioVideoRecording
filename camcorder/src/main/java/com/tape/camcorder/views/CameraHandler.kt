package com.tape.camcorder.views

import android.content.Context
import android.graphics.Rect
import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.tape.camcorder.utils.CameraUtils
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Handler class for asynchronous camera operation
 */
class CameraHandler(private var thread: CameraThread?,
                    private val cameraViewRef: WeakReference<CameraGLView>)
    : Handler() {
    companion object {
        private val TAG = "CameraHandler"
        private const val MSG_PREVIEW_STOP = 2
        private const val FOCUS_AREA_WEIGHT = 1000
    }

    private val lock = java.lang.Object()
    private var camera: Camera? = null
    private var isFrontFace: Boolean = false

    fun startPreview(width: Int, height: Int, cameraId: Int) {
        if (width > height) {
            post { performStartPreview(width, height, cameraId) }
        } else {
            post { performStartPreview(height, width, cameraId) }
        }
    }

    /**
     * request to stop camera preview
     * @param needWait need to wait for stopping camera preview
     */
    fun stopPreview(needWait: Boolean) {
        synchronized(lock) {
            sendEmptyMessage(MSG_PREVIEW_STOP)
            post {
                performStopPreview()
                synchronized(lock) {
                    lock.notifyAll()
                }
                Looper.myLooper()!!.quit()
                thread = null
            }


            if (needWait && thread!!.isRunning) {
                try {
                    Log.d(TAG, "wait for terminating of camera thread")
                    lock.wait()
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Failed to wait for camera termination. $e")
                }
            }
        }
    }

    fun focus(focusRect: Rect) {
        post {
            Log.d(TAG, "focus() focusRect=$focusRect")
            camera?.let {
                it.cancelAutoFocus()
                val focusList = mutableListOf(Camera.Area(focusRect, FOCUS_AREA_WEIGHT))
                val parameters = it.parameters
                if (parameters.maxNumMeteringAreas > 0) {
                    parameters.meteringAreas = focusList
                }
                if (parameters.maxNumFocusAreas > 0) {
                    parameters.focusAreas = focusList
                }
                it.parameters = parameters
                it.autoFocus { success, camera ->
                    Log.d(TAG, "autoFocus success=$success")
                }
            }
        }
    }

    /**
     * start camera preview
     *
     * @param width The longer side of the preview regardless of the orientation.
     * @param height The shorter side of the preview regardless of the orientation.
     */
    private fun performStartPreview(width: Int, height: Int, cameraId: Int = Camera.CameraInfo.CAMERA_FACING_FRONT) {
        Log.v(TAG, "startPreview:")
        val parent = cameraViewRef.get()
        if (parent != null && camera == null) {
            // This is a sample project so just use 0 as camera ID.
            // it is better to selecting camera is available
            try {
                camera = Camera.open(cameraId)
                val params = camera!!.parameters
                val focusModes = params.supportedFocusModes
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                } else {
                    Log.i(TAG, "Camera does not support autofocus")
                }
                // let's try fastest frame rate. You will get near 60fps, but your device become hot.
                val supportedFpsRange = params.supportedPreviewFpsRange
                //					final int n = supportedFpsRange != null ? supportedFpsRange.size() : 0;
                //					int[] range;
                //					for (int i = 0; i < n; i++) {
                //						range = supportedFpsRange.get(i);
                //						Log.i(TAG, String.format("supportedFpsRange(%d)=(%d,%d)", i, range[0], range[1]));
                //					}
                val max_fps = supportedFpsRange[supportedFpsRange.size - 1]
                Log.i(TAG, String.format("fps:%d-%d", max_fps[0], max_fps[1]))
                params.setPreviewFpsRange(max_fps[0], max_fps[1])
                params.setRecordingHint(true)
                Log.i(TAG, "requested previewSize[$width, $height]")
                // request closest supported preview size
                val closestSize = CameraUtils.getClosestSupportedSize(
                    params.supportedPreviewSizes, width, height)
                params.setPreviewSize(closestSize.width, closestSize.height)
                Log.i(TAG, "closestSize[${closestSize.width}, ${closestSize.height}]")
                // request closest picture size for an aspect ratio issue on Nexus7
                val pictureSize = CameraUtils.getClosestSupportedSize(
                    params.supportedPictureSizes, width, height)
                Log.i(TAG, "pictureSize[${pictureSize.width}, ${pictureSize.height}]")
                params.setPictureSize(pictureSize.width, pictureSize.height)
                // rotate camera preview according to the device orientation
                setRotation(cameraId, params)
                camera!!.parameters = params
                // get the actual preview size
                val previewSize = camera!!.parameters.previewSize
                Log.i(TAG, String.format("previewSize(%d, %d)", previewSize.width, previewSize.height))
                // adjust view size with keeping the aspect ration of camera preview.
                // here is not a UI thread and we should request parent view to execute.
                parent.post { parent.setVideoSize(previewSize.width, previewSize.height) }
                val st = parent.surfaceTexture
                st!!.setDefaultBufferSize(previewSize.width, previewSize.height)
                camera!!.setPreviewTexture(st)
            } catch (e: IOException) {
                Log.e(TAG, "startPreview:", e)
                if (camera != null) {
                    camera!!.release()
                    camera = null
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "startPreview:", e)
                if (camera != null) {
                    camera!!.release()
                    camera = null
                }
            }

            if (camera != null) {
                // start camera preview display
                camera!!.startPreview()
            }
        }
    }

    /**
     * stop camera preview
     */
    private fun performStopPreview() {
        Log.v(TAG, "stopPreview:")
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.release()
            camera = null
        }
        val parent = cameraViewRef.get() ?: return
        parent.cameraHandler = null
    }

    /**
     * rotate preview screen according to the device orientation
     *
     * @param params
     */
    private fun setRotation(cameraId: Int, params: Camera.Parameters) {
        Log.v(TAG, "setRotation")
        val parent = cameraViewRef.get() ?: return

        val display = (parent.context
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        var degrees = 0

        Log.d(TAG, "display.rotation=${display.rotation}")

        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        // get whether the camera is front camera or back camera
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)

        isFrontFace = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
        degrees = (info.orientation + degrees) % 360
        Log.d(TAG, "info.facing=${info.facing}")
        if (isFrontFace) {    // front camera
            degrees = (360 - degrees) % 360  // reverse
        }
        // apply rotation setting
        camera!!.setDisplayOrientation(degrees)
        parent.rotationDegrees = degrees
    }
}
