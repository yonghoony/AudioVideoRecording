package com.serenegiant.audiovideosample

import android.hardware.Camera
import java.util.Collections
import java.util.Comparator

object CameraUtils {

    fun getClosestSupportedSize(supportedSizes: List<Camera.Size>,
                                requestedWidth: Int,
                                requestedHeight: Int): Camera.Size {
        return Collections.min<Camera.Size>(supportedSizes, object : Comparator<Camera.Size> {

            private fun diff(size: Camera.Size): Int {
                return Math.abs(requestedWidth - size.width) + Math.abs(requestedHeight - size.height)
            }

            override fun compare(lhs: Camera.Size, rhs: Camera.Size): Int {
                return diff(lhs) - diff(rhs)
            }
        })
    }
}
