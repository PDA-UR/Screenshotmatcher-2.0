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
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
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
 * @property ci The [CameraInstance] passed through the constructor
 * @property ca The [Activity] of the [CameraInstance][ci]
 *
 * @property cameraId The physical camera id
 * @property captureSession The active camera capture session
 * @property cameraDevice The open camera
 * @property previewTextureView The [TextureView], on which the camera preview image will be displayed
 * @property previewSize The [Size] of the camera preview image
 * @property imageReader Handler for still image capture
 * @property previewRequestBuilder The builder for [previewRequest]
 * @property previewRequest The [CaptureRequest] to get camera images for the preview
 * @property cameraOpenCloseLock Prevents closing the app without closing the camera first
 *
 */
class CameraProvider(cameraInstance: CameraInstance) {
    private val ci = cameraInstance
    private var ca: Activity? = ci.getActivity()

    /**
     * Options for the camera image.
     */
    companion object OPTIONS {
        val MAX_PREVIEW_WIDTH = 1920
        val MAX_PREVIEW_HEIGHT = 1080
        val IMG_TARGET_SIZE = 512
    }

    private lateinit var cameraId: String
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var cameraDevice: CameraDevice
    private lateinit var previewTextureView: TextureView
    private lateinit var previewSize: Size
    private lateinit var imageReader: ImageReader
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var previewRequest: CaptureRequest
    private var cameraOpenCloseLock: Semaphore = Semaphore(1)

    /**
     * Starts the camera preview on [previewTextureView].
     */
    fun start(){
        Log.d("CA", "CA-L cameraProvider start")
        initializeTextureView()
    }

    /**
     * Initializes [previewTextureView] and sets the [SurfaceTextureListener][TextureView.SurfaceTextureListener].
     */
    private fun initializeTextureView(){
        previewTextureView = ci.getTextureView()
        previewTextureView.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    Log.d("CA", "CA-L cameraProvider initializeTextureView onSurfaceTextureAvailable")
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
        if (previewTextureView.isAvailable) {
            Log.d("CA", "CA-L cameraProvider initializeTextureView isAvailable, starting")
            openCamera(previewTextureView.width, previewTextureView.height)
        }
    }

    /**
     * Opens the camera with the provided [width] and [height] if [Manifest.permission.CAMERA] is granted.
	 */
    fun openCamera(width: Int, height: Int) {
        Log.d("CA", "CA-L cameraProvider openCamera")
        setUpCameraOutputs(width, height)
        if (ca !== null) {
            val manager =
                ca?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (ActivityCompat.checkSelfPermission(
                    ca!!,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("CA", "CA-L cameraProvider openCamera permission not granted")
                return
            }
            try {
                cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)
                manager.openCamera(cameraId, cameraDeviceStateCallback, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                throw RuntimeException("Camera lock opening interrupted", e)
            }
        }

    }

    /**
     * Sets up the camera output by choosing the optimal size from all available cameras.
     *
	 * @param width The target camera image width
	 * @param height The target camera image height
	 */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager =
            ca?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                //skip front facing cameras
                if (CameraCharacteristics.LENS_FACING_FRONT == characteristics[CameraCharacteristics.LENS_FACING]) continue

                // Use largest size available
                val largest: Size = listOf(map.getOutputSizes(ImageFormat.JPEG))[0][0]
                imageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 2
                )

                val displaySize = Point()
                ca?.windowManager?.defaultDisplay?.getSize(displaySize)
                var maxPreviewWidth: Int = displaySize.x
                var maxPreviewHeight: Int = displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                previewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, maxPreviewWidth,
                    maxPreviewHeight, largest
                )!!
                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
        }
    }

    /**
     * Callback for handling all state changes of the open camera.
     */
    private val cameraDeviceStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraProvider.cameraDevice = cameraDevice
            createCameraCaptureSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            ca?.finish()
        }
    }

    /**
     * Creates a [captureSession] on [previewTextureView], using [previewRequestBuilder].
     */
    private fun createCameraCaptureSession() {
        try {
            val texture = previewTextureView.surfaceTexture!!.apply {
                setDefaultBufferSize(previewSize.width, previewSize.height)
            }
            val surface = Surface(texture)

            //Capturing for preview
            previewRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice.createCaptureSession(
                listOf(surface, imageReader.surface),
                cameraCaptureSessionStateCallback,
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Callback for handling all state changes of [captureSession].
     */
    private var cameraCaptureSessionStateCallback: CameraCaptureSession.StateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            captureSession = cameraCaptureSession
            try {
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )

                previewRequest = previewRequestBuilder.build()
                captureSession.setRepeatingRequest(
                    previewRequest,
                    null, null
                )
                StudyLogger.hashMap["preview_width"] = previewSize.width
                StudyLogger.hashMap["preview_height"] = previewSize.height
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

    /**
     * Helper method for choosing a [Size] of a given set of [choices], which best fits the given constraints.
     *
	 * @param choices All available camera output sizes
	 * @param textureViewWidth The width of [previewTextureView]
	 * @param textureViewHeight The height of [previewTextureView]
	 * @param maxWidth The maximum width
	 * @param maxHeight The maximum height
	 * @param aspectRatio The aspect ratio
	 */
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


    /**
     * Captures the current frame of the [previewTextureView] and returns it as a [Bitmap].
	 */
    fun captureImageWithPreviewExtraction(): Bitmap? {
        var mBitmap: Bitmap? = previewTextureView.bitmap

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

    fun stop() {
        cameraDevice.close()
        ca = null
    }
}