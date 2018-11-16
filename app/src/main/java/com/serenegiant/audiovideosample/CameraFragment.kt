package com.serenegiant.audiovideosample

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: CameraFragment.java
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

import android.app.Fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

import com.serenegiant.encoder.MediaAudioEncoder
import com.serenegiant.encoder.MediaEncoder
import com.serenegiant.encoder.MediaMuxerWrapper
import com.serenegiant.encoder.MediaVideoEncoder

class CameraFragment : Fragment() {

    companion object {
        private const val TAG = "CameraFragment"
    }

    /**
     * for camera preview display
     */
    private var cameraView: CameraGLView? = null
    /**
     * for scale mode display
     */
    private var scaleModeView: TextView? = null
    /**
     * button for start/stop recording
     */
    private var recordButton: ImageButton? = null
    /**
     * muxer for audio/video recording
     */
    private var mediaMuxer: MediaMuxerWrapper? = null

    /**
     * method when touch record button
     */
    private val onClickListener = OnClickListener { view ->
        when (view.id) {
            R.id.cameraView -> {
                val scale_mode = (cameraView!!.scaleMode + 1) % 4
                cameraView!!.scaleMode = scale_mode
                updateScaleModeText()
            }
            R.id.record_button -> if (mediaMuxer == null)
                startRecording()
            else
                stopRecording()
        }
    }

    /**
     * callback methods from encoder
     */
    private val mMediaEncoderListener = object : MediaEncoder.MediaEncoderListener {
        override fun onPrepared(encoder: MediaEncoder) {
            Log.v(TAG, "onPrepared:encoder=$encoder")
            if (encoder is MediaVideoEncoder)
                cameraView!!.setVideoEncoder(encoder)
        }

        override fun onStopped(encoder: MediaEncoder) {
            Log.v(TAG, "onStopped:encoder=$encoder")
            if (encoder is MediaVideoEncoder)
                cameraView!!.setVideoEncoder(null)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        cameraView = rootView.findViewById<View>(R.id.cameraView) as CameraGLView

        val display = activity.windowManager.defaultDisplay

        cameraView!!.setVideoSize(display.width, display.height)
//        cameraView!!.setVideoSize(1280, 720)
        cameraView!!.setOnClickListener(onClickListener)
        scaleModeView = rootView.findViewById<View>(R.id.scalemode_textview) as TextView
        updateScaleModeText()
        recordButton = rootView.findViewById<View>(R.id.record_button) as ImageButton
        recordButton!!.setOnClickListener(onClickListener)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "onResume:")
        cameraView!!.onResume()
    }

    override fun onPause() {
        Log.v(TAG, "onPause:")
        stopRecording()
        cameraView!!.onPause()
        super.onPause()
    }

    private fun updateScaleModeText() {
        val scaleMode = cameraView!!.scaleMode
        scaleModeView!!.text =
            when (scaleMode) {
                0 -> "scale to fit"
                1 -> "keep aspect(viewport)"
                2 -> "keep aspect(matrix)"
                3 -> "keep aspect(crop center)"
                else -> ""
            }
    }

    /**
     * start resorcing
     * This is a sample project and call this on UI thread to avoid being complicated
     * but basically this should be called on private thread because prepareing
     * of encoder is heavy work
     */
    private fun startRecording() {
        Log.v(TAG, "startRecording:")
        try {
            recordButton!!.setColorFilter(-0x10000)    // turn red
            mediaMuxer = MediaMuxerWrapper(".mp4")    // if you record audio only, ".m4a" is also OK.

            MediaVideoEncoder(mediaMuxer, mMediaEncoderListener, cameraView!!.videoWidth, cameraView!!.videoHeight)
            MediaAudioEncoder(mediaMuxer, mMediaEncoderListener)

            mediaMuxer!!.prepare()
            mediaMuxer!!.startRecording()
        } catch (e: IOException) {
            recordButton!!.setColorFilter(0)
            Log.e(TAG, "startCapture:", e)
        }
    }

    /**
     * request stop recording
     */
    private fun stopRecording() {
        Log.v(TAG, "stopRecording:mediaMuxer=$mediaMuxer")
        recordButton!!.setColorFilter(0)    // return to default color
        mediaMuxer?.let {
            it.stopRecording()
            mediaMuxer = null
        }
    }
}
