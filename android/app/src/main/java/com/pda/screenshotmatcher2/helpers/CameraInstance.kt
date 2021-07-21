package com.pda.screenshotmatcher2.helpers

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.pda.screenshotmatcher2.R
import com.pda.screenshotmatcher2.activities.CameraActivity
import com.pda.screenshotmatcher2.logger.StudyLogger
import com.pda.screenshotmatcher2.network.sendBitmap
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class CameraInstance(cameraActivity: CameraActivity) {


    private val ca = cameraActivity
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080
    private val IMG_TARGET_SIZE = 512

    private var sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(ca)
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

    //Handles still image capture
    private lateinit var mImageReader: ImageReader

    //Camera preview builder
    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder

    //Sensors
    private lateinit var mSensorManager : SensorManager
    private lateinit var mAccelerometer : Sensor
    public var phoneOrientation : Int = 0;

    //Request from builder
    private lateinit var mPreviewRequest: CaptureRequest

    //Prevent exit before closing cam
    private var mCameraOpenCloseLock: Semaphore = Semaphore(1)

    //Permission ID
    private var surfaceTextureHeight: Int = 0
    private var surfaceTextureWidth: Int = 0


    private var startTime: Long = 0
    private var isCapturing: Boolean = false


    fun initialize(){
        setListeners()
    }
    private fun setListeners(){
        mTextureView = ca.findViewById(R.id.preview_view)
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
    }
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
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
            cameraActivity.finish()
        }
    }
    fun openCamera(width: Int, height: Int) {
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
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
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
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
                            StudyLogger.hashMap["preview_width"] = mPreviewSize.width
                            StudyLogger.hashMap["preview_height"] = mPreviewSize.height
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
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
                ca.windowManager.defaultDisplay.getSize(displaySize)
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
        }
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = ca.windowManager.defaultDisplay.rotation
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


        var keyWidth: String = ca.getString(R.string.ca_pref_preview_width)
        var keyHeight: String = ca.getString(R.string.ca_pref_preview_height)

        var keyWidthView: String = ca.getString(R.string.ca_pref_surface_view_width)
        var keyHeightView: String = ca.getString(R.string.ca_pref_surface_view_height)

        val orientation = ca.windowManager.defaultDisplay.rotation
        if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
            keyWidth = ca.getString(R.string.ca_pref_preview_height)
            keyHeight = ca.getString(R.string.ca_pref_preview_width)
            keyWidthView = ca.getString(R.string.ca_pref_preview_height)
            keyHeightView = ca.getString(R.string.ca_pref_surface_view_width)
        }

        val mSavedWidth: Int = sp.getInt(keyHeight, 0)
        val mSavedHeight: Int = sp.getInt(keyWidth, 0)

        val w = aspectRatio.width
        val h = aspectRatio.height
        val mSavedWidthOfView: Int = sp.getInt(keyWidthView, 0)
        val mSavedHeightOfView: Int = sp.getInt(keyHeightView, 0)

        //disabling saving for now
        if ((mSavedWidth != 0 && mSavedHeight != 0) && (mSavedWidthOfView == w && mSavedHeightOfView == h) && false) {
            return Size(mSavedWidth, mSavedHeight)
        } else {
            sp.edit().putInt(keyWidthView, w).apply()
            sp.edit().putInt(keyHeightView, h).apply()
            // resolutions > preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // resolutions < preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()

            for (option in choices) {
                var ratioChecked = option.height / option.width
                var ratioView = textureViewHeight/textureViewWidth
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w
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
                    sp.edit()
                        .putInt(keyWidth, Collections.min(bigEnough,
                            CompareSizesByArea()
                        ).width)
                        .apply()
                    sp.edit()
                        .putInt(keyHeight, Collections.min(bigEnough,
                            CompareSizesByArea()
                        ).height)
                        .apply()
                    Collections.min(bigEnough,
                        CompareSizesByArea()
                    )
                }
                notBigEnough.size > 0 -> {
                    sp.edit()
                        .putInt(keyWidth, Collections.max(notBigEnough,
                            CompareSizesByArea()
                        ).width)
                        .apply()
                    sp.edit().putInt(
                        keyHeight,
                        Collections.max(notBigEnough,
                            CompareSizesByArea()
                        ).height
                    ).apply()
                    Collections.max(notBigEnough,
                        CompareSizesByArea()
                    )
                }
                else -> {
                    choices[0]
                }
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


    fun captureImageWithPreviewExtraction() {
        isCapturing = true
        startTime = System.currentTimeMillis()
        var mBitmap: Bitmap? = mTextureView.bitmap
        val screenOrientation = ca.windowManager.defaultDisplay.rotation

        // screen rotated
        if (screenOrientation != Surface.ROTATION_0 && mBitmap != null) {
            when (screenOrientation) {
                Surface.ROTATION_90 -> mBitmap =
                    rotateBitmapAndAdjustRatio(
                        mBitmap,
                        -90F
                    )
                Surface.ROTATION_270 -> mBitmap =
                    rotateBitmapAndAdjustRatio(
                        mBitmap,
                        90F
                    )
                Surface.ROTATION_180 -> mBitmap =
                    rotateBitmap(
                        mBitmap,
                        180F
                    )
            }
        }
        // screen rotation locked, but physical phone rotated
        else if(screenOrientation == Surface.ROTATION_0 && phoneOrientation != Surface.ROTATION_0 && mBitmap != null){
            when(phoneOrientation){
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

        var mServerURL = ca.getServerUrl()
        if (mBitmap != null && mServerURL != null) {
            if (mServerURL != ""){
                StudyLogger.hashMap["tc_image_captured"] = System.currentTimeMillis()   // image is in memory
                StudyLogger.hashMap["long_side"] = IMG_TARGET_SIZE
                val greyImg =
                    rescale(
                        mBitmap,
                        IMG_TARGET_SIZE
                    )
                val matchingOptions: HashMap<Any?, Any?>? = ca.getMatchingOptionsFromPref()
                sendBitmap(
                    greyImg,
                    mServerURL,
                    ca,
                    matchingOptions
                )
            } else {
                ca.onMatchRequestError()
            }
        } else {
            ca.onMatchRequestError()
        }
    }
}