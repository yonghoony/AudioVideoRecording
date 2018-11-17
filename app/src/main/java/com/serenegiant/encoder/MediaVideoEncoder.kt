package com.serenegiant.encoder

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: MediaVideoEncoder.java
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

import java.io.IOException

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.EGLContext
import android.util.Log
import android.view.Surface

import com.serenegiant.glutils.RenderHandler

class MediaVideoEncoder(muxer: MediaMuxerWrapper,
                        listener: MediaEncoder.MediaEncoderListener,
                        private val mWidth: Int, private val mHeight: Int) : MediaEncoder(muxer, listener) {
    private var renderHandler = RenderHandler.createHandler(TAG)
    private var surface: Surface? = null

    fun frameAvailableSoon(tex_matrix: FloatArray): Boolean {
        val result: Boolean = super.frameAvailableSoon()
        if (result)
            renderHandler!!.draw(tex_matrix)
        return result
    }

    fun frameAvailableSoon(tex_matrix: FloatArray, mvp_matrix: FloatArray): Boolean {
        val result: Boolean = super.frameAvailableSoon()
        if (result)
            renderHandler!!.draw(tex_matrix, mvp_matrix)
        return result
    }

    override fun frameAvailableSoon(): Boolean {
        val result: Boolean = super.frameAvailableSoon()
        if (result)
            renderHandler!!.draw(null)
        return result
    }

    @Throws(IOException::class)
    override fun prepare() {
        Log.i(TAG, "prepare")
        mTrackIndex = -1
        mIsEOS = false
        mMuxerStarted = mIsEOS

        val videoCodecInfo = selectVideoCodec(MIME_TYPE)
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for $MIME_TYPE")
            return
        }
        Log.i(TAG, "selected codec: " + videoCodecInfo.name)

        val format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)    // API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
        Log.i(TAG, "format: $format")

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        // get Surface for encoder input
        // this method only can call between #configure and #start
        surface = mMediaCodec.createInputSurface()    // API >= 18
        mMediaCodec.start()
        Log.i(TAG, "prepare finishing")
        if (mListener != null) {
            try {
                mListener.onPrepared(this)
            } catch (e: Exception) {
                Log.e(TAG, "prepare:", e)
            }

        }
    }

    fun setEglContext(shared_context: EGLContext, tex_id: Int) {
        renderHandler!!.setEglContext(shared_context, tex_id, surface, true)
    }

    override fun release() {
        Log.i(TAG, "release:")
        if (surface != null) {
            surface!!.release()
            surface = null
        }
        if (renderHandler != null) {
            renderHandler!!.release()
            renderHandler = null
        }
        super.release()
    }

    private fun calcBitRate(): Int {
        val bitrate = (BPP * FRAME_RATE.toFloat() * mWidth.toFloat() * mHeight.toFloat()).toInt()
        Log.i(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate.toFloat() / 1024f / 1024f))
        return bitrate
    }

    override fun signalEndOfInputStream() {
        Log.d(TAG, "sending EOS to encoder")
        mMediaCodec.signalEndOfInputStream()    // API >= 18
        mIsEOS = true
    }

    companion object {
        private val TAG = "MediaVideoEncoder"

        private const val MIME_TYPE = "video/avc"
        // parameters for recording
        private const val FRAME_RATE = 25
        private const val BPP = 0.25f

        /**
         * select the first codec that match a specific MIME type
         * @param mimeType
         * @return null if no codec matched
         */
        protected fun selectVideoCodec(mimeType: String): MediaCodecInfo? {
            Log.v(TAG, "selectVideoCodec:")

            // get the list of available codecs
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)

                if (!codecInfo.isEncoder) {    // skipp decoder
                    continue
                }
                // select first codec that match a specific MIME type and color format
                val types = codecInfo.supportedTypes
                for (j in types.indices) {
                    if (types[j].equals(mimeType, ignoreCase = true)) {
                        Log.i(TAG, "codec:" + codecInfo.name + ",MIME=" + types[j])
                        val format = selectColorFormat(codecInfo, mimeType)
                        if (format > 0) {
                            return codecInfo
                        }
                    }
                }
            }
            return null
        }

        /**
         * select color format available on specific codec and we can use.
         * @return 0 if no colorFormat is matched
         */
        protected fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
            Log.i(TAG, "selectColorFormat: ")
            var result = 0
            val caps: MediaCodecInfo.CodecCapabilities
            try {
                Thread.currentThread().priority = Thread.MAX_PRIORITY
                caps = codecInfo.getCapabilitiesForType(mimeType)
            } finally {
                Thread.currentThread().priority = Thread.NORM_PRIORITY
            }
            var colorFormat: Int
            for (i in caps.colorFormats.indices) {
                colorFormat = caps.colorFormats[i]
                if (isRecognizedViewoFormat(colorFormat)) {
                    if (result == 0)
                        result = colorFormat
                    break
                }
            }
            if (result == 0)
                Log.e(TAG, "couldn't find a good color format for " + codecInfo.name + " / " + mimeType)
            return result
        }

        /**
         * color formats that we can use in this class
         */
        protected var recognizedFormats: IntArray? = null

        init {
            recognizedFormats = intArrayOf(
                //        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                //        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                //        	MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        private fun isRecognizedViewoFormat(colorFormat: Int): Boolean {
            Log.i(TAG, "isRecognizedViewoFormat:colorFormat=$colorFormat")
            val n = if (recognizedFormats != null) recognizedFormats!!.size else 0
            for (i in 0 until n) {
                if (recognizedFormats!![i] == colorFormat) {
                    return true
                }
            }
            return false
        }
    }

}
