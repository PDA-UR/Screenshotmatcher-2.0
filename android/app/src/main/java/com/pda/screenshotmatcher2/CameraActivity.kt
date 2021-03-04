package com.pda.screenshotmatcher2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import net.gotev.uploadservice.UploadServiceConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


class CameraActivity : AppCompatActivity() {

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
    private var surfaceTextureHeight: Int = 0
    private var surfaceTextureWidth: Int = 0

    private var serverURL: String = ""
    private var startTime: Long = 0

    //Other UI Views
    private var mSelectDeviceButton: Button? = null
    private var mSelectDeviceList: ListView? = null
    private var mCaptureButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        supportActionBar?.hide()

        verifyPermissions(this)
        getServerURL()
        createNotificationChannel()
        UploadServiceConfig.initialize(
            context = this.application,
            defaultNotificationChannel = CameraActivity.notificationChannelID,
            debug = BuildConfig.DEBUG
        )
        initViews()
        setViewListeners()
    }


    private fun initViews() {
        mTextureView = findViewById(R.id.preview_view)
        mCaptureButton = findViewById(R.id.capture_button)
        mSelectDeviceButton = findViewById(R.id.select_device_button)
        mSelectDeviceButton!!.text = getString(R.string.select_device_button_notConnected_en)
        mSelectDeviceButton!!.background =
            resources.getDrawable(R.drawable.select_device_disconnected)
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

        mCaptureButton?.setOnClickListener(View.OnClickListener {
            //captureImageWithCaptureRequest()
            captureImageWithPreviewExtraction()
        })

        mSelectDeviceButton?.setOnClickListener(View.OnClickListener {
            if (mSelectDeviceList?.isVisible!!) {
                mSelectDeviceList!!.visibility = View.INVISIBLE
                getServerURL()
            } else {
                mSelectDeviceList!!.visibility = View.VISIBLE
            }
        })
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

    //Capture an image by simply saving the current frame of the Texture View
    private fun captureImageWithPreviewExtraction() {
        Log.v("TIMING", "Button pressed")
        startTime = System.currentTimeMillis()
        val mBitmap: Bitmap? = mTextureView!!.bitmap
        if (mBitmap != null) {
//            Log.d("BITMAP", "calling savePhotoToDisk")
//            val greyImg = savePhotoToDisk(mBitmap, null, null, 512)
//            sendFile(greyImg, "http://192.168.178.34:49049")
            val greyImg = rescale(mBitmap, 512)
            Log.v("TIMING", "Image rescaled.")
            sendBitmap(greyImg, serverURL)
        }
    }

    //Capture an image by using a Capture Request
    private fun captureImageWithCaptureRequest() {
        val outputSurfaces: MutableList<Surface> = LinkedList()
        outputSurfaces.add(mImageReader!!.getSurface())

        mCaptureRequestBuilder =
            mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        mCaptureRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )

        val orientations = SparseIntArray()
        orientations.append(Surface.ROTATION_0, 90)
        orientations.append(Surface.ROTATION_90, 0)
        orientations.append(Surface.ROTATION_180, 270)
        orientations.append(Surface.ROTATION_270, 180)
        val rotation = windowManager.defaultDisplay.rotation

        mCaptureRequestBuilder!!.set(CaptureRequest.JPEG_ORIENTATION, orientations.get(rotation))
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
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                // Use largest size available
                val largest: Size = listOf(map.getOutputSizes(ImageFormat.JPEG))[0][0]

                mImageReader = ImageReader.newInstance(
                    largest.width, largest.height,
                    ImageFormat.JPEG, 2
                )

                //Callback for receiving images as soon as they are available
                mImageReader!!.setOnImageAvailableListener({ reader ->
                    var image: Image? = null
                    try {
                        image = reader!!.acquireLatestImage()
                        val greyImg = savePhotoToDisk(null, image, null, 512)
                        sendFile(greyImg, serverURL)
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
        super.onRequestPermissionsResult(requestCode, permissions!!, grantResults)
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

    private fun getServerURL() {
        Thread {
            Log.v("TEST", "discovering server...")
            serverURL = discoverServerOnNetwork(this, 49050, "")
            onServerURLget(serverURL)
        }.start()
//            val httpClient = HTTPClient()
    }

    private fun onServerURLget(serverURL: String) {
        Thread {
            Log.v("TIMING", "Got URL")
            runOnUiThread {
                mSelectDeviceButton!!.background =
                    resources.getDrawable(R.drawable.select_device_connected)
                mSelectDeviceButton!!.text = serverURL
            }
        }.start()
    }

    private fun sendFile(file: File, serverURL: String) {
        val httpClient = HTTPClient(serverURL, this, this)
        Thread {
            Log.v("TIMING", "Sending file to server.")
            httpClient.sendFileToServer(file.absolutePath)
        }.start()
    }

    private fun sendBitmap(bitmap: Bitmap, serverURL: String) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val b64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
        Log.v("TIMING", "Base64 generated")
        Log.v("TIMING", b64Image.length.toString())
        val queue = Volley.newRequestQueue(this)
        val json = JSONObject()
        json.put("b64", b64Image)
        val qTime = System.currentTimeMillis()
        Log.v("TIMING", "Upload queued.")

        val jsonOR = JsonObjectRequest(Request.Method.POST, "$serverURL/match-b64", json,
            { response ->
                val rTime = System.currentTimeMillis()
                Log.v("TIMING", "Got response.")
//                    val timeDiff = System.currentTimeMillis() - response.getJSONObject().get("timestamp")
//                    val httpClient = HTTPClient(serverURL, this, this)
//                    Thread{
//                        httpClient.downloadMatch(response)
//                    }.start()
                Log.v("TEST", response.get("hasResult").toString())
                if (response.get("hasResult").toString() != "false") {
                    try {
                        val b64ImageString = response.get("b64").toString()
                        Log.v("TESTING", b64ImageString)
                        if (b64ImageString.isNotEmpty()) {
                            Log.v("TEST", "got valid screenshot")
                            var croppedScreenshotFilename: String =
                                saveFileToExternalDir(b64ImageString, this)
                            startResultsActivity(croppedScreenshotFilename)
                            val timeTotal = System.currentTimeMillis() - startTime
                            Log.v("TIMING", "Total time taken was: $timeTotal")
                            Toast.makeText(
                                this,
                                "Round-trip time: $timeTotal ms",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: InvocationTargetException) {
                        e.printStackTrace()
                    }
                }
            },
            { error -> Log.v("TIMING", error.toString()) })
        queue.add(jsonOR)

    }

    private fun startResultsActivity(filename: String) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("ScreenshotFilename", filename)
        }
        startActivity(intent)
    }

    companion object {
        const val notificationChannelID = "Screenshotmatcher Channel"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelID,
                "Screenshot Matcher",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

}