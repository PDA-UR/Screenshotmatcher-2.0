package com.pda.screenshotmatcher2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentTransaction
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class CameraActivity : AppCompatActivity() {
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080
    private val IMG_TARGET_SIZE = 512

    //Physical camera
    private lateinit var mCameraId: String

    //Preview view
    private lateinit var mTextureView: TextureView

    //Active camera session
    private lateinit var mCaptureSession: CameraCaptureSession

    //opened camera
    private lateinit var mCameraDevice: CameraDevice

    //size of preview
    private lateinit var mPreviewSize: Size

    //Handler for background tasks
    private lateinit var mBackgroundHandler: Handler

    //Handles still image capture
    private lateinit var mImageReader: ImageReader

    //Camera preview builder
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder

    //Request from builder
    private lateinit var mPreviewRequest: CaptureRequest

    //Prevent exit before closing cam
    private var mCameraOpenCloseLock: Semaphore = Semaphore(1)

    //Permission ID
    private val REQUEST_CAMERA_PERMISSION: Int = 1
    private var surfaceTextureHeight: Int = 0
    private var surfaceTextureWidth: Int = 0

    //Other UI Views
    private lateinit var mSelectDeviceButton: Button
    private lateinit var mSelectDeviceList: ListView
    private lateinit var mCaptureButton: Button
    private lateinit var backgroundDarkening: FrameLayout

    private var mServerURL: String = ""
    private var mUserID: String = ""
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        supportActionBar?.hide()
        verifyPermissions(this)
        getServerURL()
        getUserID()
        initViews()
        setViewListeners()
    }

    private fun initViews() {
        mTextureView = findViewById(R.id.preview_view)
        mCaptureButton = findViewById(R.id.capture_button)
        mSelectDeviceButton = findViewById(R.id.select_device_button)
        mSelectDeviceButton.text = getString(R.string.select_device_button_notConnected_en)
        mSelectDeviceButton.background =
            resources.getDrawable(R.drawable.select_device_disconnected)
        mSelectDeviceList = findViewById(R.id.select_device_list)
        backgroundDarkening = findViewById(R.id.ca_dark_background)
        backgroundDarkening.setOnClickListener { Log.d("bg", "background clicked") }
    }

    private fun setViewListeners() {
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
                    configureTransform(width, height)
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
            }

        mCaptureButton.setOnClickListener {
            StudyLogger.hashMap["client_id"] = mUserID
            StudyLogger.hashMap["tc_button_pressed"] = System.currentTimeMillis()
            captureImageWithPreviewExtraction()
        }

        mSelectDeviceButton.setOnClickListener {
            if (mSelectDeviceList.isVisible) {
                mSelectDeviceList.visibility = View.INVISIBLE
                getServerURL()
            } else {
                mSelectDeviceList.visibility = View.VISIBLE
            }
        }
    }

    //Callback for camera changes
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d("CAMERA", "Camera opened")
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            finish()
        }
    }


    private fun openCamera(width: Int, height: Int) {
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Camera lock opening timeout")
            }

            manager.openCamera(mCameraId, mStateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Camera lock opening interrupted", e)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
            val surface = Surface(texture)

            //Capturing for preview
            mPreviewRequestBuilder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder.addTarget(surface)

            mCameraDevice.createCaptureSession(
                listOf(surface, mImageReader.surface),
                object : CameraCaptureSession.StateCallback() {
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
                            Log.d("CAMERA", "Start Preview with size: $mPreviewSize")
                            StudyLogger.hashMap["preview_width"] = mPreviewSize.width
                            StudyLogger.hashMap["preview_height"] = mPreviewSize.height
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
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Use largest size available
                val largest: Size = listOf(map.getOutputSizes(ImageFormat.JPEG))[0][0]

                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 2
                )

                //Callback for receiving images as soon as they are available
//                mImageReader!!.setOnImageAvailableListener({ reader ->
//                    var image: Image? = null
//                    try {
//                        image = reader!!.acquireLatestImage()
//                        val greyImg = savePhotoToDisk(null, image, null, 512)
//                        sendFile(greyImg, mServerURL)
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                    image?.close()
//                }, mBackgroundHandler)


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
                )!!
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
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0F, 0F,
            mPreviewSize.height.toFloat(),
            mPreviewSize.width.toFloat()
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize.height,
                viewWidth.toFloat() / mPreviewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180F, centerX, centerY)
        }
        mTextureView.setTransform(matrix)
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

        return when {
            bigEnough.size > 0 -> {
                Collections.min(bigEnough, CompareSizesByArea())
            }
            notBigEnough.size > 0 -> {
                Collections.max(notBigEnough, CompareSizesByArea())
            }
            else -> {
                Log.e("CAMERA", "No suitable preview size")
                choices[0]
            }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("CAMERA", "Permission Callback Code $requestCode")
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    for (permission in permissions) {
                        Log.d("CAMERA", "GOT permission $permission")
                    }
                    Log.d("CAMERA", "Got Permissions")
                    openCamera(surfaceTextureHeight, surfaceTextureWidth)

                } else {
                    Log.d("CAMERA", "No permission")
                }
            }
        }
    }

    //Capture an image by simply saving the current frame of the Texture View
    private fun captureImageWithPreviewExtraction() {
        Log.v("TIMING", "Button pressed")
        startTime = System.currentTimeMillis()
        val mBitmap: Bitmap? = mTextureView.bitmap
        if (mBitmap != null) {
            StudyLogger.hashMap["tc_image_captured"] = System.currentTimeMillis()
            StudyLogger.hashMap["long_side"] = IMG_TARGET_SIZE
            val greyImg = rescale(mBitmap, IMG_TARGET_SIZE)
            Log.v("TIMING", "Image rescaled.")
            sendBitmap(greyImg, mServerURL, this, this)
        }
    }

    private fun getUserID() {
        val sharedPreferences =
            this.getSharedPreferences("com.pda.screenshotmatcher2", Context.MODE_PRIVATE)
        mUserID = sharedPreferences.getString("uid", "").toString()
        if (mUserID.isEmpty()) {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            mUserID = (1..20).map { allowedChars.random() }.joinToString("")
            sharedPreferences.edit().putString("uid", mUserID).apply()
        }
        Log.v("TEST", mUserID)
    }

    private fun getServerURL() {
        Thread {
            Log.v("TEST", "discovering server...")
            mServerURL = discoverServerOnNetwork(this, 49050, "")
            onServerURLget(mServerURL)
        }.start()
    }

    private fun onServerURLget(serverURL: String) {
        Thread {
            Log.v("TIMING", "Got URL")
            runOnUiThread {
                mSelectDeviceButton.background =
                    resources.getDrawable(R.drawable.select_device_connected)
                mSelectDeviceButton.text = serverURL
            }
        }.start()
    }

    private fun startResultsActivity(matchID: String, img: ByteArray) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("matchID", matchID)
            putExtra("img", img)
            putExtra("ServerURL", mServerURL)
//            putExtra("DownloadID", downloadID)
        }
        startActivity(intent)
    }

    fun onMatchResult(matchID: String, img: ByteArray) {
        startResultsActivity(matchID, img)
    }


    fun openErrorFragment(uid: String, url: String) {
        Log.d("FRAG", "opening fragment")
        val errorFragment = ErrorFragment()

        val bundle = Bundle()
        bundle.putString(UID_KEY, uid)
        bundle.putString(URL_KEY, url)
        errorFragment.arguments = bundle

        this.supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container_view, errorFragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .commit()

        backgroundDarkening.visibility = View.VISIBLE
    }
}