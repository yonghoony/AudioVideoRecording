package com.serenegiant.audiovideosample

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log

/**
 * Handler class for asynchronous camera operation
 */
class CameraHandler(private var thread: CameraThread?) : Handler() {
    companion object {
        private val TAG = "CameraHandler"
        private const val MSG_PREVIEW_START = 1
        private const val MSG_PREVIEW_STOP = 2
    }

    private val lock = java.lang.Object()

    fun startPreview(width: Int, height: Int) {
        sendMessage(obtainMessage(MSG_PREVIEW_START, width, height))
    }

    /**
     * request to stop camera preview
     * @param needWait need to wait for stopping camera preview
     */
    fun stopPreview(needWait: Boolean) {
        synchronized(lock) {
            sendEmptyMessage(MSG_PREVIEW_STOP)
            if (needWait && thread!!.isRunning) {
                try {
                    Log.d(TAG, "wait for terminating of camera thread")
                    lock.wait()
                } catch (e: InterruptedException) {
                }

            }
        }
    }

    /**
     * message handler for camera thread
     */
    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_PREVIEW_START -> thread!!.startPreview(msg.arg1, msg.arg2)
            MSG_PREVIEW_STOP -> {
                thread!!.stopPreview()
                synchronized(lock) {
                    lock.notifyAll()
                }
                Looper.myLooper()!!.quit()
                thread = null
            }
            else -> throw RuntimeException("unknown message:what=" + msg.what)
        }
    }
}
