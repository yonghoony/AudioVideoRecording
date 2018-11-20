package com.tape.camcorder.encoder

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log

class VideoCodecUtils {
    companion object {
        private val TAG = "VideoCodecUtils"
    }

    /**
     * color formats that we can use in this class
     */
    protected val recognizedFormats by lazy {
        intArrayOf(
            //        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            //        	MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            //        	MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    }

    /**
     * select the first codec that match a specific MIME type
     * @param mimeType
     * @return null if no codec matched
     */
    fun selectVideoCodec(mimeType: String): MediaCodecInfo? {
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

    private fun isRecognizedViewoFormat(colorFormat: Int): Boolean {
        Log.i(TAG, "isRecognizedViewoFormat:colorFormat=$colorFormat")
        for (recognizedFormat in recognizedFormats) {
            if (recognizedFormat == colorFormat) {
                return true
            }
        }
        return false
    }
}