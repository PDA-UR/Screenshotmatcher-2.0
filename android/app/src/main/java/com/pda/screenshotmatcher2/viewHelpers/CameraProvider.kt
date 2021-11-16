package com.pda.screenshotmatcher2.viewHelpers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.views.activities.CameraActivity
import com.pda.screenshotmatcher2.utils.CompareSizesByArea
import com.pda.screenshotmatcher2.utils.rotateBitmap
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.views.activities.interfaces.CameraInstance
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


/**
 * A class that provides [Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary) features to any class that implements the [CameraInstance] interface.
 *
 * @constructor A class that implements the [CameraInstance] interface
 *
 * @property ci The [CameraInstance] passed through the constructor
 * @property ca The [Activity] of the [ci]
 *
 */
class CameraProvider(cameraInstance: CameraInstance) {
    private val ci = cameraInstance
    private val ca: Activity = ci.getActivity()

    /**
     * Options for the camera image.
     */
    companion object OPTIONS {
        val MAX_PREVIEW_WIDTH = 1920
        val MAX_PREVIEW_HEIGHT = 1080
        val IMG_TARGET_SIZE = 512
    }

    //Physical camera ID
    private lateinit var mCameraId: String
    //Active camera session
    private lateinit var mCaptureSession: CameraCaptureSession
    //opened camera
    private lateinit var mCameraDevice: CameraDevice

    //Preview view
    private lateinit var mTextureView: TextureView
    //size of preview
    private lateinit var mPreviewSize: Size
    private var surfaceTextureHeight: Int = 0
    private var surfaceTextureWidth: Int = 0

    //Handles still image capture
    private lateinit var mImageReader: ImageReader
    //Camera preview builder
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
    //Request from builder
    private lateinit var mPreviewRequest: CaptureRequest

    //Prevent exit before closing cam
    private var mCameraOpenCloseLock: Semaphore = Semaphore(1)

    private var startTime: Long = 0
    var isCapturing: Boolean = false

    fun start(){
        initializeTextureView()
    }
    
    private fun initializeTextureView(){
        mTextureView = ci.getTextureView()
        mTextureView.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    surfaceTextureWidth = width
                    surfaceTextureHeight = height
                    openCamera(width, height)
                }

                override fun onSurfaceTextureSizeChanged(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
            }
    }

    fun openCamera(width: Int, height: Int) {
        setUpCameraOutputs(width, height)
        val manager =
            ca.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (ActivityCompat.checkSelfPermission(
                ca,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)
            manager.openCamera(mCameraId, mCameraDeviceStateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Camera lock opening interrupted", e)
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager =
            ca.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                //skip front facing cameras
                if (CameraCharacteristics.LENS_FACING_FRONT == characteristics[CameraCharacteristics.LENS_FACING]) continue

                // Use largest size available
                val largest: Size = listOf(map.getOutputSizes(ImageFormat.JPEG))[0][0]
                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 2
                )

                val displaySize = Point()
                ca.windowManager.defaultDisplay.getSize(displaySize)
                var maxPreviewWidth: Int = displaySize.x
                var maxPreviewHeight: Int = displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                mPreviewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, maxPreviewWidth,
                    maxPreviewHeight, largest
                )!!
                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
        }
    }

    private val mCameraDeviceStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraCaptureSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            ca.finish()
        }
    }

    private fun createCameraCaptureSession() {
        try {
            val texture = mTextureView.surfaceTexture!!.apply { 
                setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
            }
            val surface = Surface(texture)

            //Capturing for preview
            mPreviewRequestBuilder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder.addTarget(surface)

            mCameraDevice.createCaptureSession(
                listOf(surface, mImageReader.surface),
                mCameraCaptureSessionStateCallback,
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private var mCameraCaptureSessionStateCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            mCaptureSession = cameraCaptureSession
            try {
                mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )

                mPreviewRequest = mPreviewRequestBuilder.build()
                mCaptureSession.setRepeatingRequest(
                    mPreviewRequest,
                    null, null
                )
                StudyLogger.hashMap["preview_width"] = mPreviewSize.width
                StudyLogger.hashMap["preview_height"] = mPreviewSize.height
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(
            cameraCaptureSession: CameraCaptureSession
        ) {
            throw RuntimeException("Failed to configure CameraCaptureSession")
        }
    }
    
    private fun chooseOptimalSize(
        choices: Array<Size>, textureViewWidth: Int,
        textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
    ): Size? {
        val w = aspectRatio.width
        val h = aspectRatio.height

        // list for resolutions > preview Surface
        val bigEnough: MutableList<Size> = ArrayList()
        // list for resolutions < preview Surface
        val notBigEnough: MutableList<Size> = ArrayList()

        for (option in choices) {
            val ratioChecked = option.height / option.width
            val ratioView = textureViewHeight / textureViewWidth
            if (option.width <= maxWidth &&
                option.height <= maxHeight &&
                option.height == option.width * h / w
            ) {
                if (option.width >= textureViewWidth &&
                    option.height >= textureViewHeight &&
                    ratioChecked == ratioView
                ) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }
        return when {
            bigEnough.size > 0 -> {
                Collections.min(bigEnough,
                    CompareSizesByArea()
                )
            }
            notBigEnough.size > 0 -> {
                Collections.max(notBigEnough,
                    CompareSizesByArea()
                )
            }
            else -> {
                choices[0]
            }
        }
    }


    fun captureImageWithPreviewExtraction(): Bitmap? {
        isCapturing = true
        startTime = System.currentTimeMillis()
        var mBitmap: Bitmap? = mTextureView.bitmap

        if(ci.getOrientation() != Surface.ROTATION_0 && mBitmap != null){
            when(ci.getOrientation()){
                Surface.ROTATION_90 -> mBitmap =
                    rotateBitmap(
                        mBitmap,
                        -90F
                    )
                Surface.ROTATION_270 -> mBitmap =
                    rotateBitmap(mBitmap, 90F)
                Surface.ROTATION_180 -> mBitmap =
                    rotateBitmap(
                        mBitmap,
                        180F
                    )
            }
        }
        return mBitmap
    }
}