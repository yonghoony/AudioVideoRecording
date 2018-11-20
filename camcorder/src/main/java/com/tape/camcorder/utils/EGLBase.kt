package com.tape.camcorder.utils

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: EGLBase.java
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

import android.annotation.TargetApi
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class EGLBase(shared_context: EGLContext, with_depth_buffer: Boolean, isRecordable: Boolean) {    // API >= 17

    private var mEglConfig: EGLConfig? = null
    var context = EGL14.EGL_NO_CONTEXT
        private set
    private var mEglDisplay: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var mDefaultContext = EGL14.EGL_NO_CONTEXT

    class EglSurface {
        private val mEgl: EGLBase
        private var mEglSurface: EGLSurface? = EGL14.EGL_NO_SURFACE
        val width: Int
        val height: Int

        val context: EGLContext
            get() = mEgl.context

        internal constructor(egl: EGLBase, surface: Any) {
            if (DEBUG) Log.v(TAG, "EglSurface:")
            if (surface !is SurfaceView
                && surface !is Surface
                && surface !is SurfaceHolder
                && surface !is SurfaceTexture)
                throw IllegalArgumentException("unsupported surface")
            mEgl = egl
            mEglSurface = mEgl.createWindowSurface(surface)
            width = mEgl.querySurface(mEglSurface, EGL14.EGL_WIDTH)
            height = mEgl.querySurface(mEglSurface, EGL14.EGL_HEIGHT)
            if (DEBUG) Log.v(TAG, String.format("EglSurface:size(%d,%d)", width, height))
        }

        internal constructor(egl: EGLBase, width: Int, height: Int) {
            if (DEBUG) Log.v(TAG, "EglSurface:")
            mEgl = egl
            mEglSurface = mEgl.createOffscreenSurface(width, height)
            this.width = width
            this.height = height
        }

        fun makeCurrent() {
            mEgl.makeCurrent(mEglSurface)
        }

        fun swap() {
            mEgl.swap(mEglSurface)
        }

        fun release() {
            if (DEBUG) Log.v(TAG, "EglSurface:release:")
            mEgl.makeDefault()
            mEgl.destroyWindowSurface(mEglSurface)
            mEglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    init {
        if (DEBUG) Log.v(TAG, "EGLBase:")
        init(shared_context, with_depth_buffer, isRecordable)
    }

    fun release() {
        if (DEBUG) Log.v(TAG, "release:")
        if (mEglDisplay !== EGL14.EGL_NO_DISPLAY) {
            destroyContext()
            EGL14.eglTerminate(mEglDisplay)
            EGL14.eglReleaseThread()
        }
        mEglDisplay = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
    }

    fun createFromSurface(surface: Any): EglSurface {
        if (DEBUG) Log.v(TAG, "createFromSurface:")
        val eglSurface = EglSurface(this, surface)
        eglSurface.makeCurrent()
        return eglSurface
    }

    fun createOffscreen(width: Int, height: Int): EglSurface {
        if (DEBUG) Log.v(TAG, "createOffscreen:")
        val eglSurface = EglSurface(this, width, height)
        eglSurface.makeCurrent()
        return eglSurface
    }

    fun querySurface(eglSurface: EGLSurface?, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(mEglDisplay, eglSurface, what, value, 0)
        return value[0]
    }

    private fun init(shared_context: EGLContext?, with_depth_buffer: Boolean, isRecordable: Boolean) {
        var shared_context = shared_context
        if (DEBUG) Log.v(TAG, "init:")
        if (mEglDisplay !== EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("EGL already set up")
        }

        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            mEglDisplay = null
            throw RuntimeException("eglInitialize failed")
        }

        shared_context = shared_context ?: EGL14.EGL_NO_CONTEXT
        if (context === EGL14.EGL_NO_CONTEXT) {
            mEglConfig = getConfig(with_depth_buffer, isRecordable)
            if (mEglConfig == null) {
                throw RuntimeException("chooseConfig failed")
            }
            // create EGL rendering context
            context = createContext(shared_context)
        }
        // confirm whether the EGL rendering context is successfully created
        val values = IntArray(1)
        EGL14.eglQueryContext(mEglDisplay, context, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        if (DEBUG) Log.d(TAG, "EGLContext created, client version " + values[0])
        makeDefault()    // makeCurrent(EGL14.EGL_NO_SURFACE);
    }

    /**
     * change context to draw this window surface
     * @return
     */
    private fun makeCurrent(surface: EGLSurface?): Boolean {
        //		if (DEBUG) Log.v(TAG, "makeCurrent:");
        if (mEglDisplay == null) {
            if (DEBUG) Log.d(TAG, "makeCurrent:eglDisplay not initialized")
        }
        if (surface == null || surface === EGL14.EGL_NO_SURFACE) {
            val error = EGL14.eglGetError()
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "makeCurrent:returned EGL_BAD_NATIVE_WINDOW.")
            }
            return false
        }
        // attach EGL renderring context to specific EGL window surface
        if (!EGL14.eglMakeCurrent(mEglDisplay, surface, surface, context)) {
            Log.w(TAG, "eglMakeCurrent:" + EGL14.eglGetError())
            return false
        }
        return true
    }

    private fun makeDefault() {
        if (DEBUG) Log.v(TAG, "makeDefault:")
        if (!EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            Log.w("TAG", "makeDefault" + EGL14.eglGetError())
        }
    }

    private fun swap(surface: EGLSurface?): Int {
        //		if (DEBUG) Log.v(TAG, "swap:");
        if (!EGL14.eglSwapBuffers(mEglDisplay, surface)) {
            val err = EGL14.eglGetError()
            if (DEBUG) Log.w(TAG, "swap:err=$err")
            return err
        }
        return EGL14.EGL_SUCCESS
    }

    private fun createContext(shared_context: EGLContext?): EGLContext {
        //		if (DEBUG) Log.v(TAG, "createContext:");

        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(mEglDisplay, mEglConfig, shared_context, attrib_list, 0)
        checkEglError("eglCreateContext")
        return context
    }

    private fun destroyContext() {
        if (DEBUG) Log.v(TAG, "destroyContext:")

        if (!EGL14.eglDestroyContext(mEglDisplay, context)) {
            Log.e("destroyContext", "display:$mEglDisplay context: $context")
            Log.e(TAG, "eglDestroyContex:" + EGL14.eglGetError())
        }
        context = EGL14.EGL_NO_CONTEXT
        if (mDefaultContext !== EGL14.EGL_NO_CONTEXT) {
            if (!EGL14.eglDestroyContext(mEglDisplay, mDefaultContext)) {
                Log.e("destroyContext", "display:$mEglDisplay context: $mDefaultContext")
                Log.e(TAG, "eglDestroyContex:" + EGL14.eglGetError())
            }
            mDefaultContext = EGL14.EGL_NO_CONTEXT
        }
    }

    private fun createWindowSurface(nativeWindow: Any): EGLSurface? {
        if (DEBUG) Log.v(TAG, "createWindowSurface:nativeWindow=$nativeWindow")

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, nativeWindow, surfaceAttribs, 0)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "eglCreateWindowSurface", e)
        }

        return result
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    private fun createOffscreenSurface(width: Int, height: Int): EGLSurface? {
        if (DEBUG) Log.v(TAG, "createOffscreenSurface:")
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreatePbufferSurface(mEglDisplay, mEglConfig, surfaceAttribs, 0)
            checkEglError("eglCreatePbufferSurface")
            if (result == null) {
                throw RuntimeException("surface was null")
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "createOffscreenSurface", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "createOffscreenSurface", e)
        }

        return result
    }

    private fun destroyWindowSurface(surface: EGLSurface?) {
        var surface = surface
        if (DEBUG) Log.v(TAG, "destroySurface:")

        if (surface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(mEglDisplay,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(mEglDisplay, surface)
        }
        surface = EGL14.EGL_NO_SURFACE
        if (DEBUG) Log.v(TAG, "destroySurface:finished")
    }

    private fun checkEglError(msg: String) {
        val error: Int = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    private fun getConfig(with_depth_buffer: Boolean, isRecordable: Boolean): EGLConfig? {
        val attribList = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE, EGL14.EGL_NONE, //EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE, EGL14.EGL_NONE, //EGL_RECORDABLE_ANDROID, 1,	// this flag need to recording of MediaCodec
            EGL14.EGL_NONE, EGL14.EGL_NONE, //	with_depth_buffer ? EGL14.EGL_DEPTH_SIZE : EGL14.EGL_NONE,
            // with_depth_buffer ? 16 : 0,
            EGL14.EGL_NONE)
        var offset = 10
        if (false) {                // ステンシルバッファ(常時未使用)
            attribList[offset++] = EGL14.EGL_STENCIL_SIZE
            attribList[offset++] = 8
        }
        if (with_depth_buffer) {    // デプスバッファ
            attribList[offset++] = EGL14.EGL_DEPTH_SIZE
            attribList[offset++] = 16
        }
        if (isRecordable && Build.VERSION.SDK_INT >= 18) {// MediaCodecの入力用Surfaceの場合
            attribList[offset++] = EGL_RECORDABLE_ANDROID
            attribList[offset++] = 1
        }
        for (i in attribList.size - 1 downTo offset) {
            attribList[i] = EGL14.EGL_NONE
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(mEglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            // XXX it will be better to fallback to RGB565
            Log.w(TAG, "unable to find RGBA8888 / " + " EGLConfig")
            return null
        }
        return configs[0]
    }

    companion object {
        private val DEBUG = false    // TODO set false on release
        private val TAG = "EGLBase"

        private val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
