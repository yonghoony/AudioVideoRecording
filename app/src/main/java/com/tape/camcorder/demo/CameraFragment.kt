package com.tape.camcorder.demo

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

import java.io.File
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import com.tape.camcorder.encoder.VideoRecorder
import com.tape.camcorder.views.CameraGLView
import kotlinx.android.synthetic.main.fragment_main.record_button as recordButton
import kotlinx.android.synthetic.main.fragment_main.scalemode_textview as scaleModeTextView
import kotlinx.android.synthetic.main.fragment_main.camera_view as cameraView
import kotlinx.android.synthetic.main.fragment_main.switch_camera_button


class CameraFragment : Fragment() {

    companion object {
        private const val TAG = "CameraFragment"
        private const val COLOR_FILTER_RED = -0x10000
    }

    private lateinit var videoRecorder: VideoRecorder

    /**
     * method when touch record button
     */
    private val onClickListener = OnClickListener { view ->
        when (view.id) {
            R.id.scalemode_textview -> {
                val scaleMode = (cameraView.scaleMode + 1) % 4
                cameraView.scaleMode = scaleMode
                updateScaleModeText()
            }
            R.id.record_button -> if (!videoRecorder.isRecording())
                startRecording()
            else
                stopRecording()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        cameraView.scaleMode = CameraGLView.SCALE_CROP_CENTER

        val display = activity.windowManager.defaultDisplay
        cameraView.setVideoSize(display.width, display.height)
        scaleModeTextView.setOnClickListener(onClickListener)
        updateScaleModeText()
        recordButton.setOnClickListener(onClickListener)
        switch_camera_button.setOnClickListener {
            cameraView.toggleCamera()
        }

        cameraView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                cameraView.focusOnTouch(event.x, event.y)
            }
            false
        }

        videoRecorder = VideoRecorder(cameraView)
    }

    override fun onResume() {
        super.onResume()
        Log.v(TAG, "onResume:")
        cameraView.onResume()
    }

    override fun onPause() {
        Log.v(TAG, "onPause:")
        stopRecording()
        cameraView.onPause()
        super.onPause()
    }

    private fun updateScaleModeText() {
        val scaleMode = cameraView.scaleMode
        scaleModeTextView.text =
            when (scaleMode) {
                0 -> "Scale to fit"
                1 -> "Keep aspect (viewport)"
                2 -> "Keep aspect (matrix)"
                3 -> "Keep aspect (crop center)"
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
            recordButton.setColorFilter(COLOR_FILTER_RED)
            videoRecorder.startRecording(cameraView.measuredWidth, cameraView.measuredHeight)
        } catch (e: IOException) {
            recordButton.setColorFilter(0)
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    /**
     * request stop recording
     */
    private fun stopRecording() {
        Log.v(TAG, "stopRecording")
        recordButton.setColorFilter(0)    // return to default color

        videoRecorder.stopRecording()?.let {
            openVideo(it)
        }
    }

    private fun openVideo(videoPath: String?) {
        videoPath?.let {
            Log.d(TAG, "outputFile ${it} size=${File(it).length() / 1024 / 1024} MB")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                .setDataAndType(Uri.parse(it), "video/mp4")
            startActivity(intent)
        }
    }
}
