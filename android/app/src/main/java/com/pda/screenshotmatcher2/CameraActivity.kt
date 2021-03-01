package com.pda.screenshotmatcher2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ListView
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class CameraActivity : Activity() {

    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080

    //Physical camera
    private var mCameraId: String? = null

    //Preview view
    private var mTextureView: TextureView? = null

    //Active camera session
    private var mCaptureSession: CameraCaptureSession? = null

    //opened camera
    private var mCameraDevice: CameraDevice? = null

    //size of preview
    private var mPreviewSize: Size? = null

    private var mCaptureButton: Button? = null

    //Thread for background tasks
    private var mBackgroundThread: HandlerThread? = null

    //Handler for background tasks
    private var mBackgroundHandler: Handler? = null

    //Handles still image capture
    private var mImageReader: ImageReader? = null

    //Camera preview builder
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    //Camera capture builder
    private var mCaptureRequestBuilder: CaptureRequest.Builder? = null

    //Request from builder
    private var mPreviewRequest: CaptureRequest? = null

    //Prevent exit before closing cam
    private var mCameraOpenCloseLock: Semaphore? = Semaphore(1)

    //Permission ID
    private val REQUEST_CAMERA_PERMISSION: Int = 1

    //Other UI Views
    private var mSelectDeviceButton: Button? = null
    private var mSelectDeviceList: ListView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        initiateViews()
        setViewListeners()
    }

    private fun initiateViews() {
        mTextureView = findViewById(R.id.preview_view)
        mCaptureButton = findViewById(R.id.capture_button)
        mSelectDeviceButton = findViewById(R.id.select_device_button)
        mSelectDeviceButton!!.text = getString(R.string.select_device_button_notConnected_en)
        mSelectDeviceButton!!.background = resources.getDrawable(R.drawable.select_device_disconnected)
        mSelectDeviceList = findViewById(R.id.select_device_list)
    }

    private fun setViewListeners() {
        mTextureView!!.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    openCamera(width, height)
                }

                override fun onSurfaceTextureSizeChanged(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    configureTransform(width, height)
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
            }

        mCaptureButton?.setOnClickListener(View.OnClickListener {
            campureImageWithCaptureRequest()
            //captureImageWithPreviewExtraction()
        })

        mSelectDeviceButton?.setOnClickListener(View.OnClickListener {
            if (mSelectDeviceList?.isVisible!!){
                mSelectDeviceList!!.setVisibility(View.INVISIBLE)
                Log.d("CAMERA_UI", "Hiding list")
            }   else{
                mSelectDeviceList!!.setVisibility(View.VISIBLE)
                Log.d("CAMERA_UI", "Showing list")
            }
        })
    }

    //Capture an image by simply saving the current frame of the Texture View
    private fun captureImageWithPreviewExtraction() {
        Log.d("BITMAP", "Button pressed")
        var mBitmap: Bitmap? = mTextureView!!.getBitmap()
        if (mBitmap != null) {
            Log.d("BITMAP", "calling savePhotoToDisk")
            savePhotoToDisk(mBitmap, null, null, 512)
        }
    }

    //Capture an image by using a Capture Request
    private fun campureImageWithCaptureRequest() {
        val outputSurfaces: MutableList<Surface> = LinkedList()
        outputSurfaces.add(mImageReader!!.getSurface())

        mCaptureRequestBuilder =
            mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        mCaptureRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )

        val ORIENTATIONS = SparseIntArray()
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
        val rotation = windowManager.defaultDisplay.rotation

        mCaptureRequestBuilder!!.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))
        mCaptureRequestBuilder!!.addTarget(outputSurfaces[0])

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
            ) {
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                Log.d("CAMERA", "Capture complete")
            }
        }
        mCaptureSession!!.capture(
            mCaptureRequestBuilder!!.build(),
            captureCallback,
            mBackgroundHandler
        )
    }

    //Callback for camera changes
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d("CAMERA", "Camera opened")
            mCameraOpenCloseLock?.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock?.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock?.release()
            cameraDevice.close()
            mCameraDevice = null
            finish()
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            val surface = Surface(texture)

            //Capturing for preview
            mPreviewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)

            mCameraDevice!!.createCaptureSession(
                Arrays.asList(surface, mImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == mCameraDevice) {
                            return
                        }

                        mCaptureSession = cameraCaptureSession
                        try {
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )

                            mPreviewRequest = mPreviewRequestBuilder!!.build()
                            mCaptureSession!!.setRepeatingRequest(
                                mPreviewRequest!!,
                                null, mBackgroundHandler
                            )
                            Log.d("CAMERA", "Start Preview with size: $mPreviewSize")
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        Log.d("CAMERA", "camera config Failed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Use largest size available
                val largest: Size =
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)).get(0).get(0)

                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 2
                )

                //Callback for receiving images as soon as they are available
                mImageReader!!.setOnImageAvailableListener({ reader ->
                    var image: Image? = null
                    try {
                        image = reader!!.acquireLatestImage()
                        savePhotoToDisk(null, image, null, 512)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    image?.close()
                }, mBackgroundHandler)

                val displaySize = Point()
                windowManager.defaultDisplay.getSize(displaySize)
                var maxPreviewWidth: Int = displaySize.x
                var maxPreviewHeight: Int = displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }


                mPreviewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, maxPreviewWidth,
                    maxPreviewHeight, largest
                )
                mCameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            Log.d("CAMERA", "Camera2 API unavailable")
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (null == mTextureView || null == mPreviewSize) {
            return
        }
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0F, 0F,
            mPreviewSize!!.height.toFloat(),
            mPreviewSize!!.width.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize!!.height,
                viewWidth.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180F, centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    private fun chooseOptimalSize(
        choices: Array<Size>, textureViewWidth: Int,
        textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size
    ): Size? {

        // resolutions > preview Surface
        val bigEnough: MutableList<Size> = ArrayList()
        // resolutions < preview Surface
        val notBigEnough: MutableList<Size> = ArrayList()
        val w = aspectRatio.width
        val h = aspectRatio.height
        for (option in choices) {
            Log.d("CAMERA", "Checking option $option")
            if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w
            ) {
                if (option.width >= textureViewWidth &&
                    option.height >= textureViewHeight
                ) {
                    bigEnough.add(option)
                    Log.d("CAMERA", "Added $option to big resolutions")
                } else {
                    notBigEnough.add(option)
                    Log.d("CAMERA", "Added $option to small resolutions")
                }
            }
        }

        return if (bigEnough.size > 0) {
            Collections.min(bigEnough, CompareSizesByArea())
        } else if (notBigEnough.size > 0) {
            Collections.max(notBigEnough, CompareSizesByArea())
        } else {
            Log.e("CAMERA", "No suitable preview size")
            choices[0]
        }
    }

    internal class CompareSizesByArea : Comparator<Size?> {
        override fun compare(o1: Size?, o2: Size?): Int {
            if (o1 != null) {
                if (o2 != null) {
                    return java.lang.Long.signum(o1.width.toLong() * o2.height - o1.width.toLong() * o2.height)
                }
            }
            return -1
        }
    }

    private fun openCamera(width: Int, height: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPerm()
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock!!.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Camera lock opening timeout")
            }
            manager.openCamera(mCameraId!!, mStateCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Camera lock opening interrupted", e)
        }
    }

    private fun requestPerm() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> { }

            else -> {
                requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    12
                )
            }
        }
    }
}