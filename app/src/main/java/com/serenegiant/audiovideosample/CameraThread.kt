package com.serenegiant.audiovideosample

import android.content.Context
import android.hardware.Camera
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.WindowManager

import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Thread for asynchronous operation of camera preview
 */
class CameraThread(cameraView: CameraGLView) : Thread("Camera thread") {

    companion object {
        private const val TAG = "CameraThread"
    }

    private val readyFence = java.lang.Object()
    private val cameraViewRef: WeakReference<CameraGLView> = WeakReference(cameraView)

    @JvmField
    @Volatile
    var isRunning = false

    var handler: CameraHandler? = null
        get() {
            synchronized(readyFence) {
                try {
                    readyFence.wait()
                } catch (e: InterruptedException) {
                }
            }
            return field
        }

    /**
     * message loop
     * prepare Looper and create Handler for this thread
     */
    override fun run() {
        Log.d(TAG, "Camera thread start")
        Looper.prepare()
        synchronized(readyFence) {
            handler = CameraHandler(this, cameraViewRef)
            isRunning = true
            readyFence.notify()
        }
        Looper.loop()
        Log.d(TAG, "Camera thread finish")
        synchronized(readyFence) {
            handler = null
            isRunning = false
        }
    }

}
