package com.serenegiant.audiovideosample

import android.util.Log
import com.serenegiant.encoder.AudioEncoder
import com.serenegiant.encoder.MediaEncoder
import com.serenegiant.encoder.MediaMuxerWrapper
import com.serenegiant.encoder.VideoEncoder

class VideoRecorder(cameraView: CameraGLView) {

    companion object {
        private const val TAG = "VideoRecorder"
        private const val FILE_EXT = ".mp4"
    }

    /**
     * muxer for audio/video recording
     */
    private var mediaMuxer: MediaMuxerWrapper? = null

    /**
     * callback methods from encoder
     */
    private val mediaEncoderListener = object : MediaEncoder.MediaEncoderListener {
        override fun onPrepared(encoder: MediaEncoder) {
            Log.v(TAG, "onPrepared encoder=$encoder")
            if (encoder is VideoEncoder)
                cameraView.setVideoEncoder(encoder)
        }

        override fun onStopped(encoder: MediaEncoder) {
            Log.v(TAG, "onStopped encoder=$encoder")
            if (encoder is VideoEncoder)
                cameraView.setVideoEncoder(null)
        }
    }

    fun startRecording(width: Int, height: Int) {
        mediaMuxer = MediaMuxerWrapper(FILE_EXT)
        VideoEncoder(mediaMuxer!!, mediaEncoderListener, width, height)
        AudioEncoder(mediaMuxer!!, mediaEncoderListener)
        mediaMuxer!!.prepare()
        mediaMuxer!!.startRecording()
    }

    fun isRecording(): Boolean {
        return mediaMuxer != null
    }

    fun stopRecording(): String? {
        var outputPath: String? = null
        mediaMuxer?.let {
            it.stopRecording()
            outputPath = it.outputPath
        }
        mediaMuxer = null
        return outputPath
    }
}