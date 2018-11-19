package com.serenegiant.audiovideosample;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: CameraGLView.java
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

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import com.serenegiant.encoder.MediaVideoEncoder;

/**
 * Sub class of GLSurfaceView to display camera preview and write video frame to capturing surface
 */
public final class CameraGLView extends GLSurfaceView {

	private static final String TAG = "CameraGLView";

	public static final int SCALE_STRETCH_FIT = 0;
	public static final int SCALE_KEEP_ASPECT_VIEWPORT = 1;
	public static final int SCALE_KEEP_ASPECT = 2;
	public static final int SCALE_CROP_CENTER = 3;

	private final Object lock = new Object();
	private final CameraSurfaceRenderer mRenderer;
	public boolean hasSurface;
	public CameraHandler mCameraHandler = null;
	public int mVideoWidth, mVideoHeight;
	private int mRotation;
	private int mScaleMode = SCALE_CROP_CENTER;
	private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

	public CameraGLView(final Context context) {
		this(context, null, 0);
	}

	public CameraGLView(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CameraGLView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs);
		Log.v(TAG, "CameraGLView:");
		mRenderer = new CameraSurfaceRenderer(this);
		setEGLContextClientVersion(2);	// GLES 2.0, API >= 8
		setRenderer(mRenderer);
/*		// the frequency of refreshing of camera preview is at most 15 fps
		// and RENDERMODE_WHEN_DIRTY is better to reduce power consumption
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY); */
	}

	@Override
	public void onResume() {
		Log.v(TAG, "onResume:");
		super.onResume();
		if (hasSurface) {
			if (mCameraHandler == null) {
				Log.v(TAG, "surface already exist");
				startPreview(getWidth(),  getHeight());
			}
		}
	}

	@Override
	public void onPause() {
		Log.v(TAG, "onPause:");
		// just request stop prviewing
		stopPreview(false);
		super.onPause();
	}

	public void setScaleMode(final int mode) {
		if (mScaleMode != mode) {
			mScaleMode = mode;
			queueEvent(new Runnable() {
				@Override
				public void run() {
					mRenderer.updateViewport();
				}
			});
		}
	}

	public int getScaleMode() {
		return mScaleMode;
	}

	public void setVideoSize(final int width, final int height) {
		if ((mRotation % 180) == 0) {
			mVideoWidth = width;
			mVideoHeight = height;
		} else {
			mVideoWidth = height;
			mVideoHeight = width;
		}
		queueEvent(new Runnable() {
			@Override
			public void run() {
				mRenderer.updateViewport();
			}
		});
	}

	public int getVideoWidth() {
		return mVideoWidth;
	}

	public int getVideoHeight() {
		return mVideoHeight;
	}

	public SurfaceTexture getSurfaceTexture() {
		Log.v(TAG, "getSurfaceTexture:");
		return mRenderer != null ? mRenderer.mSTexture : null;
	}

	@Override
	public void surfaceDestroyed(final SurfaceHolder holder) {
		Log.v(TAG, "surfaceDestroyed:");
		// wait for finish previewing here
		// otherwise camera try to display on un-exist Surface and some error will occure
		stopPreview(true);
		mCameraHandler = null;
		hasSurface = false;
		mRenderer.onSurfaceDestroyed();
		super.surfaceDestroyed(holder);
	}

	public void setVideoEncoder(final MediaVideoEncoder encoder) {
		Log.v(TAG, "setVideoEncoder:tex_id=" + mRenderer.hTex + ",encoder=" + encoder);
		queueEvent(new Runnable() {
			@Override
			public void run() {
				synchronized (mRenderer) {
					if (encoder != null) {
						encoder.setEglContext(EGL14.eglGetCurrentContext(), mRenderer.hTex);
					}
					mRenderer.setVideoEncoder(encoder);
				}
			}
		});
	}

	public void toggleCamera() {
		if (cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
		} else {
			cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
		}

		stopPreview(true);
		startPreview(getWidth(),  getHeight());
	}

	public void startPreview(final int width, final int height) {
		Log.i(TAG, "startPreview [" + width + ", " + height + "]");
		synchronized (lock) {
			if (mCameraHandler == null) {
				final CameraThread thread = new CameraThread(this);
				thread.start();
				mCameraHandler = thread.getHandler();
			}
			mCameraHandler.startPreview(width, height, cameraId);
		}
	}

	private void stopPreview(boolean needWait) {
		if (mCameraHandler != null) {
			mCameraHandler.stopPreview(needWait);
		}
	}

	public void setRotation(int rotation) {
		mRotation = rotation;
	}
}
