package com.tape.camcorder.encoder

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: VideoEncoder.java
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
import android.media.MediaFormat
import android.opengl.EGLContext
import android.util.Log
import android.view.Surface

import com.tape.camcorder.utils.RenderHandler

class VideoEncoder(muxer: MediaMuxerWrapper,
                   listener: MediaEncoder.MediaEncoderListener,
                   private val width: Int,
                   private val height: Int)
    : MediaEncoder(muxer, listener) {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
        // parameters for recording
        private const val FRAME_RATE = 25
        private const val I_FRAME_INTERVAL = 10
        private const val BPP = 0.25f
    }

    private val videoCodecUtils by lazy { VideoCodecUtils() }
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

        val videoCodecInfo = videoCodecUtils.selectVideoCodec(MIME_TYPE)
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for $MIME_TYPE")
            return
        }
        Log.i(TAG, "selected codec: ${videoCodecInfo.name}")

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)    // API >= 18
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        Log.i(TAG, "format: $format")

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mMediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
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
        val bitrate = (BPP * FRAME_RATE.toFloat() * width.toFloat() * height.toFloat()).toInt()
        val bitrateMbps = bitrate.toFloat() / 1024f / 1024f
        Log.i(TAG, "bitrate=%5.2f[Mbps]".format(bitrateMbps))
        return bitrate
    }

    override fun signalEndOfInputStream() {
        Log.d(TAG, "sending EOS to encoder")
        mMediaCodec.signalEndOfInputStream()    // API >= 18
        mIsEOS = true
    }

}
